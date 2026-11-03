package oolong.client.api.retrofit.exception

import oolong.client.api.Error.ERROR_NOT_LOGGED_IN

class TiebaNotLoggedInException(
    msg: String = "Not logged in!"
) : TiebaLocalException(ERROR_NOT_LOGGED_IN, msg) {
    override fun toString(): String {
        return "TiebaNotLoggedInException(message=$message)"
    }
}
