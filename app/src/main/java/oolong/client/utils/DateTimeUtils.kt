package oolong.client.utils

import android.content.Context
import oolong.client.R
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import android.text.format.DateFormat as AndroidDateFormat

/** How long ago something was, in the steps a post's time is told in. */
sealed interface RelativeTime {
    /** Under a minute ago, or a little ahead of this device's clock. */
    data object JustNow : RelativeTime

    data class MinutesAgo(val minutes: Int) : RelativeTime

    data class Today(val time: Long) : RelativeTime

    data class Yesterday(val time: Long) : RelativeTime

    data class ThisYear(val time: Long) : RelativeTime

    /** In an earlier year: the date alone, as the time of day no longer matters. */
    data class Earlier(val time: Long) : RelativeTime
}

object DateTimeUtils {
    /**
     * Which step [time] falls in, seen from [now]: minutes by how long has passed, days and years
     * by the calendar in [zone].
     */
    fun relativeTime(time: Long, now: Long, zone: TimeZone = TimeZone.getDefault()): RelativeTime {
        val elapsed = now - time
        if (elapsed < MINUTE) return RelativeTime.JustNow
        if (elapsed < HOUR) return RelativeTime.MinutesAgo((elapsed / MINUTE).toInt())
        val then = Calendar.getInstance(zone).apply { timeInMillis = time }
        val today = Calendar.getInstance(zone).apply { timeInMillis = now }
        if (then.sameDay(today)) return RelativeTime.Today(time)
        val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        if (then.sameDay(yesterday)) return RelativeTime.Yesterday(time)
        if (then.get(Calendar.YEAR) == today.get(Calendar.YEAR)) return RelativeTime.ThisYear(time)
        return RelativeTime.Earlier(time)
    }

    /** [timestamp], in seconds or milliseconds, as how long before [now] it was. */
    @JvmStatic
    @JvmOverloads
    fun getRelativeTimeString(context: Context, timestamp: Long, now: Long = System.currentTimeMillis()): String =
        format(context, relativeTime(fixTimestamp(timestamp), now))

    @JvmStatic
    @JvmOverloads
    fun getRelativeTimeString(context: Context, timestampString: String, now: Long = System.currentTimeMillis()): String =
        format(context, relativeTime(fixTimestamp(timestampString), now))

    fun format(context: Context, relative: RelativeTime): String {
        val res = context.resources
        val locale = res.configuration.locales[0] ?: Locale.getDefault()
        val is24Hour = AndroidDateFormat.is24HourFormat(context)
        fun pattern(skeleton: String, time: Long) =
            SimpleDateFormat(bestPatterns.getOrPut(locale to skeleton) { AndroidDateFormat.getBestDateTimePattern(locale, skeleton) }, locale)
                .format(Date(time))
        fun timeOfDay(time: Long) = pattern(if (is24Hour) "Hm" else "hm", time)
        return when (relative) {
            RelativeTime.JustNow -> res.getString(R.string.relative_date_just_now)
            is RelativeTime.MinutesAgo -> res.getQuantityString(R.plurals.relative_date_minutes, relative.minutes, relative.minutes)
            is RelativeTime.Today -> res.getString(R.string.relative_date_today, timeOfDay(relative.time))
            is RelativeTime.Yesterday -> res.getString(R.string.relative_date_yesterday, timeOfDay(relative.time))
            is RelativeTime.ThisYear -> pattern(if (is24Hour) "MMMdHm" else "MMMdhm", relative.time)
            is RelativeTime.Earlier -> pattern("yMMMd", relative.time)
        }
    }

    @JvmStatic
    fun isToday(timestamp: Long): Boolean {
        val date = DateFormat.getDateInstance().format(timestamp)
        val todayDate = DateFormat.getDateInstance().format(System.currentTimeMillis())
        return date == todayDate
    }

    private fun Calendar.sameDay(other: Calendar) =
        get(Calendar.YEAR) == other.get(Calendar.YEAR) && get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)

    private fun fixTimestamp(timestamp: Long): Long {
        return fixTimestamp(timestamp.toString())
    }

    private fun fixTimestamp(timestampString: String): Long {
        val timestampStrBuilder: StringBuilder = StringBuilder(timestampString)
        while (timestampStrBuilder.length < 13) {
            timestampStrBuilder.append("0")
        }
        return timestampStrBuilder.toString().toLong()
    }

    /** Worked out once for each locale and skeleton: every time shown in a list asks again. */
    private val bestPatterns = ConcurrentHashMap<Pair<Locale, String>, String>()

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
}
