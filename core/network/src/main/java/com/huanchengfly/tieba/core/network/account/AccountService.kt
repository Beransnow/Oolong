package com.huanchengfly.tieba.core.network.account

import com.huanchengfly.tieba.post.api.models.protos.getUserInfo.GetUserInfoResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.POST

interface AccountFormService {
    @POST("c/s/login")
    suspend fun login(@Body body: RequestBody): LoginResponse

    @POST("c/s/initNickname")
    suspend fun initNickname(@Body body: RequestBody): JsonObject

    @POST("c/s/sync")
    suspend fun sync(@Body body: RequestBody): SyncResponse
}

interface AccountService {
    @POST("c/u/user/getuserinfo?cmd=303024&format=protobuf")
    suspend fun userInfo(@Body body: RequestBody): GetUserInfoResponse
}

/** The account a BDUSS belongs to, and its `tbs`. */
@Serializable
data class LoginResponse(
    val user: User = User(),
    val anti: Anti = Anti(),
) {
    @Serializable
    data class User(val id: String = "", val name: String = "", val portrait: String = "")

    @Serializable
    data class Anti(val tbs: String = "")
}

/** The ids the server assigns to this installation. */
@Serializable
data class SyncResponse(
    val client: Client = Client(),
    @SerialName("wl_config") val wlConfig: WlConfig = WlConfig(),
) {
    @Serializable
    data class Client(@SerialName("client_id") val clientId: String = "")

    @Serializable
    data class WlConfig(@SerialName("sample_id") val sampleId: String = "")
}
