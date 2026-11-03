package oolong.client.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import androidx.core.content.ContextCompat
import oolong.client.R
import oolong.client.api.legacyThreadRemoteDataSource
import oolong.client.pendingIntentFlagMutable
import oolong.client.receivers.AutoSignAlarm
import oolong.client.services.OKSignService
import oolong.client.toastShort
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.navigation.WebViewKey
import oolong.client.ui.widgets.compose.AppDialogs
import java.util.Calendar

object TiebaUtil {
    /** The label of what the app copies, so it can tell its own copies apart. */
    const val ClipLabel = "Oolong"

    private fun ClipData.setIsSensitive(isSensitive: Boolean): ClipData = apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, isSensitive)
            }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun copyText(
        context: Context,
        text: String?,
        toast: String = context.getString(R.string.toast_copy_success),
        isSensitive: Boolean = false
    ) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText(ClipLabel, text).setIsSensitive(isSensitive)
        cm.setPrimaryClip(clipData)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            context.toastShort(toast)
        }
    }

    fun initAutoSign(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val autoSign = context.appPreferences.autoSign
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, AutoSignAlarm::class.java),
            pendingIntentFlagMutable()
        )
        if (autoSign) {
            val autoSignTimeStr = context.appPreferences.autoSignTime!!
            val time = autoSignTimeStr.split(":").toTypedArray()
            val hour = time[0].toInt()
            val minute = time[1].toInt()
            val calendar = Calendar.getInstance()
            calendar[Calendar.HOUR_OF_DAY] = hour
            calendar[Calendar.MINUTE] = minute
            if (calendar.timeInMillis >= System.currentTimeMillis()) {
                alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    AlarmManager.INTERVAL_DAY,
                    pendingIntent
                )
            }
        } else {
            alarmManager.cancel(pendingIntent)
        }
    }

    @JvmStatic
    fun startSign(context: Context) {
        context.appPreferences.signDay = Calendar.getInstance()[Calendar.DAY_OF_MONTH]
//        OKSignService.enqueueWork(
//            context,
//            Intent()
//                .setClassName(
//                    context.packageName,
//                    "${context.packageName}.services.OKSignService"
//                )
//                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//                .setAction(OKSignService.ACTION_START_SIGN)
//        )
        ContextCompat.startForegroundService(
            context,
            Intent()
                .setClassName(
                    context.packageName,
                    "${context.packageName}.services.OKSignService"
                )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setAction(OKSignService.ACTION_START_SIGN)
        )
    }

    @JvmStatic
    @JvmOverloads
    fun shareText(context: Context, text: String, title: String? = null) {
        context.startActivity(Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "${if (title != null) "「$title」\n" else ""}$text\n" + context.getString(R.string.share_suffix)
            )
        })
    }

    suspend fun reportPost(
        context: Context,
        navigator: Navigator,
        postId: String,
    ) {
        val url = AppDialogs.withLoading { runCatching { legacyThreadRemoteDataSource.reportUrl(postId.toLong()) } }
        url.onSuccess { navigator.navigate(WebViewKey(it)) }
            .onFailure { context.toastShort(R.string.toast_load_failed) }
    }
}
