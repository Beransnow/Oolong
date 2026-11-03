package oolong.core.network

import oolong.client.api.retrofit.exception.TiebaException

/**
 * What the server asks the user to complete before it accepts a write.
 */
sealed interface VerificationChallenge {
    /** A captcha (error 5 or 6, or `need_vcode`); the answer is sent back with the retried request. */
    data class Captcha(
        val vcodeMd5: String,
        val vcodeType: String,
        val picUrl: String,
        /** `vcode_extra`, as JSON or protobuf depending on the response. */
        val extra: Any?,
    ) : VerificationChallenge

    /** Error 227001: the account has to pass an access check. */
    data class AccountAccess(val accessState: Any?) : VerificationChallenge

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

/** The captcha fields a response carries, from whichever of its objects has them. */
internal class CaptchaFields(
    val vcodeMd5: String,
    val vcodeType: String,
    val picUrl: String,
    val extra: Any?,
)

/**
 * Turns an error code and the verification fields of a write response into the exception callers
 * see: [VerificationRequiredException] when the server asks the user to verify, otherwise
 * [TiebaServerException] for a non-zero code, or `null` when the write went through.
 */
internal fun writeResponseError(
    code: Int,
    message: String,
    needsCaptcha: Boolean,
    captcha: () -> CaptchaFields?,
    accessState: () -> Any?,
    response: Any?,
): Exception? = when {
    needsCaptcha || code in CAPTCHA_CODES -> {
        val fields = captcha()
        VerificationRequiredException(
            code, message,
            VerificationChallenge.Captcha(
                vcodeMd5 = fields?.vcodeMd5.orEmpty(),
                vcodeType = fields?.vcodeType.orEmpty(),
                picUrl = fields?.picUrl.orEmpty(),
                extra = fields?.extra,
            ),
        )
    }

    code == ACCOUNT_ACCESS_CODE ->
        VerificationRequiredException(code, message, VerificationChallenge.AccountAccess(accessState()))

    code in SECURITY_CODES ->
        VerificationRequiredException(code, message, VerificationChallenge.Security(code))

    code != 0 -> TiebaServerException(code, message, response)
    else -> null
}

private val CAPTCHA_CODES = setOf(5, 6)
private const val ACCOUNT_ACCESS_CODE = 227001
private val SECURITY_CODES = setOf(220009, 3250017, 3250020, 3250021, 3250022, 3250023, 3250024)
