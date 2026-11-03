package oolong.client.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import oolong.client.services.AutoSign

/**
 * Sets the daily alarm again whenever it may have been lost or put at the wrong time: after the
 * phone starts, the app is updated, the clock or time zone changes, or the app is allowed exact
 * alarms. After a start, a day the phone was off for is caught up.
 */
class BootCompleteSignReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching {
            AutoSign.schedule(context)
            if (intent.action == Intent.ACTION_BOOT_COMPLETED) AutoSign.catchUp(context, foreground = false)
        }
    }
}
