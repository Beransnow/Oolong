package oolong.client.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UploadPictureResultBean(
    @SerialName("error_code")
    val errorCode: String,
    @SerialName("error_msg")
    val errorMsg: String,
    // Only the response to the last chunk carries the picture fields
    val resourceId: String? = null,
    val chunkNo: String,
    val picId: String? = null,
    val picInfo: PicInfo? = null,
)

@Serializable
data class PicInfo(
    val originPic: PicInfoItem,
    val bigPic: PicInfoItem,
    val smallPic: PicInfoItem,
)

@Serializable
data class PicInfoItem(
    val width: String,
    val height: String,
    val type: String,
    val picUrl: String
)
