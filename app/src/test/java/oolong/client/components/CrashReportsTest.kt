package oolong.client.components

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class CrashReportsTest {
    private fun record(build: String, vararg header: String, body: String) =
        (listOf("Oolong $build", "Android 15 (API 35) · Xiaomi 2210132C", "System: OS2.0.1", "Time: 2026-11-05 10:00:00") + header)
            .joinToString("\n") + "\n\n" + body

    @Test
    fun `sends a release crash with its version, thread and page`() {
        val trace = "java.lang.IllegalStateException: x\n\tat a.b(SourceFile:3)"
        val report = CrashReports.parse(record("1.0.0-alpha.2+abc (1000012) release", "Page: ThreadKey", "Thread: main", body = trace), CrashLogs.Kind.Crash)!!
        assertEquals("1.0.0-alpha.2+abc", report.getString("version"))
        assertEquals("crash", report.getString("kind"))
        assertEquals(trace, report.getString("trace"))
        assertEquals("ThreadKey", report.getString("page"))
        assertEquals("main", report.getString("thread"))
    }

    @Test
    fun `sends nothing from a debug build`() {
        assertNull(CrashReports.parse(record("1.0.0 (1) debug", "Thread: main", body = "java.lang.Error\n\tat a.b(X.kt:1)"), CrashLogs.Kind.Crash))
    }

    @Test
    fun `sends only the main thread of a freeze`() {
        val dump = "----- pid 1 -----\n\n\"main\" prio=5 tid=1 Native\n  at a.b(SourceFile:1)\n\n\"other\" prio=5 tid=2\n  at c.d(SourceFile:2)"
        val report = CrashReports.parse(record("1.0.0+abc (1000012) release", "Input dispatching timed out", body = dump), CrashLogs.Kind.Freeze)!!
        assertEquals("anr", report.getString("kind"))
        assertEquals("\"main\" prio=5 tid=1 Native\n  at a.b(SourceFile:1)", report.getString("trace"))
    }

    @Test
    fun `sends a native crash by the line the system gives`() {
        val report = CrashReports.parse(record("1.0.0+abc (1000012) release", "Page: -", "signal 11 (SIGSEGV) in libhwui.so", body = ""), CrashLogs.Kind.Native)!!
        assertEquals("native", report.getString("kind"))
        assertEquals("signal 11 (SIGSEGV) in libhwui.so", report.getString("trace"))
        assertFalse(report.has("page"))
    }
}
