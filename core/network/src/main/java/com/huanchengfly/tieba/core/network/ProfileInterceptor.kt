package com.huanchengfly.tieba.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the common headers of [profile] to every request, keeping any header the request already
 * sets itself.
 */
class ProfileInterceptor(
    private val profile: TiebaProfile,
    private val commonParams: CommonParams,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder()
        commonParams.headers(profile).forEach { (name, value) ->
            if (request.header(name) == null) builder.addHeader(name, value)
        }
        return chain.proceed(builder.build())
    }
}
