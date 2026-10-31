package com.huanchengfly.tieba.core.network.json

import com.huanchengfly.tieba.core.network.identity.SessionProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/**
 * Decodes JSON responses with kotlinx.serialization after [checkJsonResponse] has handled errors,
 * verification and `tbs` refreshes.
 */
class JsonConverterFactory(
    private val json: Json,
    private val sessionProvider: SessionProvider,
) : Converter.Factory() {
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *> {
        val serializer = json.serializersModule.serializer(type)
        return Converter<ResponseBody, Any?> { body ->
            val element = json.parseToJsonElement(body.use { it.string() })
            if (element is JsonObject) checkJsonResponse(element, sessionProvider)
            json.decodeFromJsonElement(serializer, element)
        }
    }
}
