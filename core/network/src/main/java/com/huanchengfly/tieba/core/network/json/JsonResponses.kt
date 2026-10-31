package com.huanchengfly.tieba.core.network.json

import com.huanchengfly.tieba.core.network.CaptchaFields
import com.huanchengfly.tieba.core.network.writeResponseError
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
    val info = response["info"] as? JsonObject
    val error = writeResponseError(
        code = code,
        message = response.string("error_msg").orEmpty(),
        needsCaptcha = info?.string("need_vcode") == "1",
        captcha = {
            listOfNotNull(info, antiStat, response["anti"] as? JsonObject)
                .firstOrNull { !it.string("vcode_md5").isNullOrEmpty() }
                ?.let {
                    CaptchaFields(
                        vcodeMd5 = it.string("vcode_md5").orEmpty(),
                        vcodeType = it.string("vcode_type").orEmpty(),
                        picUrl = it.string("vcode_pic_url").orEmpty(),
                        extra = it["vcode_extra"],
                    )
                }
        },
        accessState = { info?.get("access_state") },
        response = response,
    )
    if (error != null) throw error
}

private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.content
