/**
 * Looks records up in the file scripts/shard.mjs writes, reading only the header and the shards a
 * lookup needs, each at most once. [read](offset, length) gives those bytes of the file: a ranged
 * R2 get in the Worker, a slice in tests.
 *
 * The file: MAGIC, the shard count n (u32 LE), n + 1 offsets (u32 LE) where each shard starts and
 * the last ends, then the shards, each gzipped lines `key<TAB>json` (see pack in mapping.js).
 */

import { shardOf } from "./mapping.js";

export const MAGIC = "OLM1";

/** [shards] is the count the file was written with; the header must agree. */
export function shardLookup(read, shards) {
    let offsets = null;
    const header = async () => {
        offsets ??= read(0, 8 + 4 * (shards + 1)).then((bytes) => {
            const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
            const magic = String.fromCharCode(...bytes.subarray(0, 4));
            if (magic !== MAGIC || view.getUint32(4, true) !== shards) throw new Error("not a mapping file with that many shards");
            return Array.from({ length: shards + 1 }, (_, i) => view.getUint32(8 + 4 * i, true));
        });
        return offsets;
    };
    const files = new Map();
    const load = (index) => {
        if (!files.has(index)) {
            files.set(
                index,
                header().then((at) => read(at[index], at[index + 1] - at[index])).then(gunzip),
            );
        }
        return files.get(index);
    };
    const line = async (key) => {
        const text = await load(shardOf(key, shards));
        // Keys hold no tab or line break, so "\n<key>\t" finds exactly this key's line
        const start = text.startsWith(`${key}\t`) ? 0 : text.indexOf(`\n${key}\t`) + 1;
        if (start === 0 && !text.startsWith(`${key}\t`)) return null;
        const end = text.indexOf("\n", start);
        return JSON.parse(text.slice(start + key.length + 1, end));
    };
    return async (name, method) => {
        if (method === undefined) {
            const head = await line(name);
            return head && { ...head, m: Object.create(null) };
        }
        const found = await line(`${name} ${method}`);
        if (found) {
            const { g, ...head } = found;
            const m = Object.create(null);
            m[method] = g;
            return { ...head, m };
        }
        // A class without this method still names the class
        const head = await line(name);
        return head && { ...head, m: Object.create(null) };
    };
}

async function gunzip(bytes) {
    const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream("gzip"));
    return new Response(stream).text();
}
