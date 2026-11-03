package oolong.client.repository

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import oolong.client.R

/**
 * Once the app has replaced itself with an update it installed, which closes it, says so in a
 * notification that opens it again: an app may not open itself from the background.
 */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val version = context.updateRepository.takeUpdated() ?: return@launch
                notify(context, version)
            } finally {
                pending.finish()
            }
        }
    }

    private fun notify(context: Context, version: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(ChannelId, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.title_software_update))
                .setShowBadge(false)
                .build(),
        )
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val notification = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.ic_launcher_new_monochrome)
            .setContentTitle(context.getString(R.string.update_done_title, version))
            .setContentText(context.getString(R.string.update_done_text))
            .setContentIntent(PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(NotificationId, notification)
        } catch (_: SecurityException) {
            // Notifications turned off after the check
        }
    }

    private companion object {
        const val ChannelId = "app_update"
        const val NotificationId = 0x0013
    }
}
