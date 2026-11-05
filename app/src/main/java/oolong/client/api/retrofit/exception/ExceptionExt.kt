package oolong.client.api.retrofit.exception

import android.content.Context
import oolong.client.App
import oolong.client.R
import oolong.client.api.Error
import java.io.IOException

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

/** The error's message as the user should read it: a network error says so instead of its technical message. */
fun Throwable.userMessage(context: Context): String =
    if (isNetworkError()) context.getString(R.string.error_network) else getErrorMessage()

/**
 * Whether the request got no answer: no network, the host out of reach, or the connection dropped
 * or timed out. The app's own errors are IO exceptions too, but they carry an answer, so only
 * [NoConnectivityException] among them counts.
 */
fun Throwable.isNetworkError(): Boolean = generateSequence(this) { it.cause.takeIf { cause -> cause !== it } }
    .firstOrNull { it is IOException }
    ?.let { it is NoConnectivityException || it !is TiebaException } == true
