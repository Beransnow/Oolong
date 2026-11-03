package oolong.client.utils

/** Base32 with the RFC 4648 alphabet and no padding: five bits a character, the last one filled with zeros. */
object Base32 {
    private const val Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encode(bytes: ByteArray): String = buildString((bytes.size * 8 + 4) / 5) {
        var buffer = 0
        var bits = 0
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                append(Alphabet[(buffer ushr bits) and 31])
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        if (bits > 0) append(Alphabet[(buffer shl (5 - bits)) and 31])
    }
}
