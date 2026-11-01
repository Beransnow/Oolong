package com.huanchengfly.tieba.post.utils

import android.content.Context
import android.os.Looper
import android.text.TextUtils
import coil3.imageLoader
import java.io.File
import java.math.BigDecimal
import kotlin.concurrent.thread

/**
 * 图片缓存工具类
 * Created by Trojx on 2016/10/10 0010.
 */
object ImageCacheUtil {
    const val DEFAULT_DISK_CACHE_DIR = "image_manager_disk_cache"

    /**
     * 清除图片磁盘缓存
     */
    fun clearImageDiskCache(context: Context) {
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                thread { context.imageLoader.diskCache?.clear() }
            } else {
                context.imageLoader.diskCache?.clear()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 清除图片内存缓存
     */
    fun clearImageMemoryCache(context: Context) {
        try {
            context.imageLoader.memoryCache?.clear()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 清除图片所有缓存
     */
    fun clearImageAllCache(context: Context) {
        clearImageDiskCache(context)
        clearImageMemoryCache(context)
        val imageExternalCacheDir =
            context.externalCacheDir.toString() + File.separator + DEFAULT_DISK_CACHE_DIR
        deleteFolderFile(imageExternalCacheDir, false)
        deleteFolderFile(context.cacheDir.toString() + File.separator + ".shareTemp", false)
        legacyCacheDirs(context).forEach { deleteFolderFile(it.path, true) }
    }

    /**
     * 获取缓存大小
     *
     * @return CacheSize
     */
    fun getCacheSize(context: Context): String {
        try {
            val glideCacheSize = getFolderSize(
                File(
                    context.cacheDir,
                    DEFAULT_DISK_CACHE_DIR
                )
            ).toDouble()
            val shareCacheSize = getFolderSize(File(context.cacheDir, ".shareTemp")).toDouble()
            val imageCacheSize = (context.imageLoader.diskCache?.size ?: 0L).toDouble()
            val legacyCacheSize = legacyCacheDirs(context).sumOf { getFolderSize(it) }.toDouble()
            return getFormatSize(glideCacheSize + shareCacheSize + imageCacheSize + legacyCacheSize)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    /** Where the image library used before kept its cache, left behind on updated installs. */
    private fun legacyCacheDirs(context: Context): List<File> =
        listOfNotNull(context.externalCacheDir, context.cacheDir).map { File(it, "sketch3") }.filter { it.exists() }

    /**
     * 获取指定文件夹内所有文件大小的和
     *
     * @param file file
     * @return size
     */
    private fun getFolderSize(file: File): Long {
        var size: Long = 0
        try {
            val fileList = file.listFiles() ?: return 0
            for (aFileList in fileList) {
                size = if (aFileList.isDirectory) {
                    size + getFolderSize(aFileList)
                } else {
                    size + aFileList.length()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return size
    }

    /**
     * 删除指定目录下的文件，这里用于缓存的删除
     *
     * @param filePath       filePath
     * @param deleteThisPath deleteThisPath
     */
    private fun deleteFolderFile(filePath: String, deleteThisPath: Boolean) {
        if (!TextUtils.isEmpty(filePath)) {
            try {
                val file = File(filePath)
                if (file.isDirectory) {
                    file.listFiles()?.forEach {
                        deleteFolderFile(it.absolutePath, true)
                    }
                }
                if (deleteThisPath) {
                    if (!file.isDirectory) {
                        file.delete()
                    } else {
                        if (file.listFiles().isNullOrEmpty()) {
                            file.delete()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 格式化单位
     *
     * @param size size
     * @return size
     */
    fun getFormatSize(size: Double): String {
        val kiloByte = size / 1024
        if (kiloByte < 1) {
            return size.toString() + "B"
        }
        val megaByte = kiloByte / 1024
        if (megaByte < 1) {
            val result1 = BigDecimal(java.lang.Double.toString(kiloByte))
            return result1.setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString() + "KB"
        }
        val gigaByte = megaByte / 1024
        if (gigaByte < 1) {
            val result2 = BigDecimal(java.lang.Double.toString(megaByte))
            return result2.setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString() + "MB"
        }
        val teraBytes = gigaByte / 1024
        if (teraBytes < 1) {
            val result3 = BigDecimal(java.lang.Double.toString(gigaByte))
            return result3.setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString() + "GB"
        }
        val result4 = BigDecimal(teraBytes)
        return result4.setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString() + "TB"
    }
}
