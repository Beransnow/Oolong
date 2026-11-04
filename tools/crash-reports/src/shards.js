/**
 * Looks records up in the shards scripts/shard.mjs writes (see pack in mapping.js), reading each
 * file at most once. [read](path) gives a file's bytes, or null when there is none: an R2 get in the
 * Worker, a file read in tests. [meta] is the version's meta.json.
 */

import { shardOf } from "./mapping.js";

export function shardLookup(read, meta) {
    const files = new Map();
    const load = (index) => {
        if (!files.has(index)) files.set(index, read(`${index}.gz`).then((bytes) => (bytes ? gunzip(bytes) : "")));
        return files.get(index);
    };
    const line = async (key) => {
        const text = await load(shardOf(key, meta.shards));
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
