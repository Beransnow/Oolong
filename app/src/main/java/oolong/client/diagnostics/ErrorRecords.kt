package oolong.client.diagnostics

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import oolong.client.BuildConfig
import oolong.client.data.error.isUnexpectedError
import oolong.core.network.UnreadableResponseException
import org.json.JSONObject
import retrofit2.HttpException
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.WeakHashMap

/**
 * Errors the app caught but could not explain to the person, kept on the device as crashes are
 * ([CrashLogs]), so that the person can read them and pass one on. Only errors that were shown, as
 * an error page or a message, and only those of no known kind: not a lost connection, not an error
 * the server named. One problem is one record, counting how often it came. Nothing is sent unless
 * the person sends a record themselves.
 */
object ErrorRecords {
    @Immutable
    data class Entry(val id: String, val time: Long, val title: String, val count: Int)

    /** Errors already taken in, as one error is often shown more than once. */
    private val taken: MutableSet<Throwable> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    /**
     * Keeps [error], shown to the person, if it is of no known kind; returns the id of its record,
     * or null when it isn't kept. Blocking.
     */
    fun record(context: Context, error: Throwable, time: Long = System.currentTimeMillis()): String? {
        if (!error.isUnexpectedError()) return null
        val detail = detailOf(error)
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val id = fingerprint(error, detail)
        if (!taken.add(error)) return id
        synchronized(this) {
            val dir = dir(context).apply { mkdirs() }
            val file = File(dir, "$id.txt")
            val previous = file.takeIf { it.isFile }?.readText()
            val count = (previous?.let { header(it, CountField)?.toIntOrNull() } ?: 0) + 1
            val first = previous?.let { header(it, FirstField) } ?: format(time)
            val header = listOf(
                "Oolong ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}",
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}",
                "System: ${Build.DISPLAY}",
                "$FirstField: $first",
                "$LastField: ${format(time)}",
                "$CountField: $count",
                "Page: ${CrashLogs.page ?: "-"}",
                detail,
            )
            file.writeText(CrashLogs.redact(header.joinToString("\n") + "\n\n" + trace))
            file.setLastModified(time)
            // A problem that comes again may be sent again
            preferences(context).edit { putStringSet(Sent, sent(context) - id) }
            files(context).drop(MaxKept).forEach { it.delete() }
        }
        return id
    }

    /** The records kept, the latest first. */
    fun list(context: Context): List<Entry> = files(context).mapNotNull { file ->
        val text = runCatching { file.readText() }.getOrNull() ?: return@mapNotNull null
        val title = text.substringBefore("\n\n").lines().lastOrNull().orEmpty()
        Entry(file.nameWithoutExtension, file.lastModified(), title, header(text, CountField)?.toIntOrNull() ?: 1)
    }

    fun read(context: Context, id: String): String? = File(dir(context), "$id.txt").takeIf { it.isFile }?.readText()

    fun clear(context: Context) {
        files(context).forEach { it.delete() }
        preferences(context).edit { remove(Sent) }
    }

    /** Whether the record [id] can be sent: only those of release builds, whose traces the developer can read. */
    fun canSend(context: Context, id: String): Boolean =
        read(context, id)?.lines()?.firstOrNull()?.endsWith(" release") == true

    fun isSent(context: Context, id: String): Boolean = id in sent(context)

    /** Sends the record [id] to the developer, as the person asked; true once it is sent. Blocking. */
    fun send(context: Context, id: String): Boolean {
        val record = read(context, id) ?: return false
        val headerLines = record.substringBefore("\n\n").lines()
        val version = Regex("""^Oolong (\S+) \(\d+\) release$""").find(headerLines.firstOrNull().orEmpty())?.groupValues?.get(1)
            ?: return false
        val report = JSONObject()
            .put("version", version)
            .put("kind", "error")
            .put("trace", record.substringAfter("\n\n", "").take(MaxTrace))
            .put("detail", headerLines.lastOrNull().orEmpty().take(200))
            .put("count", header(record, CountField)?.toIntOrNull() ?: 1)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}".take(100))
            .put("rom", Build.DISPLAY.take(100))
            .apply { header(record, "Page")?.takeIf { it != "-" }?.let { put("page", it.take(100)) } }
            .put("install", CrashReports.installId(context))
        val sent = CrashReports.post(report)
        if (sent) preferences(context).edit { putStringSet(Sent, sent(context) + id) }
        return sent
    }

    /**
     * What the error came from, in a line: the endpoint and status for an HTTP error, the endpoint
     * and what came for a response that couldn't be read, else the exception and its message.
     */
    internal fun detailOf(error: Throwable): String {
        val causes = generateSequence(error) { it.cause.takeIf { cause -> cause !== it } }.toList()
        causes.firstNotNullOfOrNull { it as? HttpException }?.let { http ->
            val response = http.response()
            val raw = response?.raw()
            // Retrofit keeps the body, and with it its type, apart from the raw response
            val type = (response?.errorBody()?.contentType() ?: raw?.header("Content-Type")?.toMediaTypeOrNull())
                ?.let { "${it.type}/${it.subtype}" }
            return listOfNotNull("HTTP ${http.code()}", raw?.request?.url?.encodedPath, type).joinToString(" · ")
        }
        causes.firstNotNullOfOrNull { it as? UnreadableResponseException }?.let { unreadable ->
            val reason = unreadable.cause?.let { it.javaClass.simpleName }
            return listOfNotNull("Unreadable response", unreadable.endpoint, unreadable.contentType, reason).joinToString(" · ")
        }
        val root = causes.last()
        val message = root.message?.lineSequence()?.firstOrNull()?.take(120)
        return listOfNotNull(root.javaClass.simpleName, message).joinToString(": ")
    }

    /** One problem's id: the line above and where in the app's own code it was thrown. */
    private fun fingerprint(error: Throwable, detail: String): String {
        val root = generateSequence(error) { it.cause.takeIf { cause -> cause !== it } }.last()
        val frames = generateSequence(error) { it.cause.takeIf { cause -> cause !== it } }
            .flatMap { it.stackTrace.asSequence() }
            .filter { it.className.startsWith(OwnPackage) }
            .take(3)
            .joinToString("\n") { "${it.className}.${it.methodName}" }
        // Counts and the like change between two of the same problem
        val stable = detail.replace(Regex("""\d{4,}"""), "#")
        val text = listOf(root.javaClass.name, stable, frames).joinToString("\n")
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
    }

    private fun header(record: String, name: String): String? =
        record.substringBefore("\n\n").lines().firstOrNull { it.startsWith("$name: ") }?.removePrefix("$name: ")

    private fun format(time: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(time))

    private fun files(context: Context): List<File> =
        dir(context).listFiles { file -> file.isFile && file.name.endsWith(".txt") }.orEmpty().sortedByDescending { it.lastModified() }

    private fun sent(context: Context): Set<String> = preferences(context).getStringSet(Sent, emptySet()).orEmpty()

    private fun preferences(context: Context) = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)

    private fun dir(context: Context) = File(context.filesDir, "errors")

    private const val OwnPackage = "oolong.client."
    private const val Preferences = "error_records"
    private const val Sent = "sent"
    private const val FirstField = "First"
    private const val LastField = "Last"
    private const val CountField = "Count"
    private const val MaxKept = 20

    /** The Worker takes reports of up to 64 KB; escaped as JSON, a trace may grow by a part. */
    private const val MaxTrace = 48 * 1024
}
