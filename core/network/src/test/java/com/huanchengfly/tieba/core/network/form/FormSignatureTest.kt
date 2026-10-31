package com.huanchengfly.tieba.core.network.form

import kotlin.test.Test
import kotlin.test.assertEquals

class FormSignatureTest {
    // Expected values computed independently with Python's hashlib
    private val params = listOf(
        "tbs" to "abc",
        "kw" to "吧名",
        "_client_version" to "12.52.1.0",
        "fid" to "52",
    )

    @Test
    fun `signs the sorted raw parameters`() {
        assertEquals("447117CA299FE693603283D902E1E9F5", FormSignature.sign(params))
    }

    @Test
    fun `computes sig from the same string with the prefix`() {
        assertEquals("49bec97b1db7ead91a1290b990372c3b", FormSignature.sig(params))
    }

    @Test
    fun `does not depend on the parameter order`() {
        assertEquals(FormSignature.sign(params), FormSignature.sign(params.reversed()))
    }
}
