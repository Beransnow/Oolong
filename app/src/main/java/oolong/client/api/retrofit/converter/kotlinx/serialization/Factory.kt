@file:OptIn(ExperimentalSerializationApi::class)

package oolong.client.api.retrofit.converter.kotlinx.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.StringFormat
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

internal class Factory(
    private val serializer: Serializer,
) : Converter.Factory() {
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *>? {
        // Types it can't read are left to the converters after it
        val loader = runCatching { serializer.serializer(type) }.getOrNull() ?: return null
        return Converter { body: ResponseBody -> serializer.fromResponseBody(loader, body) }
    }
}

@JvmName("create")
fun StringFormat.asConverterFactory(): Converter.Factory {
    return Factory(Serializer.FromString(this))
}
