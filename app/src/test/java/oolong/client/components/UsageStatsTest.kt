package oolong.client.components

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class UsageStatsTest {
    private val device = UsageStats.Device(
        version = "1.0.0-alpha.3+701f5613",
        channel = "beta",
        sdk = 35,
        brand = "Xiaomi",
        rom = "HyperOS 2",
        form = "phone",
        model = "2210132C",
        romVersion = "OS2.0.6.0.VMCCNXM",
        locale = "zh-CN",
    )
    private val today = LocalDate.of(2026, 11, 5) // a Thursday
    private fun at(day: LocalDate) = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun `marks the first heartbeat of the week and of the month`() {
        val first = UsageStats.heartbeat(device, today, lastWeek = null, lastMonth = null, crashed = false, installed = at(today))
        assertTrue(first.getBoolean("week"))
        assertTrue(first.getBoolean("month"))
        val again = UsageStats.heartbeat(device, today, lastWeek = "2026-11-02", lastMonth = "2026-11", crashed = true, installed = at(today))
        assertFalse(again.getBoolean("week"))
        assertFalse(again.getBoolean("month"))
        assertTrue(again.getBoolean("crashed"))
    }

    @Test
    fun `sends model, ROM build and locale only with the month's first heartbeat`() {
        val monthly = UsageStats.heartbeat(device, today, lastWeek = "2026-10-26", lastMonth = "2026-10", crashed = false, installed = at(today))
        assertEquals("2210132C", monthly.getString("model"))
        assertEquals("zh-CN", monthly.getString("locale"))
        val daily = UsageStats.heartbeat(device, today, lastWeek = "2026-10-26", lastMonth = "2026-11", crashed = false, installed = at(today))
        assertFalse(daily.has("model"))
        assertFalse(daily.has("romVersion"))
        assertFalse(daily.has("locale"))
    }

    @Test
    fun `sends the install week only in the first 8 weeks`() {
        val week = { installed: LocalDate -> UsageStats.heartbeat(device, today, null, null, false, at(installed)) }
        assertEquals(0, week(today).getInt("installWeek"))
        assertEquals(1, week(LocalDate.of(2026, 11, 1)).getInt("installWeek")) // the Sunday before
        assertEquals(7, week(today.minusWeeks(7)).getInt("installWeek"))
        assertFalse(week(today.minusWeeks(8)).has("installWeek"))
    }

    @Test
    fun `takes days in UTC`() {
        val sundayNightUtc = LocalDate.of(2026, 11, 1).atTime(23, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(LocalDate.of(2026, 11, 1), UsageStats.utcDay(sundayNightUtc))
    }

    @Test
    fun `names no ROM it does not know`() {
        assertEquals("", Roms.family())
    }
}
