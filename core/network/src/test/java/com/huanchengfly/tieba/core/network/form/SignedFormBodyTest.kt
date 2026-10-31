package com.huanchengfly.tieba.core.network.form

import kotlin.test.Test
import kotlin.test.assertEquals

class SignedFormBodyTest {
    private val params = listOf("timestamp" to "1", "tbs" to "abc", "kw" to "吧名", "fid" to "52")

    @Test
    fun `sorts the signatures together with the parameters`() {
        val body = signedFormBody(params, withSig = true)

        assertEquals(listOf("fid", "kw", "sig", "sign", "tbs", "timestamp"), (0 until body.size).map { body.name(it) })
        assertEquals(FormSignature.sig(params), body.value(2))
        assertEquals(FormSignature.sign(params), body.value(3))
    }

    @Test
    fun `leaves out sig when not asked for`() {
        val body = signedFormBody(params, withSig = false)

        assertEquals(listOf("fid", "kw", "sign", "tbs", "timestamp"), (0 until body.size).map { body.name(it) })
    }
}
