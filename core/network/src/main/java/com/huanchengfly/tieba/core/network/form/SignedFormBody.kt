package com.huanchengfly.tieba.core.network.form

import okhttp3.FormBody

/**
 * A form body of [params] with `sign`, and `sig` when [withSig], all sorted by name: the official
 * client keeps form parameters sorted as it adds them, signatures included.
 */
fun signedFormBody(params: List<Pair<String, String>>, withSig: Boolean): FormBody =
    FormBody.Builder().apply {
        signedParams(params, withSig).forEach { (name, value) -> add(name, value) }
    }.build()

/** [params] with `sign`, and `sig` when [withSig], all sorted by name. */
fun signedParams(params: List<Pair<String, String>>, withSig: Boolean): List<Pair<String, String>> {
    val signatures = listOfNotNull(
        "sign" to FormSignature.sign(params),
        ("sig" to FormSignature.sig(params)).takeIf { withSig },
    )
    return (params + signatures).sortedBy { it.first }
}
