/**
 * Reads records from the file scripts/shard.mjs writes. [read](offset, length) gives those bytes of
 * the file: a ranged R2 get in the Worker, a slice in tests.
 *
 * The file: MAGIC, the shard count n (u32 LE), n + 1 offsets (u32 LE) where each shard starts and
 * the last ends, then the shards, each gzipped lines `key<TAB>json` (see pack in mapping.js).
 *
 * A trace is retraced in two steps, so that the shards it needs can be read over several Worker
 * invocations: wantedKeys says which lines it needs from the trace alone, readLines reads them from
 * some of the shards, and once all are at hand linesLookup retraces from them without the file.
 */

import { shardOf } from "./mapping.js";
import { retrace } from "./retrace.js";

export const MAGIC = "OLM1";

/** The size of the header of a file with [shards] shards: what to read before anything else. */
export function headerSize(shards) {
    return 8 + 4 * (shards + 1);
}

/** Where each shard starts, from the header [bytes]; [shards] must be the count written. */
export function parseHeader(bytes, shards) {
    const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
    const magic = String.fromCharCode(...bytes.subarray(0, 4));
    if (magic !== MAGIC || view.getUint32(4, true) !== shards) throw new Error("not a mapping file with that many shards");
    return Array.from({ length: shards + 1 }, (_, i) => view.getUint32(8 + 4 * i, true));
}

/** The keys of every line retracing [text] can ask for, found without the mapping. */
export async function wantedKeys(text) {
    const keys = new Set();
    // A record that has the class but none of its methods: each frame then asks for nothing more
    await retrace(text, (name, method) => {
        keys.add(name);
        if (method !== undefined) keys.add(`${name} ${method}`);
        return { n: name, m: Object.create(null) };
    });
    return [...keys];
}

/** The shard each of [keys] is in. */
export function shardsOf(keys, shards) {
    return [...new Set(keys.map((key) => shardOf(key, shards)))];
}

/**
 * Reads shards [indices] from a file whose header gave [offsets], and returns each of [keys] in
 * them with its line's JSON, or null when the mapping has no such line.
 */
export async function readLines(read, offsets, shards, indices, keys) {
    const found = new Map();
    await Promise.all(
        indices.map(async (index) => {
            const text = await gunzip(await read(offsets[index], offsets[index + 1] - offsets[index]));
            for (const key of keys) if (shardOf(key, shards) === index) found.set(key, findLine(text, key));
        }),
    );
    return found;
}

/** A lookup for retrace from [lines], key to JSON or null, as readLines gives them. */
export function linesLookup(lines) {
    const parsed = new Map();
    return recordLookup((key) => {
        if (!parsed.has(key)) {
            const json = lines.get(key);
            parsed.set(key, json == null ? null : JSON.parse(json));
        }
        return parsed.get(key);
    });
}

/** A lookup reading the file as it goes: the header once, then each shard it needs once. */
export function shardLookup(read, shards) {
    let offsets = null;
    const texts = new Map();
    const shard = (index) => {
        if (!texts.has(index)) {
            offsets ??= read(0, headerSize(shards)).then((bytes) => parseHeader(bytes, shards));
            texts.set(
                index,
                offsets.then((at) => read(at[index], at[index + 1] - at[index])).then(gunzip),
            );
        }
        return texts.get(index);
    };
    return recordLookup(async (key) => {
        const json = findLine(await shard(shardOf(key, shards)), key);
        return json === null ? null : JSON.parse(json);
    });
}

/** Builds records as retrace wants them from [line](key), which gives a line's value or null. */
function recordLookup(line) {
    const bare = (head) => head && { ...head, m: Object.create(null) };
    return async (name, method) => {
        if (method === undefined) return bare(await line(name));
        const found = await line(`${name} ${method}`);
        if (!found) {
            // A class without this method still names the class
            return bare(await line(name));
        }
        const { g, ...head } = found;
        const m = Object.create(null);
        m[method] = g;
        return { ...head, m };
    };
}

/** The JSON of [key]'s line in a shard's [text], or null. Keys hold no tab or line break. */
function findLine(text, key) {
    const start = text.startsWith(`${key}\t`) ? 0 : text.indexOf(`\n${key}\t`) + 1;
    if (start === 0 && !text.startsWith(`${key}\t`)) return null;
    const end = text.indexOf("\n", start);
    return text.slice(start + key.length + 1, end < 0 ? undefined : end);
}

async function gunzip(bytes) {
    const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream("gzip"));
    return new Response(stream).text();
}
