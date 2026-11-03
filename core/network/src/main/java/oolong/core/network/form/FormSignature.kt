package oolong.core.network.form

import java.security.MessageDigest

/**
 * Signatures of form requests. Both cover every parameter, sorted by name, as `name=value` pairs
 * of raw (not URL-encoded) values.
 */
object FormSignature {
    private const val SIGN_SECRET = "tiebaclient!!!"
    private const val SIG_PREFIX = "xYQnTnvXWuWqZxyc"

    /** `sign`: upper-case MD5 of the sorted parameters followed by the secret. */
    fun sign(params: List<Pair<String, String>>): String = md5(signString(params)).uppercase()

    /** `sig`: lower-case MD5 of the same string as [sign], with a fixed prefix. */
    fun sig(params: List<Pair<String, String>>): String = md5(SIG_PREFIX + signString(params))

    private fun signString(params: List<Pair<String, String>>): String =
        params.sortedBy { it.first }.joinToString("") { (name, value) -> "$name=$value" } + SIGN_SECRET

    private fun md5(text: String): String =
        MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
