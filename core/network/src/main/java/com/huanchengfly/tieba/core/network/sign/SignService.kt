package com.huanchengfly.tieba.core.network.sign

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.POST

interface SignService {
    @POST("c/c/forum/sign")
    suspend fun sign(@Body body: RequestBody): SignResponse
}

@Serializable
data class SignResponse(
    @SerialName("user_info") val userInfo: UserInfo? = null,
    val time: Long? = null,
) {
    @Serializable
    data class UserInfo(
        @SerialName("user_id") val userId: String? = null,
        @SerialName("is_sign_in") val isSignIn: String? = null,
        @SerialName("cont_sign_num") val contSignNum: String? = null,
        @SerialName("user_sign_rank") val userSignRank: String? = null,
        @SerialName("sign_time") val signTime: String? = null,
        @SerialName("sign_bonus_point") val signBonusPoint: String? = null,
        @SerialName("level_name") val levelName: String? = null,
        @SerialName("levelup_score") val levelUpScore: String? = null,
        @SerialName("all_level_info") val allLevelInfo: List<LevelInfo> = emptyList(),
    )

    @Serializable
    data class LevelInfo(
        val id: String,
        val name: String,
        val score: String,
    )
}
