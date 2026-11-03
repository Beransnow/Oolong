package oolong.client.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import oolong.client.utils.TiebaUtil

class AutoSignAlarm : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching {
            TiebaUtil.startSign(context)
        }
    }

    companion object {
        val TAG: String = "AutoSignAlarm"
    }
}
