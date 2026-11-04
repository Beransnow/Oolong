// fixture/mapping.txt is the part of a release build's mapping that fixture/trace.txt touches;
// fixture/expected.txt is what R8's own retrace (9.2.14, the version that wrote the mapping) makes
// of the trace with it. Run with `node --test test/`.

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { gzipSync } from "node:zlib";
import { pack, parseMapping } from "../src/mapping.js";
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

test("retraces the same from shards", async () => {
    const meta = { shards: 16 };
    const files = pack(classes, meta.shards).map((text) => gzipSync(text));
    const read = async (path) => files[Number(path.replace(/\.gz$/, ""))] ?? null;
    const { text } = await retrace(trace, shardLookup(read, meta));
    assert.equal(text.trimEnd(), expected);
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
