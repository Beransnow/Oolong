package oolong.client.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import oolong.client.utils.DateTimeUtils

/** The time "how long ago" is told from, moving on each minute; null where nothing keeps it. */
val LocalNow = staticCompositionLocalOf<State<Long>?> { null }

/** A clock for [LocalNow] that moves on at the turn of each minute while the window is started. */
@Composable
fun rememberMinuteClock(): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val time = System.currentTimeMillis()
                now.longValue = time
                delay(60_000 - time % 60_000)
            }
        }
    }
    return now
}

/** [timestamp], in seconds or milliseconds, as how long ago it was, kept up to date. */
@Composable
fun relativeTime(timestamp: Long): String {
    val context = LocalContext.current
    val now = LocalNow.current?.value ?: System.currentTimeMillis()
    return DateTimeUtils.getRelativeTimeString(context, timestamp, now)
}
