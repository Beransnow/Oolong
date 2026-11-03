package oolong.core.network.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import okhttp3.ResponseBody
import oolong.core.network.identity.SessionProvider
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/**
 * The server encodes empty objects as `[]`. Treat every empty array as absent, so it falls back to
 * the field's default (`null` or an empty list) instead of failing to decode.
 */
internal fun JsonElement.withoutEmptyArrays(): JsonElement = when (this) {
    is JsonArray -> if (isEmpty()) JsonNull else JsonArray(map { it.withoutEmptyArrays() })
    is JsonObject -> JsonObject(mapValues { (_, value) -> value.withoutEmptyArrays() })
    else -> this
}

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
            json.decodeFromJsonElement(serializer, element.withoutEmptyArrays())
        }
    }
}
