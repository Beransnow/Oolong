/**
 * Reads an R8 mapping file into one record per obfuscated class, as retrace.js looks them up, and
 * lays the records out as shards so that a lookup reads one small file instead of the whole map.
 *
 * A record:
 *   { n: original name, f?: source file, m: { obfuscated method name: [group, …] } }
 * A group is the frames one obfuscated line range stands for, innermost first:
 *   [first line | null, last line | null, [frame, …]]
 * A frame:
 *   { n: method, a: argument types, h?: holder class when not the record's own,
 *     s?: first original line, e?: last original line, f?: source file when not the record's,
 *     w?: [{ c: conditions, a: actions }]   rewriteFrame rules
 *     o?: 1                                  the method is an outline (on its first range)
 *     p?: { outline's line: call site line } the call site of an outline
 *     y?: 1                                  synthesized by the compiler
 *     r?: 1 }                                the obfuscated method itself, which the mapping
 *                                            writes with bare class names
 * A record synthesized by the compiler as a whole has y: 1.
 */

const CLASS = /^(\S+) -> (\S+):$/;
// [first:last:]return name(arguments)[:original first[:original last]] -> obfuscated
const METHOD = /^ {4}(?:(\d+):(\d+):)?\S+ ([^\s(]+)\(([^)]*)\)(?::(\d+)(?::(\d+))?)? -> (\S+)$/;
const META = /^\s*# (\{.*\})\s*$/;

/** Parses [text], the whole mapping file, into a Map from obfuscated class name to its record. */
export function parseMapping(text) {
    const classes = new Map();
    // Source files by original class name, to name the file of a frame inlined from another class
    const files = new Map();
    let record = null;
    // The frame the next member metadata belongs to, and the group it was added to
    let frame = null;
    let last = null;
    for (const line of text.split("\n")) {
        if (line.length === 0) continue;
        if (line.startsWith("#")) {
            const meta = parseMeta(line);
            if (!meta || !record) continue;
            if (meta.id === "sourceFile") {
                record.f = meta.fileName;
                files.set(record.n, meta.fileName);
            } else if (meta.id === "com.android.tools.r8.synthesized") {
                record.y = 1;
            }
            continue;
        }
        if (line.startsWith("      #")) {
            const meta = parseMeta(line);
            if (meta && frame) addMeta(frame, meta);
            continue;
        }
        const header = CLASS.exec(line);
        if (header) {
            record = { n: header[1], m: Object.create(null) };
            classes.set(header[2], record);
            frame = null;
            last = null;
            continue;
        }
        const method = METHOD.exec(line);
        if (!method || !record) {
            // A field, or a line this reader doesn't know: nothing a stack frame needs
            frame = null;
            continue;
        }
        const [, first, lastLine, qualified, args, start, end, obfuscated] = method;
        const dot = qualified.lastIndexOf(".");
        frame = { n: dot < 0 ? qualified : qualified.slice(dot + 1), a: args };
        if (dot >= 0) frame.h = qualified.slice(0, dot);
        if (start !== undefined) frame.s = Number(start);
        if (end !== undefined) frame.e = Number(end);
        const a = first === undefined ? null : Number(first);
        const b = lastLine === undefined ? null : Number(lastLine);
        const groups = (record.m[obfuscated] ??= []);
        // Lines with the same range one after another are one position: the inlined frames, innermost first
        if (last && last.name === obfuscated && last.group[0] === a && last.group[1] === b && a !== null) {
            last.group[2].push(frame);
        } else {
            const group = [a, b, [frame]];
            groups.push(group);
            last = { name: obfuscated, group };
        }
    }
    // Holders written relative to the class's package, and the file each inlined frame came from
    for (const record of classes.values()) {
        const pkg = record.n.includes(".") ? record.n.slice(0, record.n.lastIndexOf(".") + 1) : "";
        for (const groups of Object.values(record.m)) {
            for (const [, , frames] of groups) {
                for (const frame of frames) {
                    if (frame.a.split(",").some(isBareType)) frame.r = 1;
                    if (frame.h === undefined) continue;
                    if (!frame.h.includes(".") && !files.has(frame.h) && pkg) {
                        frame.h = pkg + frame.h;
                        frame.r = 1;
                    }
                    if (frame.h === record.n) {
                        delete frame.h;
                        continue;
                    }
                    const file = files.get(frame.h);
                    if (file !== undefined) frame.f = file;
                }
            }
        }
    }
    return classes;
}

const PRIMITIVES = new Set(["boolean", "byte", "char", "short", "int", "long", "float", "double"]);

/** A class type named without its package, as the mapping writes the obfuscated method's own types. */
function isBareType(type) {
    const name = type.replace(/(\[\])+$/, "");
    return name.length > 0 && !name.includes(".") && !PRIMITIVES.has(name);
}

function parseMeta(line) {
    const match = META.exec(line);
    if (!match) return null;
    try {
        return JSON.parse(match[1]);
    } catch {
        return null;
    }
}

function addMeta(frame, meta) {
    switch (meta.id) {
        case "com.android.tools.r8.rewriteFrame":
            (frame.w ??= []).push({ c: meta.conditions, a: meta.actions });
            break;
        case "com.android.tools.r8.outline":
            frame.o = 1;
            break;
        case "com.android.tools.r8.outlineCallsite":
            frame.p = meta.positions;
            break;
        case "com.android.tools.r8.synthesized":
            frame.y = 1;
            break;
    }
}

/** FNV-1a over the UTF-8 bytes of [name]: the same in Node and in a Worker. */
export function hash(name) {
    let h = 0x811c9dc5;
    for (const byte of new TextEncoder().encode(name)) {
        h ^= byte;
        h = Math.imul(h, 0x01000193) >>> 0;
    }
    return h;
}

/** The shard an obfuscated class name falls in. */
export function shardOf(name, count) {
    return hash(name) % count;
}

/**
 * Lays [classes] out as [count] shards of lines, `key<TAB>json`, so that a lookup decompresses one
 * small file, finds its line and parses only that. A class's own line, keyed by its obfuscated name,
 * holds { n, f?, y? }; each of its methods has a line keyed `<class> <method>` holding the record
 * with just that method's groups, { n, f?, y?, g }. A line goes to the shard its key hashes to, so
 * a large class spreads over many shards. Returns the shards' text.
 */
export function pack(classes, count) {
    const shards = Array.from({ length: count }, () => []);
    const add = (key, value) => shards[shardOf(key, count)].push(`${key}\t${JSON.stringify(value)}`);
    for (const [name, record] of classes) {
        const { m, ...head } = record;
        add(name, head);
        for (const [method, groups] of Object.entries(m)) add(`${name} ${method}`, { ...head, g: groups });
    }
    return shards.map((lines) => `${lines.join("\n")}\n`);
}
