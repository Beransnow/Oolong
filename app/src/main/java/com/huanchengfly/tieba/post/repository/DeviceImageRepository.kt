package com.huanchengfly.tieba.post.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.huanchengfly.tieba.post.arch.IoDispatcher
import com.huanchengfly.tieba.post.model.DeviceImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** The pictures on the device that the app may read, for picking without the system photo picker. */
class DeviceImageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** Every readable picture, newest first. */
    suspend fun images(): List<DeviceImage> = withContext(io) {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
        )
        val cursor = context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Images.Media.SIZE} > 0",
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        ) ?: return@withContext emptyList()
        cursor.use {
            val id = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketId = it.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketName = it.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val mimeType = it.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            buildList(it.count) {
                while (it.moveToNext()) {
                    add(
                        DeviceImage(
                            uri = ContentUris.withAppendedId(collection, it.getLong(id)),
                            albumId = it.getLong(bucketId),
                            albumName = it.getString(bucketName).orEmpty(),
                            mimeType = it.getString(mimeType).orEmpty(),
                        )
                    )
                }
            }
        }
    }
}
