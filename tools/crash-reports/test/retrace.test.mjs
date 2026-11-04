// fixture/mapping.txt is the part of a release build's mapping that fixture/trace.txt touches;
// fixture/expected.txt is what R8's own retrace (9.2.14, the version that wrote the mapping) makes
// of the trace with it. Run with `npm test`.

import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { parseMapping } from "../src/mapping.js";
import { retrace } from "../src/retrace.js";
import { headerSize, linesLookup, parseHeader, readLines, shardLookup, shardsOf, wantedKeys } from "../src/shards.js";

const fixture = (name) => readFileSync(new URL(`fixture/${name}`, import.meta.url), "utf8");
const classes = parseMapping(fixture("mapping.txt"));
const trace = fixture("trace.txt");
const expected = fixture("expected.txt").trimEnd();

test("retraces as R8 does", async () => {
    const { text } = await retrace(trace, (name) => classes.get(name));
    assert.equal(text.trimEnd(), expected);
});

test("retraces the same from the shard file", async () => {
    const { read, reads } = shardFile(16);
    const { text } = await retrace(trace, shardLookup(read, 16));
    assert.equal(text.trimEnd(), expected);
    // The header first, then each shard the trace needs once
    assert.equal(reads[0], 0);
    assert.equal(new Set(reads).size, reads.length);
});

/** The fixture laid out as a shard file with [shards] shards, and a reader recording its reads. */
function shardFile(shards) {
    const dir = mkdtempSync(join(tmpdir(), "shards-"));
    const file = join(dir, "mapping.bin");
    execFileSync(process.execPath, [fileURLToPath(new URL("../scripts/shard.mjs", import.meta.url)), fileURLToPath(new URL("fixture/mapping.txt", import.meta.url)), file, String(shards)]);
    const bytes = new Uint8Array(readFileSync(file));
    rmSync(dir, { recursive: true });
    const reads = [];
    const read = async (offset, length) => (reads.push(offset), bytes.slice(offset, offset + length));
    return { read, reads };
}

test("knows from the trace alone every line retracing it reads", async () => {
    const wanted = new Set(await wantedKeys(trace));
    const asked = [];
    const lookup = shardLookup(shardFile(64).read, 64);
    await retrace(trace, async (name, method) => {
        asked.push(name);
        if (method !== undefined) asked.push(`${name} ${method}`);
        return lookup(name, method);
    });
    for (const key of asked) assert.ok(wanted.has(key), key);
});

test("retraces the same from lines read in parts", async () => {
    const shards = 64;
    const { read } = shardFile(shards);
    const offsets = parseHeader(await read(0, headerSize(shards)), shards);
    const keys = await wantedKeys(trace);
    const indices = shardsOf(keys, shards);
    const lines = new Map();
    // A few shards at a time, as over several invocations
    for (let i = 0; i < indices.length; i += 5) {
        for (const [key, json] of await readLines(read, offsets, shards, indices.slice(i, i + 5), keys)) lines.set(key, json);
    }
    assert.equal(lines.size, keys.length);
    const { text } = await retrace(trace, linesLookup(lines));
    assert.equal(text.trimEnd(), expected);
});

test("gives the frames of a thread dump under no exception", async () => {
    const { exceptions } = await retrace('"main" prio=5 tid=1 Native\n  at nq5$a.onTransact(SourceFile:458)', (name) => classes.get(name));
    assert.equal(exceptions.length, 1);
    assert.equal(exceptions[0].exception, null);
});

test("reports each exception with its frames", async () => {
    const { exceptions } = await retrace(trace, (name) => classes.get(name));
    assert.equal(exceptions[0].exception, "java.lang.NullPointerException");
    assert.equal(exceptions[0].frames[0].n, "createSubDecor");
    assert.equal(exceptions[0].frames[0].l, 934);
    assert.equal(exceptions[1].cause, true);
    assert.equal(exceptions[3].exception, "okhttp3.internal.proxy.NullProxySelector");
});

test("treats an object's own property names as method names", async () => {
    const { text } = await retrace("java.lang.Error\n\tat qy.constructor(SourceFile:1)", (name) => classes.get(name));
    assert.match(text, /\.constructor\(/);
});
