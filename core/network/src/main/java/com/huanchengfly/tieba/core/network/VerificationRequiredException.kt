package com.huanchengfly.tieba.core.network

import com.huanchengfly.tieba.post.api.retrofit.exception.TiebaException
import kotlinx.serialization.json.JsonElement

/**
 * What the server asks the user to complete before it accepts a write.
 */
sealed interface VerificationChallenge {
    /** A captcha (error 5 or 6, or `need_vcode`); the answer is sent back with the retried request. */
    data class Captcha(
        val vcodeMd5: String,
        val vcodeType: String,
        val picUrl: String,
        val extra: JsonElement?,
    ) : VerificationChallenge

    /** Error 227001: the account has to pass an access check. */
    data class AccountAccess(val accessState: JsonElement?) : VerificationChallenge

    /** UEG security checks such as binding a phone or face verification. */
    data class Security(val code: Int) : VerificationChallenge
}

class VerificationRequiredException(
    override val code: Int,
    message: String,
    val challenge: VerificationChallenge,
) : TiebaException(message.ifEmpty { "需要验证" }) {
    override fun toString(): String =
        "VerificationRequiredException(code=$code, message=$message, challenge=$challenge)"
}
