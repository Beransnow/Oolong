package oolong.client.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The app's short messages, such as "saved" or a failure, shown as one [GlassToast] over every
 * page rather than as the system's toast, which looks and moves like no part of the app.
 */
object AppToast {
    private class Message(val text: String, val action: String? = null, val answer: CompletableDeferred<Boolean>? = null)

    /**
     * The message to show. A new one replaces the one showing rather than waiting behind it: a
     * message waiting its turn would by then be about something already over, as when a switch is
     * flipped twice in a row.
     */
    private val latest = MutableStateFlow<Message?>(null)

    private fun replaceWith(message: Message) {
        // One replaced before its button was taken counts as not taken
        latest.getAndUpdate { message }?.answer?.complete(false)
    }

    /** How many screens can show a message now; none while the app is in the background. */
    private var hosts by mutableIntStateOf(0)

    /**
     * Shows [text] over the app if it is in front, in place of any message showing now, and
     * returns whether it will; if not, the caller shows it some other way.
     */
    fun post(text: String): Boolean {
        if (hosts == 0) return false
        replaceWith(Message(text))
        return true
    }

    /**
     * Shows [text] with an [action] button for a while, and returns whether the button was
     * tapped; false at once when the app is not in front.
     */
    suspend fun ask(text: String, action: String): Boolean {
        if (hosts == 0) return false
        val answer = CompletableDeferred<Boolean>()
        replaceWith(Message(text, action, answer))
        return answer.await()
    }

    @Composable
    internal fun Host(content: @Composable () -> Unit) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle) {
            var counted = false
            val observer = LifecycleEventObserver { _, _ ->
                val started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                if (started != counted) {
                    hosts += if (started) 1 else -1
                    counted = started
                }
            }
            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
                if (counted) hosts -= 1
            }
        }
        val floors = remember { ToastFloors() }
        // The whole app is what the message's glass shows through
        val glass = rememberHazeState()
        val source = remember(glass) { GlassSource(glass, AppToastKey) }
        var shown by remember { mutableStateOf(Message("")) }
        var visible by remember { mutableStateOf(false) }
        // Only a screen in front takes messages, so one under another screen doesn't show them unseen
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // A new message cuts the wait of the one showing short and refocuses in its place
                latest.collectLatest { message ->
                    if (message == null) return@collectLatest
                    shown = message
                    visible = true
                    try {
                        // Waits its time, or until it is tapped away or its button taken
                        withTimeoutOrNull(shownMillis(message)) { snapshotFlow { visible }.first { !it } }
                    } finally {
                        // Replaced, it stays up for the next; done with, it goes
                        if (latest.compareAndSet(message, null)) visible = false
                        message.answer?.complete(false)
                    }
                }
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().hazeSource(glass, key = AppToastKey)) {
                CompositionLocalProvider(LocalToastFloors provides floors, content = content)
            }
            val floor = with(LocalDensity.current) { floors.highest.toDp() }
            CompositionLocalProvider(LocalGlassSource provides source) {
                GlassToast(
                    visible = visible,
                    text = shown.text,
                    action = shown.action,
                    onAction = {
                        shown.answer?.complete(true)
                        visible = false
                    },
                    onClick = { visible = false },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = floor + FloorGap),
                )
            }
        }
    }

    /** Long enough to read: two seconds, a little more for a long message, and more to take its button. */
    private fun shownMillis(message: Message): Long =
        (if (message.action != null) 4000L else 2000L) + (message.text.length - 15).coerceAtLeast(0) * 40L

    private val FloorGap = 12.dp
}

private object AppToastKey

/**
 * Where messages stand: over the highest bottom chrome a page on screen has, in pixels from the
 * window's bottom, as each [GlassScaffold] reports its own.
 */
internal class ToastFloors {
    private val floors = mutableStateMapOf<Any, Int>()

    val highest: Int get() = floors.values.maxOrNull() ?: 0

    fun report(owner: Any, floor: Int) {
        if (floors[owner] != floor) floors[owner] = floor
    }

    fun remove(owner: Any) {
        floors.remove(owner)
    }
}

internal val LocalToastFloors = staticCompositionLocalOf<ToastFloors?> { null }
