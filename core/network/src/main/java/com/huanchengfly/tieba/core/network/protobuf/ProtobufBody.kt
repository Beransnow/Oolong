package com.huanchengfly.tieba.core.network.protobuf

import com.squareup.wire.Message
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink

/**
 * The multipart body of a protobuf request: optional form fields, then the encoded message as a
 * file named `data`.
 *
 * Parts carry no `Content-Length` and the body is sent chunked, as the official client does, so
 * this doesn't use OkHttp's `MultipartBody`.
 */
class ProtobufBody(
    private val message: Message<*, *>,
    private val fields: List<Pair<String, String>> = emptyList(),
) : RequestBody() {
    override fun contentType(): MediaType = CONTENT_TYPE

    override fun contentLength(): Long = -1

    override fun writeTo(sink: BufferedSink) {
        fields.forEach { (name, value) ->
            sink.writePartHeader("form-data; name=\"$name\"")
            sink.writeUtf8(value).writeUtf8(CRLF)
        }
        sink.writePartHeader("form-data; name=\"data\"; filename=\"file\"")
        sink.write(message.encode()).writeUtf8(CRLF)
        sink.writeUtf8("--").writeUtf8(BOUNDARY).writeUtf8("--").writeUtf8(CRLF)
    }

    private fun BufferedSink.writePartHeader(disposition: String) {
        writeUtf8("--").writeUtf8(BOUNDARY).writeUtf8(CRLF)
        writeUtf8("Content-Disposition: ").writeUtf8(disposition).writeUtf8(CRLF)
        writeUtf8(CRLF)
    }

    companion object {
        const val BOUNDARY = "--------7da3d81520810*"
        private const val CRLF = "\r\n"
        private val CONTENT_TYPE = "multipart/form-data; boundary=$BOUNDARY".toMediaType()
    }
}
