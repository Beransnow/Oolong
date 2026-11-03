package oolong.client.api.retrofit.interfaces

import kotlinx.coroutines.flow.Flow
import okhttp3.RequestBody
import oolong.client.api.models.SofireResponse
import retrofit2.http.Body
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

interface SofireApi {
    //    @FormUrlEncoded
    @POST
    fun post(
        @Url url: String,
        @Query("skey") skey: String,
        @Body body: RequestBody,
        @HeaderMap headers: Map<String, String>,
    ): Flow<SofireResponse>
}
