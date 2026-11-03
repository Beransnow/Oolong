package oolong.client.services

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.pendingIntentFlagImmutable
import oolong.client.repository.AccountRepository
import oolong.client.ui.common.theme.compose.currentColors
import oolong.core.network.message.MessageRemoteDataSource
import javax.inject.Inject

@AndroidEntryPoint
class NotifyJobService : JobService() {
    @Inject
    lateinit var messages: MessageRemoteDataSource

    @Inject
    lateinit var accounts: AccountRepository

    private val scope = MainScope()
    var notificationManager: NotificationManager? = null
    private fun createChannel(id: String, name: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                id,
                name, NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.group = CHANNEL_GROUP
            channel.setShowBadge(true)
            notificationManager!!.createNotificationChannel(channel)
        }
    }

    override fun onStartJob(params: JobParameters): Boolean {
        Log.i(TAG, "onStartJob")
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channelGroup = NotificationChannelGroup(CHANNEL_GROUP, getString(R.string.channel_group_messages))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    channelGroup.description = getString(R.string.channel_group_messages_description)
                }
                notificationManager!!.createNotificationChannelGroup(channelGroup)
                createChannel(CHANNEL_REPLY, getString(R.string.title_reply_me))
                createChannel(CHANNEL_AT, getString(R.string.title_at_me))
            }
        }
        // Logged out, the server answers with no counts at all
        if (accounts.current.value == null) return false
        scope.launch {
            val message = try {
                messages.reminder().message
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                jobFinished(params, true)
                return@launch
            }
            if (message == null) {
                jobFinished(params, false)
                return@launch
            }
            if (notificationManager != null) {
                var total = 0
                val replyCount = message.replyMe?.toIntOrNull() ?: 0
                if (replyCount > 0) {
                    total += replyCount
                    sendBroadcast(
                        Intent()
                            .setAction(ACTION_NEW_MESSAGE)
                            .putExtra("channel", CHANNEL_REPLY)
                            .putExtra("count", replyCount)
                    )
                    updateNotification(
                        getString(
                            R.string.tips_message_reply,
                            replyCount.toString()
                        ),
                        ID_REPLY,
                        CHANNEL_REPLY,
                        getString(R.string.title_reply_me),
                        Intent(ACTION_VIEW, Uri.parse("oolong://notifications/0")).setPackage(packageName)
                    )
                }
                val atCount = message.atMe?.toIntOrNull() ?: 0
                if (atCount > 0) {
                    total += atCount
                    sendBroadcast(
                        Intent()
                            .setAction(ACTION_NEW_MESSAGE)
                            .putExtra("channel", CHANNEL_AT)
                            .putExtra("count", atCount)
                    )
                    updateNotification(
                        getString(
                            R.string.tips_message_at,
                            atCount.toString()
                        ),
                        ID_AT,
                        CHANNEL_AT,
                        getString(R.string.title_at_me),
                        Intent(ACTION_VIEW, Uri.parse("oolong://notifications/1")).setPackage(packageName)
                    )
                }
                sendBroadcast(
                    Intent()
                        .setAction(ACTION_NEW_MESSAGE)
                        .putExtra("channel", CHANNEL_TOTAL)
                        .putExtra("count", total)
                )
            }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun updateNotification(
        text: String,
        id: Int,
        channel: String,
        channelName: String,
        intent: Intent
    ) {
        val notification = NotificationCompat.Builder(this, channel)
            .setSubText(channelName)
            .setContentText(getString(R.string.tip_touch_to_view))
            .setContentTitle(text)
            .setSmallIcon(R.drawable.ic_round_drafts)
            .setWhen(System.currentTimeMillis())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    pendingIntentFlagImmutable()
                )
            )
            .setColor(currentColors(this).accent.toArgb())
            .build()
        notificationManager!!.notify(id, notification)
    }

    companion object {
        val TAG = NotifyJobService::class.java.simpleName
        const val ACTION_NEW_MESSAGE = "oolong.client.action.NEW_MESSAGE"
        const val CHANNEL_GROUP = "20"
        const val CHANNEL_AT = "3"
        const val CHANNEL_TOTAL = "total"
        const val ID_REPLY = 20
        const val ID_AT = 21
        private const val CHANNEL_REPLY = "2"
    }
}
