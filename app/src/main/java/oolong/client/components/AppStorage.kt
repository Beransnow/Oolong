package oolong.client.components

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.webkit.WebView
import coil3.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import oolong.client.ui.page.videoview.VideoCache
import oolong.client.utils.ImageCacheUtil
import java.io.File

/**
 * How much of the phone the app takes, as the system's app details count it, and what of it is
 * what: the parts it can tell apart, and the rest of each kind as one.
 */
class StorageUse(
    /** The app itself: its package and what the system compiled of it. */
    val app: Long,
    /** What the app keeps, its caches included. */
    val data: Long,
    /** What it keeps that can go and come back: part of [data]. */
    val cache: Long,
    val images: Long,
    val videos: Long,
    val web: Long,
    /** The models and their runtime, downloaded: part of [data], not of [cache]. */
    val extensions: Long,
) {
    val total: Long get() = app + data

    /** Cache besides pictures, videos and web pages: files kept for a moment, and the like. */
    val temporary: Long get() = (cache - images - videos - web).coerceAtLeast(0)

    /** What is kept that is not cache or the models: the account, settings, history. */
    val records: Long get() = (data - cache - extensions).coerceAtLeast(0)
}

/** Measures and clears what the app keeps on the phone. */
object AppStorage {
    private const val VideoDir = "video"
    private const val WebDir = "WebView"

    /** What [extensions] bytes of downloaded models stand beside: everything measured once. */
    suspend fun measure(context: Context, extensions: Long): StorageUse = withContext(Dispatchers.IO) {
        val (app, data, cache) = systemCounts(context) ?: ownCounts(context)
        StorageUse(
            app = app,
            data = data,
            cache = cache,
            images = imageDirs(context).sumOf(::sizeOf),
            videos = sizeOf(File(context.cacheDir, VideoDir)),
            web = sizeOf(File(context.cacheDir, WebDir)),
            extensions = extensions,
        )
    }

    /** How much the app takes in all, quicker than [measure]. */
    suspend fun total(context: Context): Long = withContext(Dispatchers.IO) {
        val (app, data, _) = systemCounts(context) ?: ownCounts(context)
        app + data
    }

    /** The system's own counts, the same as its app details show; null where they cannot be had. */
    private fun systemCounts(context: Context): Triple<Long, Long, Long>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return runCatching {
            val manager = context.getSystemService(StorageStatsManager::class.java)
            // Asking about itself needs no permission
            val stats = manager.queryStatsForPackage(context.applicationInfo.storageUuid, context.packageName, Process.myUserHandle())
            Triple(stats.appBytes, stats.dataBytes, stats.cacheBytes)
        }.getOrNull()
    }

    /** Counted from the files, where the system will not say: close to its own. */
    private fun ownCounts(context: Context): Triple<Long, Long, Long> {
        val info = context.applicationInfo
        val app = (listOf(info.sourceDir) + info.splitSourceDirs.orEmpty()).sumOf { File(it).length() }
        val cache = listOfNotNull(context.cacheDir, context.codeCacheDir, context.externalCacheDir).sumOf(::sizeOf)
        val data = sizeOf(File(info.dataDir)) + sizeOf(context.getExternalFilesDir(null)?.parentFile)
        return Triple(app, data, cache)
    }

    private fun imageDirs(context: Context): List<File> = listOfNotNull(context.imageLoader.diskCache?.directory?.toFile()) + oldImageDirs(context)

    /** Where the image libraries used before kept their caches, left behind on updated installs. */
    private fun oldImageDirs(context: Context): List<File> = listOfNotNull(context.cacheDir, context.externalCacheDir).flatMap {
        listOf(File(it, ImageCacheUtil.DEFAULT_DISK_CACHE_DIR), File(it, "sketch3"))
    }

    suspend fun clearImages(context: Context) = withContext(Dispatchers.IO) {
        context.imageLoader.diskCache?.clear()
        context.imageLoader.memoryCache?.clear()
        oldImageDirs(context).forEach { it.deleteRecursively() }
    }

    suspend fun clearVideos(context: Context) = withContext(Dispatchers.IO) { VideoCache.clear(context) }

    /** Web pages' cache, through a web view: one may be open and own the files. */
    suspend fun clearWeb(context: Context) = withContext(Dispatchers.Main) {
        runCatching { WebView(context).apply { clearCache(true) }.destroy() }
        Unit
    }

    /**
     * Every other cached file: what is left of downloads, pictures made to send or share. What
     * was written in the last minutes is kept, as it may be in use.
     */
    suspend fun clearTemporary(context: Context) = withContext(Dispatchers.IO) {
        val keep = imageDirs(context).map { it.absoluteFile } +
            File(context.cacheDir, VideoDir).absoluteFile +
            File(context.cacheDir, WebDir).absoluteFile +
            // The system's own, compiled from the app
            File(context.cacheDir, "oat_primary").absoluteFile
        val before = System.currentTimeMillis() - RecentMillis
        for (base in listOfNotNull(context.cacheDir, context.externalCacheDir)) {
            base.listFiles()?.filter { it.absoluteFile !in keep }?.forEach { deleteOlder(it, before) }
        }
    }

    suspend fun clearCache(context: Context) {
        clearImages(context)
        clearVideos(context)
        clearWeb(context)
        clearTemporary(context)
    }

    /** Everything, as the system's own button does: the app is stopped once it is gone. */
    fun clearAll(context: Context) {
        context.getSystemService(ActivityManager::class.java).clearApplicationUserData()
    }

    private fun deleteOlder(file: File, before: Long) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteOlder(it, before) }
            if (file.listFiles().isNullOrEmpty()) file.delete()
        } else if (file.lastModified() < before) {
            file.delete()
        }
    }

    private fun sizeOf(file: File?): Long = when {
        file == null || !file.exists() -> 0
        file.isDirectory -> file.listFiles()?.sumOf(::sizeOf) ?: 0
        else -> file.length()
    }

    private const val RecentMillis = 10 * 60 * 1000L
}
