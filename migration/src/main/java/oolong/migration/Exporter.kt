package oolong.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Packs the old app's data for Oolong, as raw files: Oolong reads them itself, so whichever
 * version of the old app was installed, its data goes over as it was.
 *
 * Only what Oolong uses goes in: the databases, preferences, DataStore and the pictures kept in
 * files/ (the translucent theme's wallpaper). Caches, WebView data and logs stay behind.
 */
object Exporter {
    const val FILE_NAME = "oolong-export.zip"

    fun file(context: Context) = File(File(context.cacheDir, "export"), FILE_NAME)

    fun export(context: Context): File {
        val data = File(context.applicationInfo.dataDir)
        val databases = File(data, "databases")
        // Room keeps recent writes in the write-ahead log; fold them into the file before copying
        File(databases, "app.db").takeIf { it.exists() }?.let(::checkpoint)

        val entries = buildList {
            listOf("app.db", "tblite.db").map { File(databases, it) }.filter { it.isFile }
                .forEach { add("databases/${it.name}" to it) }
            File(data, "shared_prefs").listFiles { f -> f.isFile && f.name.endsWith(".xml") }.orEmpty()
                .forEach { add("shared_prefs/${it.name}" to it) }
            File(context.filesDir, "datastore").listFiles { f -> f.isFile && f.name.endsWith(".preferences_pb") }.orEmpty()
                .forEach { add("files/datastore/${it.name}" to it) }
            context.filesDir.listFiles { f -> f.isFile && f.extension.lowercase() in PICTURES }.orEmpty()
                .forEach { add("files/${it.name}" to it) }
        }

        val out = file(context).apply {
            parentFile?.mkdirs()
            delete()
        }
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            for ((name, source) in entries) {
                zip.putNextEntry(ZipEntry(name))
                source.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            // What packed it, so Oolong can tell old formats apart if it ever needs to
            zip.putNextEntry(ZipEntry("export.properties"))
            zip.write("helperVersion=${BuildConfig.VERSION_CODE}\nexportedAt=${System.currentTimeMillis()}\n".toByteArray())
            zip.closeEntry()
        }
        return out
    }

    private fun checkpoint(db: File) {
        runCatching {
            SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
                database.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            }
        }
    }

    private val PICTURES = setOf("jpg", "jpeg", "png", "webp")
}
