package oolong.client.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import android.widget.Toast
import oolong.client.R

/**
 * Hears how an update's install session went: passes on the system's confirmation when it asks for
 * one, and says so when the install failed. On success the app is replaced and restarted.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }

            PackageInstaller.STATUS_SUCCESS -> Unit

            // Turned down by the user, which needs no word from the app
            PackageInstaller.STATUS_FAILURE_ABORTED -> Unit

            else -> {
                Log.w("UpdateInstall", "status $status: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
                Toast.makeText(context, R.string.update_install_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
