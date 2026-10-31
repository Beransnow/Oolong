package com.huanchengfly.tieba.core.network.form

import com.huanchengfly.tieba.core.network.CommonParams
import com.huanchengfly.tieba.core.network.TiebaProfile
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Turns a POST form of business parameters into a complete form request of [profile]: adds the
 * common parameters (the request's own values win), then signs the result.
 *
 * Requests control this with headers, which are removed before sending:
 * - [OMIT_COMMON_PARAMS]: comma-separated common parameters to leave out
 * - [WITH_SIG]: also send `sig`, for endpoints the official client marks as needing it
 */
class FormParamsInterceptor(
    private val profile: TiebaProfile,
    private val commonParams: CommonParams,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val body = request.body
        if (request.method != "POST" || (body != null && body !is FormBody && body.contentLength() != 0L)) {
            return chain.proceed(request)
        }

        val business = (body as? FormBody)?.let { form ->
            (0 until form.size).map { form.name(it) to form.value(it) }
        }.orEmpty().filter { (name, _) -> name != "sign" && name != "sig" }
        val omitted = request.headers(OMIT_COMMON_PARAMS)
            .flatMap { it.split(',') }.map { it.trim() }.toSet()
        val businessNames = business.map { it.first }.toSet()
        val common = commonParams.form(profile)
            .filter { (name, _) -> name !in businessNames && name !in omitted }

        val signed = signedFormBody(common + business, withSig = request.header(WITH_SIG) != null)
        return chain.proceed(
            request.newBuilder()
                .removeHeader(OMIT_COMMON_PARAMS)
                .removeHeader(WITH_SIG)
                .post(signed)
                .build()
        )
    }

    companion object {
        const val OMIT_COMMON_PARAMS = "no_common_params"
        const val WITH_SIG = "X-Tieba-Sig"
    }
}
