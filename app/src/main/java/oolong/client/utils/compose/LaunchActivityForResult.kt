package oolong.client.utils.compose

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import kotlinx.coroutines.GlobalScope
import oolong.client.arch.GlobalEvent
import oolong.client.arch.emitGlobalEvent

data class LaunchActivityRequest(
    val requesterId: String,
    val intent: Intent,
)

data class ActivityResult(
    val requesterId: String,
    val resultCode: Int,
    val intent: Intent?,
)

fun launchActivityForResult(
    requesterId: String,
    intent: Intent,
) {
    GlobalScope.emitGlobalEvent(GlobalEvent.StartActivityForResult(requesterId, intent))
}

class LaunchActivityForResult : ActivityResultContract<LaunchActivityRequest, ActivityResult>() {
    private var currentRequesterId: String? = null

    override fun createIntent(context: Context, input: LaunchActivityRequest): Intent {
        currentRequesterId = input.requesterId
        return input.intent
    }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): ActivityResult {
        return ActivityResult(
            requesterId = currentRequesterId ?: "",
            resultCode = resultCode,
            intent = intent
        )
    }
}
