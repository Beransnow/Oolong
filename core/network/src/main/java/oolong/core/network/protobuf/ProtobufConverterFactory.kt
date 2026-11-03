package oolong.core.network.protobuf

import com.squareup.wire.FieldEncoding
import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.ProtoReader
import okhttp3.ResponseBody
import okio.Buffer
import okio.ByteString
import oolong.client.api.models.protos.Error
import oolong.core.network.TiebaServerException
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/**
 * Decodes protobuf responses and throws [TiebaServerException] when their `error` field
 * (field 1 in every response) carries a non-zero code.
 */
object ProtobufConverterFactory : Converter.Factory() {
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *>? {
        if (type !is Class<*> || !Message::class.java.isAssignableFrom(type)) return null
        @Suppress("UNCHECKED_CAST")
        val adapter = ProtoAdapter.get(type as Class<Message<*, *>>)
        return Converter<ResponseBody, Any> { body ->
            val bytes = body.use { it.byteString() }
            val response = adapter.decode(bytes)
            val error = readError(bytes)
            if (error != null && error.error_code != 0) {
                throw TiebaServerException(error.error_code, error.error_msg, response)
            }
            response
        }
    }

    private fun readError(bytes: ByteString): Error? {
        val reader = ProtoReader(Buffer().write(bytes))
        val token = reader.beginMessage()
        var error: Error? = null
        while (true) {
            val tag = reader.nextTag()
            if (tag == -1) break
            if (tag == ERROR_TAG && reader.peekFieldEncoding() == FieldEncoding.LENGTH_DELIMITED) {
                error = Error.ADAPTER.decode(reader)
            } else {
                reader.skip()
            }
        }
        reader.endMessageAndGetUnknownFields(token)
        return error
    }

    private const val ERROR_TAG = 1
}
