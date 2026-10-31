package com.huanchengfly.tieba.core.network.agree

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.POST

interface AgreeService {
    @POST("c/c/agree/opAgree")
    suspend fun opAgree(@Body body: RequestBody): AgreeResponse
}

@Serializable
data class AgreeResponse(val data: Data? = null) {
    @Serializable
    data class Data(val agree: Agree? = null)

    @Serializable
    data class Agree(
        val score: String? = null,
        /** `"1"` the first time the user agrees with this object. */
        @SerialName("is_first_agree") val isFirstAgree: String? = null,
    )
}
