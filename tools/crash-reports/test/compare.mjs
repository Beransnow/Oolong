// Retraces made-up stack traces with retrace.js and with R8's own retrace, and reports where they
// differ. The traces are built from the mapping itself so that every construct in it is reached:
// inlined frames, rewriteFrame rules under the exception they name, outlines and their call sites,
// frames without a line, lines outside every range, and lines that aren't frames.
//
//   node test/compare.mjs <mapping.txt> [traces] [seed]
//
// R8_JAR (default: the Android SDK's command-line tools) and JAVA (default: java) choose the retrace
// to compare with. SHARDS=<dir> looks classes up in the shards scripts/shard.mjs wrote there, as
// the Worker does, instead of in the parsed mapping.

import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { readFile } from "node:fs/promises";
import { homedir, tmpdir } from "node:os";
import { join } from "node:path";
import { parseMapping } from "../src/mapping.js";
import { retrace } from "../src/retrace.js";
import { shardLookup } from "../src/shards.js";

const [mappingPath, countArg = "500", seedArg = "1"] = process.argv.slice(2);
if (!mappingPath) {
    console.error("usage: node test/compare.mjs <mapping.txt> [traces] [seed]");
    process.exit(2);
}
const text = readFileSync(mappingPath, "utf8");
const classes = parseMapping(text);

let seed = Number(seedArg);
const random = () => ((seed = (Math.imul(seed, 1103515245) + 12345) >>> 0) / 2 ** 32);
const pick = (list) => list[Math.floor(random() * list.length)];

// Every (class, method, group) position a real frame can stand at, and the ones carrying each kind
// of metadata. Left out: outlines and their call sites, which only appear as a pair (outlinePair),
// and the lines a call site's positions point to, which stand for outlined code, not instructions.
const positions = [];
const rewrites = [];
const sites = [];
for (const [cls, record] of classes) {
    for (const [method, groups] of Object.entries(record.m)) {
        if (groups.some((g) => g[2].some((f) => f.o))) continue;
        const targets = new Set(groups.flatMap((g) => g[2].flatMap((f) => Object.values(f.p ?? {}))));
        for (const group of groups) {
            const position = { cls, method, group };
            if (group[2].some((f) => f.p)) {
                if (group[0] !== null) sites.push(position);
                continue;
            }
            if (group[0] !== null && group[0] === group[1] && targets.has(group[0])) continue;
            positions.push(position);
            if (group[2].some((f) => f.w)) rewrites.push(position);
        }
    }
}
// The outline each call site calls, from the metadata's own descriptor
const outlineOf = new Map();
{
    let cls = null;
    let previous = null;
    for (const line of text.split("\n")) {
        const header = /^\S+ -> (\S+):$/.exec(line);
        if (header) cls = header[1];
        const range = /^ {4}(\d+):(\d+):.* -> (\S+)$/.exec(line);
        if (range) previous = `${cls} ${range[3]} ${range[1]}`;
        const site = /"id":"com\.android\.tools\.r8\.outlineCallsite".*"outline":"L([^;]+);([^(]+)\(/.exec(line);
        if (site && previous) outlineOf.set(previous, { cls: site[1].replaceAll("/", "."), method: site[2] });
    }
}

const EXCEPTIONS = ["java.lang.NullPointerException", "java.lang.IllegalStateException", "java.lang.ClassCastException"];

function lineIn(group) {
    return group[0] + Math.floor(random() * (group[1] - group[0] + 1));
}

function frameLine({ cls, method, group }) {
    const roll = random();
    if (roll < 0.08 || group[0] === null) return `\tat ${cls}.${method}(Unknown Source)`;
    if (roll < 0.12) return `\tat ${cls}.${method}(SourceFile:${65000 + Math.floor(random() * 500)})`;
    return `\tat ${cls}.${method}(SourceFile:${lineIn(group)})`;
}

function outlinePair(site) {
    const outline = outlineOf.get(`${site.cls} ${site.method} ${site.group[0]}`);
    const positionsMap = site.group[2].find((f) => f.p).p;
    const record = outline && classes.get(outline.cls);
    const key = pick(Object.keys(positionsMap));
    const target = (record && Object.hasOwn(record.m, outline.method) ? record.m[outline.method] : undefined)?.find((g) => g[0] !== null && g[2].at(-1).s === Number(key));
    if (!target) return [frameLine(site)];
    paired = true;
    return [`\tat ${outline.cls}.${outline.method}(SourceFile:${target[0]})`, `\tat ${site.cls}.${site.method}(SourceFile:${lineIn(site.group)})`];
}

// Whether the trace being built has an outline and its call site
let paired = false;

const traces = [];
const withOutline = new Set();
for (let t = 0; t < Number(countArg); t++) {
    paired = false;
    const lines = [];
    const top = random();
    let exception = pick(EXCEPTIONS);
    if (top < 0.1) exception = pick([...classes.keys()]);
    lines.push(`${exception}: message ${t}`);
    // The top frame, where rewriteFrame rules and outlines apply
    if (top < 0.35 && rewrites.length) lines.push(frameLine(pick(rewrites)));
    else if (top < 0.5 && sites.length) lines.push(...outlinePair(pick(sites)));
    const depth = 1 + Math.floor(random() * 6);
    for (let i = 0; i < depth; i++) lines.push(random() < 0.15 && sites.length ? outlinePair(pick(sites)).join("\n") : frameLine(pick(positions)));
    lines.push("\tat android.os.Handler.dispatchMessage(Handler.java:99)");
    if (random() < 0.3) {
        lines.push(`Caused by: java.lang.NullPointerException`);
        lines.push(frameLine(pick(rewrites.length ? rewrites : positions)));
        lines.push("\t... 4 more");
    }
    if (random() < 0.1) lines.push('"main" prio=5 tid=1 Native', "  #00 pc 000000000004e6d8  /apex/com.android.runtime/lib64/bionic/libc.so (syscall+24)");
    if (paired) withOutline.add(t);
    traces.push(lines.join("\n"));
}
const input = traces.join("\n\n");

const dir = mkdtempSync(join(tmpdir(), "retrace-"));
writeFileSync(join(dir, "trace.txt"), input);
const jar = process.env.R8_JAR ?? join(homedir(), "android-sdk/cmdline-tools/latest/lib/r8.jar");
const expected = execFileSync(process.env.JAVA ?? "java", ["-cp", jar, "com.android.tools.r8.retrace.Retrace", mappingPath, join(dir, "trace.txt")], {
    maxBuffer: 1 << 30,
}).toString().replace(/\n$/, "");
rmSync(dir, { recursive: true });

let lookup = (name) => classes.get(name);
if (process.env.SHARDS) {
    const dir = process.env.SHARDS;
    const meta = JSON.parse(readFileSync(join(dir, "meta.json"), "utf8"));
    const read = (path) => readFile(join(dir, path)).catch(() => null);
    // A fresh lookup per trace, as the Worker reads files for one report at a time
    lookup = null;
    var perTrace = () => shardLookup(read, meta);
}
const actual = lookup
    ? (await retrace(input, lookup)).text
    : (await Promise.all(traces.map((t) => retrace(t, perTrace())))).map((r) => r.text).join("\n\n");

const want = expected.split("\n\n");
const got = actual.split("\n\n");
// R8 leaves out some frames it can place: the call site of an outline whose code maps to the
// method's own lines, and some frames without a line. Ours keeps them; a trace that differs only
// by such extra frames is counted apart from one that differs in what it says.
function onlyExtra(want, got) {
    const a = want.split("\n");
    const b = got.split("\n");
    let j = 0;
    for (const line of b) if (j < a.length && line === a[j]) j++;
    return j === a.length && b.length > a.length && b.filter((l) => !a.includes(l)).every((l) => /^\s*at /.test(l));
}

let failed = 0;
let extra = 0;
let failedWithOutline = 0;
for (let i = 0; i < Math.max(want.length, got.length); i++) {
    if (want[i] === got[i]) continue;
    if (onlyExtra(want[i] ?? "", got[i] ?? "")) {
        extra++;
        continue;
    }
    failed++;
    if (withOutline.has(i)) failedWithOutline++;
    if (failed <= Number(process.env.SHOW ?? 5)) {
        console.log(`--- trace ${i}\n${traces[i]}\n--- R8\n${want[i]}\n--- ours\n${got[i]}\n`);
    }
}
console.log(`${traces.length - failed - extra}/${traces.length} traces match; ${extra} only add frames R8 leaves out; ${failed} differ (${failedWithOutline} with an outline)`);
process.exit(failed ? 1 : 0);
