package com.huanchengfly.tieba.core.network.upload

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.POST

interface UploadService {
    @POST("c/s/uploadPicture")
    suspend fun uploadPicture(@Body body: RequestBody): UploadPictureResponse

    @POST("c/c/img/portrait")
    suspend fun uploadPortrait(@Body body: RequestBody): JsonObject
}

@Serializable
data class UploadPictureResponse(
    val resourceId: String? = null,
    val chunkNo: String? = null,
    /** Only the response to the last chunk carries the picture. */
    val picId: String? = null,
    val picInfo: PicInfo? = null,
) {
    @Serializable
    data class PicInfo(
        val originPic: Pic? = null,
        val bigPic: Pic? = null,
        val smallPic: Pic? = null,
    )

    @Serializable
    data class Pic(
        val width: String? = null,
        val height: String? = null,
        val type: String? = null,
        val picUrl: String? = null,
    )
}
