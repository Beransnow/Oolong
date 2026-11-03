package oolong.client.migration

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import oolong.client.BuildConfig
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Bringing data over from the old app (贴吧 Lite, `com.huanchengfly.tieba.post`).
 *
 * Its last version is only an exporter: Oolong starts it for a result, it checks that the caller
 * is Oolong, asks the person, and returns a read grant to a zip of its raw files. Oolong checks in
 * turn that the zip came from the old app's key, unpacks it into a staging folder and starts again;
 * the next process moves the files into place before anything has opened them ([applyStaged]),
 * and the usual paths read them: Room's migrations or the old-database import, DataStore with
 * [LegacyPackageMigration], and preferences rewritten here.
 */
object LegacyImport {
    const val PACKAGE = "com.huanchengfly.tieba.post"
    private const val ACTION_EXPORT = "$PACKAGE.action.EXPORT_DATA"

    /** The first version of the old app that can export. */
    private const val EXPORTER_VERSION = 390200L

    /** Certificates the old app is signed with: its release key, and in debug builds the debug key. */
    private val OLD_CERTIFICATES: Set<String> = buildSet {
        add("9292cc30b4e5e7d1fc0c485ced3afd5769fc25c77498570dcd28eedd96588589")
        if (BuildConfig.DEBUG) add("cbfebf02b64acc8ff13f92a716205bb706bdf18fda3bf543a4277c395d2b25a0")
    }

    enum class Source {
        /** No old app. */
        None,

        /** The old app, too old to export: it needs its last version first. */
        NeedsUpdate,

        /** The old app's exporter, ready. */
        Ready,
    }

    fun source(context: Context): Source {
        val info = try {
            context.packageManager.getPackageInfo(PACKAGE, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return Source.None
        }
        if (!isOldApp(context)) return Source.None
        return if (PackageInfoCompat.getLongVersionCode(info) >= EXPORTER_VERSION) Source.Ready else Source.NeedsUpdate
    }

    /** Starts the exporter; the result is the export's URI, or null when nothing came back. */
    object Export : ActivityResultContract<Unit, Uri?>() {
        override fun createIntent(context: Context, input: Unit) = Intent(ACTION_EXPORT).setPackage(PACKAGE)

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            intent?.data?.takeIf { resultCode == Activity.RESULT_OK }
    }

    /**
     * Reads the export at [uri] into the staging folder. Only files Oolong knows go in, by name;
     * anything else in the zip is skipped. Returns whether there is something to apply.
     */
    suspend fun receive(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        if (!isOldApp(context)) return@withContext false
        val staging = stagingDir(context).apply {
            deleteRecursively()
            mkdirs()
        }
        var count = 0
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (entry.isDirectory || !ALLOWED.matches(name)) continue
                    val out = File(staging, name)
                    // Belt and braces against names climbing out of the folder
                    if (!out.canonicalPath.startsWith(staging.canonicalPath + File.separator)) continue
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                    count++
                }
            }
        } ?: return@withContext false
        if (count == 0) return@withContext false
        File(staging, READY).writeText("1")
        true
    }

    /** Starts the app again so [applyStaged] runs before anything opens the files. */
    fun restart(activity: Activity) {
        val launch = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(launch)
        activity.finishAffinity()
        Runtime.getRuntime().exit(0)
    }

    /**
     * Moves staged files into place. Call from [Application.attachBaseContext], in the main process
     * only: it runs before content providers, Hilt and anything that opens a database or
     * preferences file.
     */
    fun applyStaged(base: Context) {
        val staging = stagingDir(base)
        if (!File(staging, READY).exists() || !isMainProcess(base)) return
        val dataDir = File(base.applicationInfo.dataDir)
        runCatching {
            val databases = File(dataDir, "databases").apply { mkdirs() }
            val stagedRoom = File(staging, "databases/$ROOM_DB")
            val stagedLegacy = File(staging, "databases/$LEGACY_DB")
            if (stagedRoom.exists() || stagedLegacy.exists()) {
                // Whatever Oolong made of its own first run goes; without a Room file, the
                // old-database import runs when Room creates its database
                ROOM_FILES.forEach { File(databases, it).delete() }
                File(databases, LEGACY_DB).delete()
                if (stagedRoom.exists()) stagedRoom.copyTo(File(databases, ROOM_DB), overwrite = true)
                if (stagedLegacy.exists()) stagedLegacy.copyTo(File(databases, LEGACY_DB), overwrite = true)
            }
            val stagedDataStore = File(staging, "files/datastore").listFiles().orEmpty()
            val prefs = File(dataDir, "shared_prefs").apply { mkdirs() }
            File(staging, "shared_prefs").listFiles().orEmpty().forEach { xml ->
                // Settings already moved to DataStore would be moved again over newer values
                if (xml.name == "settings.xml" && stagedDataStore.isNotEmpty()) return@forEach
                File(prefs, xml.name).writeText(rewritePackage(xml.readText()))
            }
            if (stagedDataStore.isNotEmpty()) {
                val dir = File(base.filesDir, "datastore").apply { mkdirs() }
                stagedDataStore.forEach { it.copyTo(File(dir, it.name), overwrite = true) }
            }
            File(staging, "files").listFiles { f -> f.isFile }.orEmpty()
                .forEach { it.copyTo(File(base.filesDir, it.name), overwrite = true) }
            File(base.filesDir, IMPORTED).writeText(System.currentTimeMillis().toString())
        }
        staging.deleteRecursively()
    }

    /** Whether an import finished since the last time this was asked; asks only once. */
    fun consumeImported(context: Context): Boolean {
        val marker = File(context.filesDir, IMPORTED)
        return marker.exists().also { if (it) marker.delete() }
    }

    /**
     * The old app's names in stored text: its package in class names (the launcher icon chosen)
     * and in absolute paths (the translucent theme's wallpaper). Both forms map to Oolong's, and
     * the old main activity to Oolong's, which dropped its "V2".
     */
    fun rewritePackage(text: String): String =
        text.replace(PACKAGE, NEW_PACKAGE).replace("$NEW_PACKAGE.MainActivityV2", "$NEW_PACKAGE.MainActivity")

    private const val NEW_PACKAGE = "oolong.client"
    private const val ROOM_DB = "app.db"
    private const val LEGACY_DB = "tblite.db"
    private val ROOM_FILES = listOf(ROOM_DB, "$ROOM_DB-wal", "$ROOM_DB-shm", "$ROOM_DB-journal")
    private const val READY = ".ready"
    private const val IMPORTED = "legacy-imported"
    private val ALLOWED = Regex("""databases/(app|tblite)\.db|shared_prefs/[\w.-]+\.xml|files/datastore/[\w.-]+\.preferences_pb|files/[\w.-]+\.(jpg|jpeg|png|webp)""")

    private fun stagingDir(context: Context) = File(context.filesDir, "legacy-import")

    private fun isMainProcess(context: Context): Boolean {
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            runCatching { File("/proc/${Process.myPid()}/cmdline").readText().trim('\u0000', ' ', '\n') }.getOrNull()
        }
        return name == null || name == context.packageName
    }

    @SuppressLint("PackageManagerGetSignatures")
    @Suppress("DEPRECATION")
    private fun isOldApp(context: Context): Boolean {
        val pm = context.packageManager
        val signatures = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo ?: return false
                if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
            } else {
                pm.getPackageInfo(PACKAGE, PackageManager.GET_SIGNATURES).signatures
            }
        } catch (e: PackageManager.NameNotFoundException) {
            return false
        }
        return signatures.orEmpty().any { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) } in OLD_CERTIFICATES
        }
    }
}
