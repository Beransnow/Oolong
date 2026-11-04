import assert from "node:assert/strict";
import { test } from "node:test";
import { groupOf, traceKey } from "../src/group.js";

const frame = (c, n, l = 1) => ({ c, n, f: "X.kt", l });

test("keys a trace by its exceptions and frames, not its messages", () => {
    const a = "java.lang.IllegalStateException: index 3\n\tat a.b(SourceFile:4)\nCaused by: java.lang.Error: x\n\tat c.d(SourceFile:9)";
    const b = "java.lang.IllegalStateException: index 7\n\tat a.b(SourceFile:4)\nCaused by: java.lang.Error: y\n\tat c.d(SourceFile:9)";
    assert.equal(traceKey("1.0", "crash", a), traceKey("1.0", "crash", b));
    assert.notEqual(traceKey("1.0", "crash", a), traceKey("1.0", "crash", a.replace("SourceFile:4", "SourceFile:5")));
    assert.notEqual(traceKey("1.0", "crash", a), traceKey("1.1", "crash", a));
});

test("groups by the root cause and the app's first frames, without lines", () => {
    const exceptions = (line) => [
        { exception: "java.lang.RuntimeException", frames: [frame("android.os.Handler", "dispatch")] },
        {
            exception: "java.lang.IllegalStateException",
            frames: [frame("androidx.compose.Foo", "bar"), frame("oolong.client.A", "a", line), frame("oolong.client.B", "b")],
        },
    ];
    const one = groupOf("crash", exceptions(10), "");
    assert.equal(one.key, groupOf("crash", exceptions(20), "").key);
    assert.equal(one.key, "crash\njava.lang.IllegalStateException\noolong.client.A.a\noolong.client.B.b");
    assert.equal(one.title, "IllegalStateException · A.a");
});

test("takes the app's frames from an outer exception when the cause has none", () => {
    const { key } = groupOf(
        "crash",
        [
            { exception: "java.lang.RuntimeException", frames: [frame("oolong.client.A", "a")] },
            { exception: "java.io.IOException", frames: [frame("okhttp3.Call", "execute")] },
        ],
        "",
    );
    assert.equal(key, "crash\njava.io.IOException\noolong.client.A.a");
});

test("names an ANR by its main thread", () => {
    const { key, title } = groupOf("anr", [{ exception: null, frames: [frame("oolong.client.A", "a")] }], "");
    assert.equal(key, "anr\nANR\noolong.client.A.a");
    assert.equal(title, "ANR · A.a");
});

test("groups a native crash by its first line without addresses", () => {
    const a = groupOf("native", [], "SIGSEGV in libhwui.so at 0x7f3a2c1000\n#00 pc 0x1234");
    const b = groupOf("native", [], "SIGSEGV in libhwui.so at 0x7f00000040\n#00 pc 0x9999");
    assert.equal(a.key, b.key);
});
