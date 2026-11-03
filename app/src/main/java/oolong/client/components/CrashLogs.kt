package oolong.client.components

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import oolong.client.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Why the app went down, kept on the device for the person to read or pass on: crashes are
 * written as they happen, and on Android 11 and later, freezes and native crashes are read from
 * the system's record of how the process ended the next time it starts. Nothing leaves the device.
 */
object CrashLogs {
    enum class Kind { Crash, Freeze, Native }

    @Immutable
    data class Entry(val id: String, val time: Long, val kind: Kind, val title: String)

    /** The page on top when the app went down, set by the navigation as it changes. */
    @Volatile
    var page: String? = null

    /** Records uncaught exceptions before the default handler takes the process down. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Keeps [error], thrown on [thread] and not caught, as a crash at [time]. */
    internal fun record(context: Context, thread: Thread, error: Throwable, time: Long = System.currentTimeMillis()) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        write(context, Kind.Crash, time, "Thread: ${thread.name}", trace)
    }

    /** Takes in how the process last ended, when that was a freeze or a native crash. */
    fun collectExits(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) runCatching { collect(context) }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun collect(context: Context) {
        val preferences = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
        val since = preferences.getLong(ExitsSeen, -1L)
        val exits = context.getSystemService(ActivityManager::class.java)
            ?.getHistoricalProcessExitReasons(null, 0, 10)
            .orEmpty()
        // Ends from before the app kept these are not taken in
        if (since >= 0) {
            exits.filter { it.timestamp > since }.forEach { exit ->
                when (exit.reason) {
                    ApplicationExitInfo.REASON_ANR -> {
                        val trace = exit.traceInputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                        write(context, Kind.Freeze, exit.timestamp, exit.description.orEmpty(), trace.take(MaxTrace))
                    }
                    ApplicationExitInfo.REASON_CRASH_NATIVE ->
                        write(context, Kind.Native, exit.timestamp, exit.description.orEmpty(), "")
                }
            }
        }
        val latest = exits.maxOfOrNull { it.timestamp } ?: 0L
        preferences.edit { putLong(ExitsSeen, maxOf(since, latest)) }
    }

    /** The records kept, newest first. */
    fun list(context: Context): List<Entry> = files(context).mapNotNull { file ->
        val (time, kind) = parseName(file.name) ?: return@mapNotNull null
        val title = file.useLines { lines -> lines.dropWhile { it.isNotBlank() }.firstOrNull { it.isNotBlank() } }.orEmpty()
        Entry(file.name, time, kind, title.trim())
    }

    fun read(context: Context, id: String): String? =
        File(dir(context), id).takeIf { it.isFile }?.readText()

    fun clear(context: Context) {
        files(context).forEach { it.delete() }
    }

    /** The newest record made since the last time one was pointed out, if any; it is not pointed out again. */
    fun takeUnnoticed(context: Context): Entry? {
        val preferences = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
        val noticed = preferences.getLong(Noticed, 0L)
        val newest = list(context).firstOrNull() ?: return null
        if (newest.time <= noticed) return null
        preferences.edit { putLong(Noticed, newest.time) }
        return newest
    }

    private fun write(context: Context, kind: Kind, time: Long, detail: String, body: String) {
        val dir = dir(context).apply { mkdirs() }
        val header = listOf(
            "Oolong ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}",
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}",
            "System: ${Build.DISPLAY}",
            "Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(time))}",
            "Page: ${page ?: "-"}",
            detail,
        ).filter { it.isNotBlank() }
        File(dir, "${time}_${kind.name}.txt").writeText(redact(header.joinToString("\n") + "\n\n" + body))
        files(context).drop(MaxKept).forEach { it.delete() }
    }

    /** Sign-in tokens never go into a record, should a message carry a cookie. */
    private fun redact(text: String) = Secret.replace(text) { "${it.groupValues[1]}=***" }

    private fun files(context: Context): List<File> =
        dir(context).listFiles { file -> parseName(file.name) != null }.orEmpty().sortedByDescending { parseName(it.name)!!.first }

    private fun parseName(name: String): Pair<Long, Kind>? {
        val parts = name.removeSuffix(".txt").split('_')
        if (parts.size != 2 || !name.endsWith(".txt")) return null
        val time = parts[0].toLongOrNull() ?: return null
        val kind = Kind.entries.firstOrNull { it.name == parts[1] } ?: return null
        return time to kind
    }

    private fun dir(context: Context) = File(context.filesDir, "crashes")

    private const val Preferences = "crash_logs"
    private const val ExitsSeen = "exits_seen"
    private const val Noticed = "noticed"
    private const val MaxKept = 10

    /** A freeze's record holds every thread; the start, with the main thread, is what matters. */
    private const val MaxTrace = 64 * 1024

    private val Secret = Regex("""(BDUSS|STOKEN|PTOKEN|BAIDUID|BDUSS_BFESS|cuid|z_id)=[^;&\s]+""", RegexOption.IGNORE_CASE)
}
