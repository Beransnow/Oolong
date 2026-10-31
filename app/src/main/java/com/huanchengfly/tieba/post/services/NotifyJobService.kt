package com.huanchengfly.tieba.post.services

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
import androidx.core.app.NotificationCompat
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.api.legacyMessageRemoteDataSource
import com.huanchengfly.tieba.post.pendingIntentFlagImmutable
import com.huanchengfly.tieba.post.ui.common.theme.utils.ThemeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NotifyJobService : JobService() {
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
                val channelGroup = NotificationChannelGroup(CHANNEL_GROUP, CHANNEL_GROUP_NAME)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    channelGroup.description = "贴吧的各种消息通知"
                }
                notificationManager!!.createNotificationChannelGroup(channelGroup)
                createChannel(CHANNEL_REPLY, CHANNEL_REPLY_NAME)
                createChannel(CHANNEL_AT, CHANNEL_AT_NAME)
            }
        }
        scope.launch {
            val message = try {
                legacyMessageRemoteDataSource.reminder().message
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
                if ("0" != message.replyMe) {
                    val replyCount = message.replyMe?.let { Integer.valueOf(it) }
                    if (replyCount != null) {
                        total += replyCount
                    }
                    sendBroadcast(
                        Intent()
                            .setAction(ACTION_NEW_MESSAGE)
                            .putExtra("channel", CHANNEL_REPLY)
                            .putExtra("count", replyCount)
                    )
                    updateNotification(
                        getString(
                            R.string.tips_message_reply,
                            message.replyMe
                        ),
                        ID_REPLY,
                        CHANNEL_REPLY,
                        CHANNEL_REPLY_NAME,
                        Intent(ACTION_VIEW, Uri.parse("tblite://notifications/0")).setPackage(packageName)
                    )
                }
                if ("0" != message.atMe) {
                    val atCount = message.atMe?.let { Integer.valueOf(it) }
                    if (atCount != null) {
                        total += atCount
                    }
                    sendBroadcast(
                        Intent()
                            .setAction(ACTION_NEW_MESSAGE)
                            .putExtra("channel", CHANNEL_AT)
                            .putExtra("count", message.atMe)
                    )
                    updateNotification(
                        getString(
                            R.string.tips_message_at,
                            message.atMe
                        ),
                        ID_AT,
                        CHANNEL_AT,
                        CHANNEL_AT_NAME,
                        Intent(ACTION_VIEW, Uri.parse("tblite://notifications/1")).setPackage(packageName)
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
            .setColor(ThemeUtils.getColorByAttr(this, R.attr.colorPrimary))
            .build()
        notificationManager!!.notify(id, notification)
    }

    companion object {
        val TAG = NotifyJobService::class.java.simpleName
        const val ACTION_NEW_MESSAGE = "com.huanchengfly.tieba.post.action.NEW_MESSAGE"
        const val CHANNEL_GROUP = "20"
        const val CHANNEL_AT = "3"
        const val CHANNEL_AT_NAME = "提到我的"
        const val CHANNEL_TOTAL = "total"
        const val ID_REPLY = 20
        const val ID_AT = 21
        private const val CHANNEL_GROUP_NAME = "消息通知"
        private const val CHANNEL_REPLY = "2"
        private const val CHANNEL_REPLY_NAME = "回复我的"
    }
}