package com.huanchengfly.tieba.post.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.huanchengfly.tieba.core.network.upload.PictureChunk
import com.huanchengfly.tieba.core.network.upload.PictureWatermark
import com.huanchengfly.tieba.core.network.upload.UploadPictureResponse
import com.huanchengfly.tieba.post.App
import com.huanchengfly.tieba.post.api.legacyUploadRemoteDataSource
import com.huanchengfly.tieba.post.api.models.PicInfo
import com.huanchengfly.tieba.post.api.models.PicInfoItem
import com.huanchengfly.tieba.post.api.models.UploadPictureResultBean
import com.huanchengfly.tieba.post.api.retrofit.exception.TiebaException
import com.huanchengfly.tieba.post.api.retrofit.exception.getErrorCode
import com.huanchengfly.tieba.post.api.retrofit.exception.getErrorMessage
import com.huanchengfly.tieba.post.utils.ImageUtil
import com.huanchengfly.tieba.post.utils.MD5Util
import com.huanchengfly.tieba.post.utils.appPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.toByteString
import java.io.File
import java.io.RandomAccessFile

class ImageUploader(
    private val forumName: String,
    private val chunkSize: Int = DEFAULT_CHUNK_SIZE
) {
    companion object {
        const val DEFAULT_CHUNK_SIZE = 512000

        const val IMAGE_MAX_SIZE = 5242880
        const val ORIGIN_IMAGE_MAX_SIZE = 10485760

        const val PIC_WATER_TYPE_NO = "0"
        const val PIC_WATER_TYPE_USER_NAME = "1"
        const val PIC_WATER_TYPE_FORUM_NAME = "2"
    }

    fun uploadImages(
        filePaths: List<String>,
        isOriginImage: Boolean = false,
    ): Flow<List<UploadPictureResultBean>> {
        return filePaths.asFlow()
            .map { filePath ->
                uploadSinglePicture(filePath, isOriginImage)
            }
            .runningFold<UploadPictureResultBean, MutableList<UploadPictureResultBean>>(initial = mutableListOf()) { list, result ->
                list.add(result)
                list
            }
            .filter { it.size == filePaths.size }
    }

    private suspend fun compressImage(
        filePath: String,
        isOriginImage: Boolean
    ): File {
        val originFile = File(filePath)
        val fileLength = originFile.length()
        val maxSize = if (isOriginImage) ORIGIN_IMAGE_MAX_SIZE else IMAGE_MAX_SIZE
        val tempFile = withContext(Dispatchers.IO) {
            File.createTempFile("temp", ".tmp")
        }
        withContext<Unit>(Dispatchers.IO) {
            if (isOriginImage && fileLength <= maxSize) {
                originFile.copyTo(tempFile, true)
            } else {
                val bitmap = BitmapFactory.decodeFile(filePath)
                val firstCompressResult = ImageUtil.compressImage(bitmap, quality = 95)
                tempFile.writeBytes(firstCompressResult)
                if (firstCompressResult.size > maxSize) {
                    // 压缩尺寸至 1080P
                    val width = bitmap.width
                    val height = bitmap.height
                    val scale = if (width > height) {
                        1080f / width
                    } else {
                        1080f / height
                    }
                    if (scale < 1) {
                        val newWidth = (width * scale).toInt()
                        val newHeight = (height * scale).toInt()
                        val newBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                        tempFile.writeBytes(ImageUtil.compressImage(newBitmap, quality = 95))
                    }
                }
            }
        }
        return tempFile
    }

    suspend fun uploadSinglePicture(
        filePath: String,
        isOriginImage: Boolean = false,
    ): UploadPictureResultBean {
        val option = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(filePath, option)
        val width = option.outWidth
        val height = option.outHeight
        check(width > 0 && height > 0) { "图片宽高不正确" }
        val file = compressImage(filePath, isOriginImage)
        val fileLength = file.length()
        val maxSize = if (isOriginImage) ORIGIN_IMAGE_MAX_SIZE else IMAGE_MAX_SIZE
        check(fileLength <= maxSize) { "图片大小超过限制" }
        val fileMd5 = MD5Util.toMd5(file)
        val isMultipleChunkSize = fileLength % chunkSize == 0L
        val totalChunkNum = fileLength / chunkSize + if (isMultipleChunkSize) 0 else 1
        val picWatermarkType =
            App.INSTANCE.appPreferences.picWatermarkType ?: PIC_WATER_TYPE_FORUM_NAME
        val watermark = when (picWatermarkType) {
            PIC_WATER_TYPE_USER_NAME -> PictureWatermark.USER_NAME
            PIC_WATER_TYPE_FORUM_NAME -> PictureWatermark.FORUM_NAME
            else -> PictureWatermark.NONE
        }
        try {
            var last: UploadPictureResponse? = null
            for (chunk in 0 until totalChunkNum) {
                val isFinish = chunk == totalChunkNum - 1
                val curChunkSize = if (isFinish && !isMultipleChunkSize) {
                    fileLength % chunkSize
                } else {
                    chunkSize
                }.toInt()
                val chunkBytes = ByteArray(curChunkSize)
                withContext(Dispatchers.IO) {
                    RandomAccessFile(file, "r").use {
                        it.seek(chunk * chunkSize.toLong())
                        it.read(chunkBytes)
                    }
                }
                last = legacyUploadRemoteDataSource.uploadPictureChunk(
                    PictureChunk(
                        resourceId = "$fileMd5$chunkSize",
                        chunkNo = chunk.toInt() + 1,
                        isFinish = isFinish,
                        size = fileLength,
                        width = width,
                        height = height,
                        saveOrigin = isOriginImage,
                        watermark = watermark,
                        forumName = forumName,
                        bytes = chunkBytes.toByteString(),
                    )
                )
            }
            val result = checkNotNull(last).toUploadPictureResultBean()
            if (result.picId.isNullOrEmpty() || result.picInfo == null) {
                throw UploadPictureFailedException()
            }
            return result
        } catch (e: UploadPictureFailedException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw UploadPictureFailedException(e.getErrorCode(), e.getErrorMessage())
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                file.delete()
            }
        }
    }
}

class UploadPictureFailedException(
    override val code: Int = -1,
    override val message: String = "上传图片失败",
) : TiebaException(message)

private fun UploadPictureResponse.toUploadPictureResultBean(): UploadPictureResultBean {
    fun UploadPictureResponse.Pic?.toItem() = PicInfoItem(
        width = this?.width.orEmpty(),
        height = this?.height.orEmpty(),
        type = this?.type.orEmpty(),
        picUrl = this?.picUrl.orEmpty(),
    )
    return UploadPictureResultBean(
        errorCode = "0",
        errorMsg = "",
        resourceId = resourceId,
        chunkNo = chunkNo.orEmpty(),
        picId = picId,
        picInfo = picInfo?.let {
            PicInfo(originPic = it.originPic.toItem(), bigPic = it.bigPic.toItem(), smallPic = it.smallPic.toItem())
        },
    )
}
