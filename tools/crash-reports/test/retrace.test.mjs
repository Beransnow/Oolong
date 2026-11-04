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
import { shardLookup } from "../src/shards.js";

const fixture = (name) => readFileSync(new URL(`fixture/${name}`, import.meta.url), "utf8");
const classes = parseMapping(fixture("mapping.txt"));
const trace = fixture("trace.txt");
const expected = fixture("expected.txt").trimEnd();

test("retraces as R8 does", async () => {
    const { text } = await retrace(trace, (name) => classes.get(name));
    assert.equal(text.trimEnd(), expected);
});

test("retraces the same from the shard file", async () => {
    const dir = mkdtempSync(join(tmpdir(), "shards-"));
    const file = join(dir, "mapping.bin");
    execFileSync(process.execPath, [fileURLToPath(new URL("../scripts/shard.mjs", import.meta.url)), fileURLToPath(new URL("fixture/mapping.txt", import.meta.url)), file, "16"]);
    const bytes = new Uint8Array(readFileSync(file));
    rmSync(dir, { recursive: true });
    const reads = [];
    const read = async (offset, length) => (reads.push(offset), bytes.slice(offset, offset + length));
    const { text } = await retrace(trace, shardLookup(read, 16));
    assert.equal(text.trimEnd(), expected);
    // The header first, then each shard the trace needs once
    assert.equal(reads[0], 0);
    assert.equal(new Set(reads).size, reads.length);
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
