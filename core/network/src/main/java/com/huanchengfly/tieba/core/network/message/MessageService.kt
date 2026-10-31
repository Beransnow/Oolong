package com.huanchengfly.tieba.core.network.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.POST

interface MessageService {
    @POST("c/s/msg")
    suspend fun reminder(@Body body: RequestBody): MessageReminderResponse
}

@Serializable
data class MessageReminderResponse(val message: Counts? = null) {
    /** Unread counts, as strings like everything the server sends. */
    @Serializable
    data class Counts(
        val agree: String? = null,
        @SerialName("replyme") val replyMe: String? = null,
        @SerialName("atme") val atMe: String? = null,
        val fans: String? = null,
        val pletter: String? = null,
        val bookmark: String? = null,
    )
}
