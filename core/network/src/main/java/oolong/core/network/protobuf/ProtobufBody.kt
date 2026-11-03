package oolong.core.network.protobuf

import com.squareup.wire.Message
import okhttp3.RequestBody
import oolong.core.network.form.MultipartFormBody

/**
 * The multipart body of a protobuf request: optional form fields, then the encoded message as a
 * file named `data`.
 */
@Suppress("ktlint:standard:function-naming")
fun ProtobufBody(
    message: Message<*, *>,
    fields: List<Pair<String, String>> = emptyList(),
): RequestBody = MultipartFormBody(fields, fileField = "data", file = message.encodeByteString())
