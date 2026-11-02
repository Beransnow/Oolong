package com.huanchengfly.tieba.post.utils

import com.huanchengfly.tieba.post.utils.DateTimeUtils.relativeTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class RelativeTimeTest {
    private val zone = TimeZone.getTimeZone("Asia/Shanghai")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, hour, minute, second)
        }.timeInMillis

    private fun relative(time: Long, now: Long) = relativeTime(time, now, zone)

    @Test
    fun underAMinuteIsJustNow() {
        val now = at(2026, 11, 3, 11, 0, 5)
        assertEquals(RelativeTime.JustNow, relative(at(2026, 11, 3, 10, 59, 50), now))
    }

    @Test
    fun aheadOfTheClockIsJustNow() {
        val now = at(2026, 11, 3, 11, 0)
        assertEquals(RelativeTime.JustNow, relative(at(2026, 11, 3, 11, 3), now))
    }

    @Test
    fun minutesCountAcrossTheHour() {
        val now = at(2026, 11, 3, 11, 2)
        assertEquals(RelativeTime.MinutesAgo(4), relative(at(2026, 11, 3, 10, 58), now))
    }

    @Test
    fun minutesCountAcrossTheYear() {
        val now = at(2027, 1, 1, 0, 1)
        assertEquals(RelativeTime.MinutesAgo(2), relative(at(2026, 12, 31, 23, 59), now))
    }

    @Test
    fun anHourOrMoreTodayIsToday() {
        val now = at(2026, 11, 3, 15, 0)
        val time = at(2026, 11, 3, 14, 0)
        assertEquals(RelativeTime.Today(time), relative(time, now))
    }

    @Test
    fun lastNightIsYesterday() {
        val now = at(2026, 11, 3, 0, 30)
        val time = at(2026, 11, 2, 23, 0)
        assertEquals(RelativeTime.Yesterday(time), relative(time, now))
    }

    @Test
    fun newYearsEveIsYesterdayOnNewYearsDay() {
        val now = at(2027, 1, 1, 9, 0)
        val time = at(2026, 12, 31, 20, 0)
        assertEquals(RelativeTime.Yesterday(time), relative(time, now))
    }

    @Test
    fun twoDaysAgoIsThisYear() {
        val now = at(2026, 11, 3, 9, 0)
        val time = at(2026, 11, 1, 9, 0)
        assertEquals(RelativeTime.ThisYear(time), relative(time, now))
    }

    @Test
    fun anEarlierYearIsEarlier() {
        val now = at(2027, 1, 3, 9, 0)
        val time = at(2026, 12, 20, 9, 0)
        assertEquals(RelativeTime.Earlier(time), relative(time, now))
    }
}
