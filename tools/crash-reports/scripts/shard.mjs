// Splits an R8 mapping into the gzipped shards the Worker reads, and a meta.json beside them:
//
//   node scripts/shard.mjs <mapping.txt> <version> <out dir> [shards]
//
// <version> is the build's exact versionName, as the app reports it (with +<commit> on CI builds);
// the files go under <out dir>/<version>/, the layout of the bucket's mapping/ prefix.

import { createHash } from "node:crypto";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { gzipSync } from "node:zlib";
import { pack, parseMapping } from "../src/mapping.js";

const [mappingPath, version, outDir, countArg = "4096"] = process.argv.slice(2);
if (!mappingPath || !version || !outDir) {
    console.error("usage: node scripts/shard.mjs <mapping.txt> <version> <out dir> [shards]");
    process.exit(2);
}
const text = readFileSync(mappingPath, "utf8");
const classes = parseMapping(text);
const count = Number(countArg);
const dir = join(outDir, version);
mkdirSync(dir, { recursive: true });

let total = 0;
let largest = 0;
for (const [i, text] of pack(classes, count).entries()) {
    const bytes = gzipSync(text, { level: 9 });
    writeFileSync(join(dir, `${i}.gz`), bytes);
    total += bytes.length;
    largest = Math.max(largest, bytes.length);
}
const meta = {
    version,
    shards: count,
    classes: classes.size,
    mapping: createHash("sha256").update(text).digest("hex"),
};
writeFileSync(join(dir, "meta.json"), JSON.stringify(meta));
console.log(`${classes.size} classes in ${count} shards: ${(total / 1e6).toFixed(1)} MB, largest ${(largest / 1e3).toFixed(0)} KB`);
