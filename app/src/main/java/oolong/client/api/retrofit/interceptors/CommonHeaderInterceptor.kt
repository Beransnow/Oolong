package oolong.client.api.retrofit.interceptors

import okhttp3.Interceptor
import okhttp3.Response
import oolong.client.api.ParamExpression
import oolong.client.api.forEachNonNull

class CommonHeaderInterceptor(private vararg val additionHeaders: ParamExpression) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val headers = request.headers

        return chain.proceed(request.newBuilder().apply {
            additionHeaders.forEachNonNull { name, value ->
                if (headers[name] == null) addHeader(name, value)
            }
        }.build())
    }
}
