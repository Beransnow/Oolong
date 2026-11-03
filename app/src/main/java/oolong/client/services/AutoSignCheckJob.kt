package oolong.client.services

import android.app.job.JobParameters
import android.app.job.JobService

/** Every few hours while online: catches today's signing up if its alarm never went off. */
class AutoSignCheckJob : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        runCatching { AutoSign.catchUp(this, foreground = false) }
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false
}
