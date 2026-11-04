/**
 * Turns an obfuscated stack trace back into the original classes, methods and lines, with the
 * records mapping.js makes. Lines that aren't frames or exception headers pass through unchanged.
 */

// "\tat a.b.c(SourceFile:12)", with any indent
const FRAME = /^(\s*)at ([^\s(]+)\.([^\s.(]+)\(([^)]*)\)(.*)$/;
// "java.lang.X: message", "Caused by: a.b: message", "Exception in thread "main" a.b"
const HEADER = /^(\s*)(Exception in thread "[^"]*" |Caused by: |Suppressed: )?([A-Za-z_$][\w$]*(?:\.[\w$]+)*)(?:(: )(.*))?$/;
const CLASS_NAME = /^[A-Za-z_$][\w$]*(?:\.[\w$]+)*$/;

/**
 * Retraces [text]. [lookup](class, method) gives the record of an obfuscated class name, or null
 * when the class isn't in the mapping; with a method, the record must hold that method's groups if
 * it has any. It may be async. Returns the retraced text and, per exception in the trace, its class
 * and frames, for grouping; frames before any exception, as in a thread dump, come under a null one.
 */
export async function retrace(text, lookup) {
    const cache = new Map();
    const get = async (name, method) => {
        const key = method === undefined ? name : `${name} ${method}`;
        if (!cache.has(key)) cache.set(key, (await lookup(name, method)) ?? null);
        return cache.get(key);
    };
    const out = [];
    const exceptions = [];
    let current = null;
    // The exception the next frame throws, while that frame is the top one
    let thrown = null;
    // An outline frame's position, to place the frame that called it
    let outline = null;

    for (const line of text.split("\n")) {
        const frame = FRAME.exec(line);
        if (frame) {
            const [, indent, cls, method, inside, rest] = frame;
            const { file, line: number } = parseInside(inside);
            const resolved = await resolveFrame(get, cls, method, number, thrown, outline);
            thrown = null;
            outline = resolved?.outline ?? null;
            if (resolved?.outline !== undefined) continue;
            const frames = resolved?.frames ?? [{ c: cls, n: method, f: file, l: number, raw: inside }];
            if (!current) {
                // Frames before any exception, as in a thread dump
                current = { exception: null, cause: false, frames: [] };
                exceptions.push(current);
            }
            frames.forEach((f, i) => {
                const prefix = i === 0 || !resolved?.ambiguous ? `${indent}at ` : `${indent}<OR> at `;
                out.push(`${prefix}${f.c}.${f.n}(${f.raw ?? formatInside(f)})${rest}`);
                current.frames.push(f);
            });
            continue;
        }
        outline = null;
        const header = HEADER.exec(line);
        // A name without a package is taken for an exception only when the mapping has it
        const record = header && (await get(header[3]));
        if (header && (record || header[3].includes("."))) {
            const [, indent, lead = "", cls, colon = "", message] = header;
            const name = record ? record.n : cls;
            let text = message;
            if (message !== undefined && CLASS_NAME.test(message.trim())) {
                const named = await get(message.trim());
                if (named) text = message.replace(message.trim(), named.n);
            }
            out.push(`${indent}${lead}${name}${colon}${text ?? ""}`);
            current = { exception: name, cause: lead.startsWith("Caused"), frames: [] };
            exceptions.push(current);
            thrown = name;
            continue;
        }
        thrown = null;
        out.push(line);
    }
    return { text: out.join("\n"), exceptions };
}

function parseInside(inside) {
    const colon = inside.lastIndexOf(":");
    if (colon >= 0 && /^\d+$/.test(inside.slice(colon + 1))) {
        return { file: inside.slice(0, colon), line: Number(inside.slice(colon + 1)) };
    }
    return { file: inside, line: null };
}

function formatInside(f) {
    return f.l === null ? f.f : `${f.f}:${f.l}`;
}

/**
 * The original frames for one obfuscated frame, innermost first; null when its class isn't in the
 * mapping. When the frame is an outline, returns only its position for the frame below.
 */
async function resolveFrame(get, cls, method, line, thrown, outlinePosition) {
    const record = await get(cls, method);
    if (!record) return null;
    const groups = Object.hasOwn(record.m, method) ? record.m[method] : undefined;
    if (!groups) {
        return { frames: [{ c: record.n, n: method, f: fileOf(record.n, record.f), l: line }] };
    }
    // Overloads may share an obfuscated name; a call site belongs to one of them
    let within = groups;
    if (outlinePosition !== null && line !== null) {
        // This frame called an outline: its line is the call, and the line the outline stopped at
        // says which of the call site's lines the outlined code stood for
        const site = groups.find((g) => contains(g, line) && g[2].some((f) => f.p));
        const positions = site?.[2].find((f) => f.p)?.p;
        if (positions) {
            const mapped = positions[outlinePosition];
            if (mapped === undefined) {
                return { frames: [{ c: record.n, n: method, f: fileOf(record.n, record.f), l: line }] };
            }
            line = mapped;
            const own = groups.filter((g) => methodOf(g) === methodOf(site));
            if (own.some((g) => contains(g, line))) within = own;
        }
    }
    let candidates = line === null ? [] : within.filter((g) => contains(g, line));
    if (candidates.length === 0 && line !== null) candidates = groups.filter((g) => g[0] === null);
    if (candidates.length === 0 && line !== null) {
        // A line outside every range: the method can't be told, so its name stays as it is
        return { frames: [{ c: record.n, n: method, f: fileOf(record.n, record.f), l: line }] };
    }
    if (line === null) {
        // Nothing to place the line by: each distinct method this name stands for, without a line
        const seen = new Set();
        const frames = [];
        for (const [, , chain] of groups) {
            const outer = chain[chain.length - 1];
            if (!isShown(record, outer)) continue;
            const frame = toFrame(record, outer, null);
            const key = `${frame.c}.${frame.n}(${frame.f})`;
            if (seen.has(key)) continue;
            seen.add(key);
            frames.push(frame);
        }
        // Alternatives in R8's order
        frames.sort((a, b) => (`${a.c}.${a.n}` < `${b.c}.${b.n}` ? -1 : `${a.c}.${a.n}` > `${b.c}.${b.n}` ? 1 : 0));
        return { frames, ambiguous: frames.length > 1 };
    }
    // An outline is marked once, on the method's first range
    if (groups.some((g) => g[2].some((f) => f.o))) return { outline: line };
    const results = [];
    for (const [a, , chain] of candidates) {
        let frames = chain
            .filter((f) => isShown(record, f))
            .map((f) => toFrame(record, f, originalLine(f, line, a)));
        if (thrown) frames = rewrite(chain, frames, thrown);
        results.push(frames);
    }
    const unique = dedupe(results);
    return { frames: unique.flat(), ambiguous: unique.length > 1 };
}

/**
 * Whether [f] is shown: the obfuscated method's own frame is no call of its own, and code the
 * compiler made up has no source to show.
 */
function isShown(record, f) {
    return !f.r && !f.y && !(record.y && !f.h);
}

/** The method a group's code physically sits in: its outermost frame. */
function methodOf(group) {
    const outer = group[2][group[2].length - 1];
    return `${outer.h ?? ""}.${outer.n}(${outer.a})`;
}

function contains(group, line) {
    return group[0] !== null && group[0] <= line && line <= group[1];
}

/** The original line of [frame] at obfuscated [line] in a range starting at [a]. */
function originalLine(frame, line, a) {
    if (frame.s === undefined) return line;
    if (frame.s === 0) return null;
    if (frame.e === undefined || frame.e === frame.s || a === null) return frame.s;
    return frame.s + (line - a);
}

function toFrame(record, f, line) {
    if (!f.h) return { c: record.n, n: f.n, f: fileOf(record.n, record.f), l: line };
    // An inlined holder without a file of its own shares the record's when both are in one top-level class
    const own = record.f && record.f !== SYNTHETIC && outerOf(f.h) === outerOf(record.n) ? record.f : undefined;
    return { c: f.h, n: f.n, f: f.f ?? own ?? fileOf(f.h), l: line };
}

const SYNTHETIC = "R8$$SyntheticClass";

function outerOf(name) {
    const dollar = name.indexOf("$", name.lastIndexOf(".") + 1);
    return dollar < 0 ? name : name.slice(0, dollar);
}

/**
 * The source file R8 names for [holder]: its own when the mapping has it, else one made from the
 * class name, in Kotlin when the class is a Kotlin file's (…Kt).
 */
function fileOf(holder, file) {
    if (file) return file;
    const simple = holder.slice(holder.lastIndexOf(".") + 1);
    const outer = simple.includes("$") ? simple.slice(0, simple.indexOf("$")) : simple;
    return outer.endsWith("Kt") ? `${outer.slice(0, -2)}.kt` : `${outer}.java`;
}

/** Applies the rewriteFrame rules of [chain] whose conditions [thrown] meets. */
function rewrite(chain, frames, thrown) {
    const descriptor = `throws(L${thrown.replaceAll(".", "/")};)`;
    let remove = 0;
    for (const f of chain) {
        for (const rule of f.w ?? []) {
            if (!rule.c.every((c) => c === descriptor)) continue;
            for (const action of rule.a) {
                const match = /^removeInnerFrames\((\d+)\)$/.exec(action);
                if (match) remove += Number(match[1]);
            }
        }
    }
    // Asked to remove every frame or more, R8 removes none
    return remove > 0 && remove < frames.length ? frames.slice(remove) : frames;
}

function dedupe(results) {
    const seen = new Set();
    return results.filter((frames) => {
        const key = frames.map((f) => `${f.c}.${f.n}(${formatInside(f)})`).join("|");
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
    });
}
