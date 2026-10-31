package com.huanchengfly.tieba.core.network

import com.huanchengfly.tieba.core.network.identity.SessionProvider
import com.huanchengfly.tieba.core.network.json.JsonConverterFactory
import com.huanchengfly.tieba.core.network.protobuf.ProtobufConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.create
import java.util.concurrent.TimeUnit

/**
 * Creates the Retrofit services of each [TiebaProfile]. All profiles share one connection pool
 * and differ only in the [ProfileInterceptor] they add.
 *
 * @param networkInterceptors added to every client after the profile headers, such as a request
 * logger in debug builds
 * @param baseUrlOverride replaces every profile's base URL, for tests
 */
class TiebaNetwork(
    private val commonParams: CommonParams,
    private val sessionProvider: SessionProvider,
    private val networkInterceptors: List<Interceptor> = emptyList(),
    private val baseUrlOverride: HttpUrl? = null,
) {
    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val retrofits = mutableMapOf<TiebaProfile, Retrofit>()

    @PublishedApi
    @Synchronized
    internal fun retrofit(profile: TiebaProfile): Retrofit = retrofits.getOrPut(profile) {
        val client = baseClient.newBuilder()
            .addInterceptor(ProfileInterceptor(profile, commonParams))
            .apply { networkInterceptors.forEach { addNetworkInterceptor(it) } }
            .build()
        Retrofit.Builder()
            .baseUrl(baseUrlOverride ?: profile.baseUrl.toHttpUrl())
            .client(client)
            .addConverterFactory(
                when (profile.protocol) {
                    TiebaProtocol.PROTOBUF -> ProtobufConverterFactory
                    TiebaProtocol.FORM -> JsonConverterFactory(json, sessionProvider)
                }
            )
            .build()
    }

    inline fun <reified T : Any> service(profile: TiebaProfile): T = retrofit(profile).create<T>()

    private companion object {
        const val TIMEOUT_SECONDS = 60L
    }
}
