package com.huanchengfly.tieba.core.network.json

import com.huanchengfly.tieba.core.network.TiebaServerException
import com.huanchengfly.tieba.core.network.VerificationChallenge
import com.huanchengfly.tieba.core.network.VerificationRequiredException
import com.huanchengfly.tieba.core.network.identity.SessionProvider
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Checks a JSON response of the form stack: stores a refreshed `anti_stat.tbs`, then throws when
 * the response carries an error or asks for verification.
 *
 * `error_code` is usually a string, sometimes a number. `info` differs between endpoints (sign-in,
 * likes and posts each have their own), so only the verification fields are read from it.
 */
internal fun checkJsonResponse(response: JsonObject, sessionProvider: SessionProvider) {
    val antiStat = response["anti_stat"] as? JsonObject
    antiStat?.string("tbs")?.takeIf { it.isNotEmpty() }?.let(sessionProvider::updateTbs)

    val code = response.string("error_code")?.toIntOrNull() ?: 0
    val message = response.string("error_msg").orEmpty()
    val info = response["info"] as? JsonObject
    val needsCaptcha = code in CAPTCHA_CODES || info?.string("need_vcode") == "1"
    when {
        needsCaptcha -> {
            val source = listOfNotNull(info, antiStat, response["anti"] as? JsonObject)
                .firstOrNull { !it.string("vcode_md5").isNullOrEmpty() }
            throw VerificationRequiredException(
                code, message,
                VerificationChallenge.Captcha(
                    vcodeMd5 = source?.string("vcode_md5").orEmpty(),
                    vcodeType = source?.string("vcode_type").orEmpty(),
                    picUrl = source?.string("vcode_pic_url").orEmpty(),
                    extra = source?.get("vcode_extra"),
                ),
            )
        }

        code == ACCOUNT_ACCESS_CODE -> throw VerificationRequiredException(
            code, message, VerificationChallenge.AccountAccess(info?.get("access_state"))
        )

        code in SECURITY_CODES -> throw VerificationRequiredException(
            code, message, VerificationChallenge.Security(code)
        )

        code != 0 -> throw TiebaServerException(code, message, response)
    }
}

private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.content

private val CAPTCHA_CODES = setOf(5, 6)
private const val ACCOUNT_ACCESS_CODE = 227001
private val SECURITY_CODES = setOf(220009, 3250017, 3250020, 3250021, 3250022, 3250023, 3250024)
