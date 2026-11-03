package oolong.core.network.testing

import okhttp3.MultipartReader
import okio.Buffer
import okio.ByteString
import kotlin.test.assertEquals

/** The parts of a multipart body by name, asserting none declares its length. */
fun multipartParts(contentType: String, body: ByteString): List<Pair<String, ByteString>> {
    val boundary = contentType.substringAfter("boundary=")
    val parts = mutableListOf<Pair<String, ByteString>>()
    MultipartReader(Buffer().write(body), boundary).use { reader ->
        while (true) {
            val part = reader.nextPart() ?: break
            assertEquals(null, part.headers["Content-Length"])
            val name = Regex("name=\"([^\"]*)\"")
                .find(part.headers["Content-Disposition"]!!)!!.groupValues[1]
            parts += name to part.body.readByteString()
        }
    }
    return parts
}
