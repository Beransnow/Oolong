package oolong.client.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import oolong.client.services.AutoSign

/**
 * The daily alarm, or one set to catch a missed day up: starts the signing, which the alarm lets
 * run in the foreground, and sets the next day's alarm.
 */
class AutoSignAlarm : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { AutoSign.startFromAlarm(context) }
        if (!intent.getBooleanExtra(EXTRA_CATCH_UP, false)) runCatching { AutoSign.schedule(context) }
    }

    companion object {
        const val EXTRA_CATCH_UP = "catch_up"
    }
}
