package oolong.client.api.retrofit.exception

import oolong.client.App
import oolong.client.R
import oolong.client.api.Error

fun Throwable.getErrorCode(): Int {
    return if (this is TiebaException) {
        code
    } else {
        Error.ERROR_UNKNOWN
    }
}

fun Throwable.getErrorMessage(): String {
    return if (message.isNullOrEmpty()) {
        App.INSTANCE.getString(R.string.error_unknown)
    } else {
        message!!
    }
}
