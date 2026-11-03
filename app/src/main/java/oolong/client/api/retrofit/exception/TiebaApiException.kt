package oolong.client.api.retrofit.exception

import oolong.client.api.models.CommonResponse

class TiebaApiException(
    private val commonResponse: CommonResponse
) : TiebaException(commonResponse.errorMsg.takeIf { it.isNotEmpty() } ?: "未知错误") {
    override val code: Int
        get() = commonResponse.errorCode

    override fun toString(): String {
        return "TiebaApiException(code=$code, message=$message, commonResponse=$commonResponse)"
    }
}
