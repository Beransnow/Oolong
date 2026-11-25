package oolong.client.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RepliedTimeTest {
    private val zone = TimeZone.getTimeZone("Asia/Shanghai")
    private lateinit var defaultZone: TimeZone
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setZone() {
        defaultZone = TimeZone.getDefault()
        TimeZone.setDefault(zone)
    }

    @After
    fun restoreZone() = TimeZone.setDefault(defaultZone)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis

    private val now = at(2026, 11, 3, 15, 0)

    private fun replied(time: Long) = DateTimeUtils.getRepliedTimeString(context, time, now)

    @Test
    @Config(qualifiers = "zh-rCN")
    fun wordsTakeTheVerbStraightAfter() {
        assertEquals("刚刚回复", replied(now - 10_000))
        assertEquals("8 分钟前回复", replied(now - 8 * 60_000))
        // An earlier year's date ends in a word too
        assertTrue(replied(at(2025, 10, 1, 9, 0)).endsWith("日回复"))
    }

    @Test
    @Config(qualifiers = "zh-rCN")
    fun aNumberKeepsASpaceBeforeTheVerb() {
        assertTrue(replied(at(2026, 11, 3, 9, 30)).let { it.startsWith("今天 ") && it.endsWith(" 回复") })
        assertTrue(replied(at(2026, 11, 1, 9, 30)).endsWith(" 回复"))
    }

    @Test
    @Config(qualifiers = "en")
    fun englishRunsWordsOnInLowerCase() {
        assertEquals("Replied just now", replied(now - 10_000))
        assertEquals("Replied 8 min ago", replied(now - 8 * 60_000))
        assertTrue(replied(at(2026, 11, 3, 9, 30)).startsWith("Replied today "))
    }
}
