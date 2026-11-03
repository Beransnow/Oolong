package oolong.client.components

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CrashLogsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clear() = CrashLogs.clear(context)

    @Test
    fun aCrashIsKeptWithItsExceptionFirst() {
        CrashLogs.page = "ThreadKey"
        CrashLogs.record(context, Thread.currentThread(), IllegalStateException("boom"), time = 1000L)
        val entry = CrashLogs.list(context).single()
        assertEquals(CrashLogs.Kind.Crash, entry.kind)
        assertEquals("java.lang.IllegalStateException: boom", entry.title)
        val text = CrashLogs.read(context, entry.id)!!
        assertTrue(text.contains("Page: ThreadKey"))
    }

    @Test
    fun onlyTheNewestTenAreKept() {
        repeat(12) { CrashLogs.record(context, Thread.currentThread(), RuntimeException("$it"), time = 1000L + it) }
        val entries = CrashLogs.list(context)
        assertEquals(10, entries.size)
        assertEquals(1011L, entries.first().time)
        assertEquals(1002L, entries.last().time)
    }

    @Test
    fun signInTokensAreLeftOut() {
        CrashLogs.record(context, Thread.currentThread(), RuntimeException("cookie BDUSS=abc123; STOKEN=xyz"), time = 1000L)
        val text = CrashLogs.read(context, CrashLogs.list(context).single().id)!!
        assertFalse(text.contains("abc123"))
        assertFalse(text.contains("xyz"))
        assertTrue(text.contains("BDUSS=***"))
    }

    @Test
    fun aNewCrashIsPointedOutOnce() {
        CrashLogs.record(context, Thread.currentThread(), RuntimeException(), time = System.currentTimeMillis())
        assertNotNull(CrashLogs.takeUnnoticed(context))
        assertNull(CrashLogs.takeUnnoticed(context))
    }
}
