// Lays an R8 mapping out as the one file the Worker reads, a header and gzipped shards (see the
// README):
//
//   node scripts/shard.mjs <mapping.txt> <out file> [shards]
//
// The file goes to R2 as mapping/<versionName>.bin, versionName exactly as the app reports it.

import { createHash } from "node:crypto";
import { readFileSync, writeFileSync } from "node:fs";
import { gzipSync } from "node:zlib";
import { pack, parseMapping } from "../src/mapping.js";
import { MAGIC } from "../src/shards.js";

const [mappingPath, outPath, countArg = "4096"] = process.argv.slice(2);
if (!mappingPath || !outPath) {
    console.error("usage: node scripts/shard.mjs <mapping.txt> <out file> [shards]");
    process.exit(2);
}
const text = readFileSync(mappingPath, "utf8");
const classes = parseMapping(text);
const count = Number(countArg);
const blocks = pack(classes, count).map((shard) => gzipSync(shard, { level: 9 }));

// Magic, shard count, then where each shard starts and where the last one ends
const header = Buffer.alloc(8 + 4 * (count + 1));
header.write(MAGIC, 0, "ascii");
header.writeUInt32LE(count, 4);
let offset = header.length;
blocks.forEach((block, i) => {
    header.writeUInt32LE(offset, 8 + 4 * i);
    offset += block.length;
});
header.writeUInt32LE(offset, 8 + 4 * count);
writeFileSync(outPath, Buffer.concat([header, ...blocks]));

const sha = createHash("sha256").update(text).digest("hex");
const largest = Math.max(...blocks.map((b) => b.length));
console.log(`${classes.size} classes in ${count} shards: ${(offset / 1e6).toFixed(1)} MB, largest shard ${(largest / 1e3).toFixed(0)} KB, mapping ${sha}`);
