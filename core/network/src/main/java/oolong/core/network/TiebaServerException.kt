package oolong.core.network

import oolong.client.api.retrofit.exception.TiebaException

/**
 * The server answered with a non-zero error code.
 *
 * @property response the decoded response, which can still carry data the caller needs, such as
 * verification challenges or a new `tbs`
 */
class TiebaServerException(
    override val code: Int,
    message: String,
    val response: Any?,
) : TiebaException(message.ifEmpty { "未知错误" }) {
    override fun toString(): String = "TiebaServerException(code=$code, message=$message)"
}
