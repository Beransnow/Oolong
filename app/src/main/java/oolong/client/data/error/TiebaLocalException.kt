package oolong.client.data.error

import oolong.core.network.TiebaException

open class TiebaLocalException(
    override val code: Int,
    msg: String
) : TiebaException(msg) {
    override fun toString(): String {
        return "TiebaLocalException(code=$code, message=$message)"
    }
}
