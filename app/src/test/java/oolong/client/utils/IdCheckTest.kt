package oolong.client.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class IdCheckTest {
    private fun check(id: String) = Base32.encode(IdCheck.of(id.toByteArray()))

    @Test
    fun `checks ids as the server expects`() {
        assertEquals("VO3W2ZKR", check("A"))
        assertEquals("YLXDI4J7", check("abcdef"))
        // Hashed lengths of exactly 16 bytes come up here
        assertEquals("MTBMHGJ4", check("0123456789a"))
        assertEquals("CZIYPJ67", check("0123456789abcdef"))
        assertEquals("2PTUN6NV", check("91B1E1F2C3D4A5B6C7D8E9F0A1B2C3D4|0"))
        assertEquals("ZG35VVXY", check("9A8B7C6D5E4F3A2B1C0D9E8F7A6B5C4D|VQ3RS4TUV5W"))
    }

    @Test
    fun `encodes Base32 without padding`() {
        assertEquals("", Base32.encode(byteArrayOf()))
        assertEquals("MY", Base32.encode("f".toByteArray()))
        assertEquals("MZXQ", Base32.encode("fo".toByteArray()))
        assertEquals("MZXW6", Base32.encode("foo".toByteArray()))
        assertEquals("MZXW6YQ", Base32.encode("foob".toByteArray()))
        assertEquals("MZXW6YTB", Base32.encode("fooba".toByteArray()))
        assertEquals("MZXW6YTBOI", Base32.encode("foobar".toByteArray()))
    }
}
