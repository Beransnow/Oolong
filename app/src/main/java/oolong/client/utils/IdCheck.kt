package oolong.client.utils

import java.util.BitSet
import java.util.zip.CRC32

/**
 * The 5-byte check written after an id, as in a CUID: 40 bits, all set at first, into which four
 * 32-bit hashes are mixed in turn. Each hash is taken over the id followed by the checks so far,
 * and is mixed into 32 of the 40 bits, starting at its own bit.
 */
object IdCheck {
    private enum class Mix { And, Xor }

    private class Step(val hash: (ByteArray, Int) -> Int, val from: Int, val mix: Mix)

    private val steps = listOf(
        Step(::crc32, from = 8, mix = Mix.And),
        Step(::xxHash32, from = 0, mix = Mix.Xor),
        Step(::xxHash32, from = 1, mix = Mix.Xor),
        Step(::crc32, from = 7, mix = Mix.Xor),
    )

    private const val Bits = 40
    private const val Bytes = Bits / 8

    fun of(id: ByteArray): ByteArray {
        val check = BitSet(Bits).apply { set(0, Bits) }
        // The id, then each check as it stands, one after another
        val input = id.copyOf(id.size + (steps.size + 1) * Bytes)
        check.toByteArray().copyInto(input, id.size)
        steps.forEachIndexed { i, step ->
            val length = id.size + (i + 1) * Bytes
            val hash = BitSet.valueOf(longArrayOf(step.hash(input, length).toLong() and 0xFFFFFFFFL))
            val part = check.get(step.from, step.from + 32)
            when (step.mix) {
                Mix.And -> part.and(hash)
                Mix.Xor -> part.xor(hash)
            }
            for (bit in 0 until 32) check.set(step.from + bit, part.get(bit))
            check.toByteArray().copyInto(input, length)
        }
        return check.toByteArray().copyOf(Bytes)
    }

    private fun crc32(bytes: ByteArray, length: Int): Int = CRC32().apply { update(bytes, 0, length) }.value.toInt()

    /**
     * xxHash32 with seed 0, except that exactly 16 bytes take the path for short input, after their
     * one stripe has gone through the third lane.
     */
    private fun xxHash32(bytes: ByteArray, length: Int): Int {
        var v1 = Prime1 + Prime2
        var v2 = Prime2
        var v3 = 0
        var v4 = -Prime1
        var p = 0
        while (p + 16 <= length) {
            v1 = round(v1, intAt(bytes, p))
            v2 = round(v2, intAt(bytes, p + 4))
            v3 = round(v3, intAt(bytes, p + 8))
            v4 = round(v4, intAt(bytes, p + 12))
            p += 16
        }
        var h = if (length > 16) {
            Integer.rotateLeft(v1, 1) + Integer.rotateLeft(v2, 7) + Integer.rotateLeft(v3, 12) + Integer.rotateLeft(v4, 18)
        } else {
            v3 + Prime5
        }
        h += length
        while (p + 4 <= length) {
            h = Integer.rotateLeft(h + intAt(bytes, p) * Prime3, 17) * Prime4
            p += 4
        }
        while (p < length) {
            h = Integer.rotateLeft(h + (bytes[p].toInt() and 0xFF) * Prime5, 11) * Prime1
            p++
        }
        h = (h xor (h ushr 15)) * Prime2
        h = (h xor (h ushr 13)) * Prime3
        return h xor (h ushr 16)
    }

    private fun round(acc: Int, lane: Int): Int = Integer.rotateLeft(acc + lane * Prime2, 13) * Prime1

    private fun intAt(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or
            ((bytes[at + 1].toInt() and 0xFF) shl 8) or
            ((bytes[at + 2].toInt() and 0xFF) shl 16) or
            ((bytes[at + 3].toInt() and 0xFF) shl 24)

    private const val Prime1 = 0x9E3779B1L.toInt()
    private const val Prime2 = 0x85EBCA77L.toInt()
    private const val Prime3 = 0xC2B2AE3DL.toInt()
    private const val Prime4 = 0x27D4EB2F
    private const val Prime5 = 0x165667B1
}
