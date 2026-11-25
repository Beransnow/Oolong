package oolong.client.data.error

import oolong.client.api.retrofit.exception.TiebaException

open class TiebaLocalException(
    override val code: Int,
    msg: String
) : TiebaException(msg) {
    override fun toString(): String {
        return "TiebaLocalException(code=$code, message=$message)"
    }
}
