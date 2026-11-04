/**
 * What makes two crash reports the same: before retracing, the same trace in the same build; after,
 * the same problem in any build.
 */

const OWN = "oolong.";
// "\tat a.b.c(SourceFile:12)", and an exception header with its message cut off
const FRAME = /^\s*at \S/;
const HEADER = /^\s*(?:Exception in thread "[^"]*" |Caused by: |Suppressed: )?([A-Za-z_$][\w$]*(?:\.[\w$]+)*)(?::|$)/;

/**
 * The key of a report's obfuscated [trace] in its build: its exception classes and first frames,
 * line numbers kept, messages left out. Reports with one key retrace to one group.
 */
export function traceKey(version, kind, trace) {
    const parts = [];
    let frames = 0;
    for (const line of trace.split("\n")) {
        if (FRAME.test(line)) {
            if (frames++ < 10) parts.push(line.trim());
        } else {
            const header = HEADER.exec(line);
            if (header && parts.length < 40) parts.push(header[1]);
        }
    }
    return `${version}\n${kind}\n${parts.join("\n")}`;
}

/**
 * The group of a retraced report: its kind, the exception at the root of it and the first three
 * frames of the app's own code, without line numbers, so that one problem stays one group from build
 * to build. Returns { key, title }. [exceptions] is what retrace gives; [trace] the report's text.
 */
export function groupOf(kind, exceptions, trace) {
    if (kind === "native") {
        // A native crash's first line names the signal and the library; addresses vary
        const first = trace.split("\n").find((line) => line.trim()) ?? "";
        const line = first.replace(/0x[0-9a-f]+|\b[0-9a-f]{8,}\b/gi, "").trim().slice(0, 200);
        return { key: `native\n${line}`, title: line || "原生崩溃" };
    }
    if (exceptions.length === 0) return { key: `${kind}\n${trace.trim().split("\n")[0]}`, title: trace.trim().split("\n")[0] || kind };
    // The innermost cause is where it went wrong
    const root = exceptions[exceptions.length - 1];
    const own = (e) => e.frames.filter((f) => f.c.startsWith(OWN)).slice(0, 3);
    // Its own frames, else those of the exceptions around it, from the inside out
    let frames = own(root);
    for (let i = exceptions.length - 2; frames.length === 0 && i >= 0; i--) frames = own(exceptions[i]);
    if (frames.length === 0) frames = root.frames.slice(0, 3);
    const exception = root.exception ?? (kind === "anr" ? "ANR" : "");
    const key = [kind, exception, ...frames.map((f) => `${f.c}.${f.n}`)].join("\n");
    const top = frames[0];
    const where = top ? `${top.c.slice(top.c.lastIndexOf(".") + 1)}.${top.n}` : "";
    const name = exception.slice(exception.lastIndexOf(".") + 1);
    return { key, title: [name, where].filter(Boolean).join(" · ") };
}

/** A short hex digest of [text], to key rows by. */
export async function digest(text) {
    const bytes = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text)));
    return Array.from(bytes.subarray(0, 12), (b) => b.toString(16).padStart(2, "0")).join("");
}
