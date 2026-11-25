package oolong.client.data.error

import oolong.client.data.error.Error.ERROR_NETWORK

class NoConnectivityException(
    msg: String = "No internet!"
) : TiebaLocalException(ERROR_NETWORK, msg) {
    override fun toString(): String {
        return "NoConnectivityException(message=$message)"
    }
}
