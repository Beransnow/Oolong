package com.huanchengfly.tieba.core.network.protobuf

import com.huanchengfly.tieba.core.network.form.MultipartFormBody
import com.squareup.wire.Message
import okhttp3.RequestBody

/**
 * The multipart body of a protobuf request: optional form fields, then the encoded message as a
 * file named `data`.
 */
fun ProtobufBody(
    message: Message<*, *>,
    fields: List<Pair<String, String>> = emptyList(),
): RequestBody = MultipartFormBody(fields, fileField = "data", file = message.encodeByteString())
