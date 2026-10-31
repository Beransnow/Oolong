package com.huanchengfly.tieba.post.arch

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import com.huanchengfly.tieba.post.api.retrofit.exception.getErrorMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Shows short messages to the user, from code that has no context, such as a ViewModel. */
interface MessageController {
    fun show(@StringRes message: Int, vararg args: Any)

    /** Shows [message] with the error's own message as its argument. */
    fun showError(@StringRes message: Int, error: Throwable) = show(message, error.getErrorMessage())
}

@Singleton
class ToastMessageController @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) : MessageController {
    override fun show(message: Int, vararg args: Any) {
        scope.launch {
            Toast.makeText(context, context.getString(message, *args), Toast.LENGTH_SHORT).show()
        }
    }
}
