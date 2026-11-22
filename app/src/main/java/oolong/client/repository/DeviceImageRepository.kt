package oolong.client.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import oolong.client.arch.IoDispatcher
import oolong.client.model.DeviceImage
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
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.ORIENTATION,
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
            val width = it.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val height = it.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val orientation = it.getColumnIndexOrThrow(MediaStore.Images.Media.ORIENTATION)
            buildList(it.count) {
                while (it.moveToNext()) {
                    // Stored sideways, it shows the other way up
                    val sideways = it.getInt(orientation) % 180 != 0
                    add(
                        DeviceImage(
                            uri = ContentUris.withAppendedId(collection, it.getLong(id)),
                            albumId = it.getLong(bucketId),
                            albumName = it.getString(bucketName).orEmpty(),
                            mimeType = it.getString(mimeType).orEmpty(),
                            width = if (sideways) it.getInt(height) else it.getInt(width),
                            height = if (sideways) it.getInt(width) else it.getInt(height),
                        )
                    )
                }
            }
        }
    }
}
