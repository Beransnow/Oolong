package oolong.client.api.retrofit.exception

import oolong.client.api.Error.ERROR_NETWORK

class NoConnectivityException(
    msg: String = "No internet!"
) : TiebaLocalException(ERROR_NETWORK, msg) {
    override fun toString(): String {
        return "NoConnectivityException(message=$message)"
    }
}
