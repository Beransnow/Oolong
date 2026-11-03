package oolong.core.network.search

import okhttp3.RequestBody
import oolong.client.api.models.protos.searchSug.SearchSugResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface SearchSugService {
    @POST("c/s/searchSug?cmd=309438&format=protobuf")
    suspend fun suggestions(@Body body: RequestBody): SearchSugResponse
}

/** The data requests the in-app search page sends, with the headers its web view adds. */
interface SearchWebService {
    @Headers(PRAGMA, CACHE_CONTROL, ACCEPT, ACCEPT_LANGUAGE, REQUESTED_WITH, FETCH_SITE, FETCH_MODE, FETCH_DEST)
    @GET("mo/q/search/forum")
    suspend fun forums(
        @Query("word") keyword: String,
        @Header("Referer") referer: String,
    ): SearchForumsResponse

    @Headers(PRAGMA, CACHE_CONTROL, ACCEPT, ACCEPT_LANGUAGE, REQUESTED_WITH, FETCH_SITE, FETCH_MODE, FETCH_DEST)
    @GET("mo/q/search/thread")
    suspend fun threads(
        @Query("word") keyword: String,
        @Query("pn") page: Int,
        @Query("st") sort: Int,
        @Query("tt") filter: Int,
        @Query("rn") pageSize: Int?,
        @Query("fname") forumName: String?,
        @Query("ct") ct: Int,
        @Query("is_use_zonghe") useZonghe: Int?,
        @Query("cv") clientVersion: String,
        @Header("Referer") referer: String,
    ): SearchThreadsResponse

    @Headers(PRAGMA, CACHE_CONTROL, ACCEPT, ACCEPT_LANGUAGE, REQUESTED_WITH, FETCH_SITE, FETCH_MODE, FETCH_DEST)
    @GET("mo/q/search/user")
    suspend fun users(
        @Query("word") keyword: String,
        @Header("Referer") referer: String,
    ): SearchUsersResponse
}

private const val PRAGMA = "Pragma: no-cache"
private const val CACHE_CONTROL = "Cache-Control: no-cache"
private const val ACCEPT = "Accept: application/json, text/plain, */*"
private const val ACCEPT_LANGUAGE = "Accept-Language: zh-CN,zh;q=0.9"
private const val REQUESTED_WITH = "X-Requested-With: com.baidu.tieba"
private const val FETCH_SITE = "Sec-Fetch-Site: same-origin"
private const val FETCH_MODE = "Sec-Fetch-Mode: cors"
private const val FETCH_DEST = "Sec-Fetch-Dest: empty"
