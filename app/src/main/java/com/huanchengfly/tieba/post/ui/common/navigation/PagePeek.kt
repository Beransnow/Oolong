package com.huanchengfly.tieba.post.ui.common.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.navigation3.scene.NavigationBackHandler
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SceneState
import androidx.navigation3.scene.rememberNavigationEventState
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationEventState
import com.huanchengfly.tieba.post.BuildConfig
import com.huanchengfly.tieba.post.rememberPreferenceAsState
import com.huanchengfly.tieba.post.ui.common.theme.compose.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp

/**
 * Back by gesture as a peek: while the finger drags, the page on top shrinks a little toward it and
 * shows the page underneath, and only goes back once let go. How far it shrinks has its own limit,
 * whatever the system makes of the drag, so a page held halfway never leaves.
 *
 * The frames of the two pages read this to draw themselves; [rememberPagePeekBack] drives it.
 */
@Stable
class PagePeek {
    /** The page peeked past, by content key; null when not peeking. */
    var topKey: Any? by mutableStateOf(null)
        private set

    /** The page shown underneath while peeking. */
    var underKey: Any? by mutableStateOf(null)
        private set

    /** 0 at rest, 1 at the furthest peek. Held where it is once back goes ahead. */
    var amount by mutableFloatStateOf(0f)
        internal set

    /** The screen edge the gesture came from, as [NavigationEvent.swipeEdge]. */
    var edge by mutableIntStateOf(NavigationEvent.EDGE_LEFT)
        private set

    /** Whether back went ahead, so the page on top slides out from the peek. */
    var committed by mutableStateOf(false)
        internal set

    /** How far the page on top has slid out once back goes ahead, 0 to 1; recorded by its frame. */
    var exit by mutableFloatStateOf(0f)
        internal set

    /** The page on top while it rests with nothing moving; only such a page can be peeked past. */
    internal var restingKey: Any? by mutableStateOf(null)

    internal val isPeeking: Boolean get() = topKey != null

    internal fun start(top: Any, under: Any, swipeEdge: Int) {
        topKey = top
        underKey = under
        committed = false
        edge = if (swipeEdge == NavigationEvent.EDGE_RIGHT) NavigationEvent.EDGE_RIGHT else NavigationEvent.EDGE_LEFT
        amount = 0f
        exit = 0f
    }

    internal fun reset() {
        committed = false
        topKey = null
        underKey = null
        amount = 0f
        exit = 0f
    }

    internal companion object {
        /** The share of the system's back progress at which the peek is all the way. */
        private const val FullPeekProgress = 0.4f

        /** How far to peek for the system's back progress: quick at first, then flattening out. */
        fun amountFor(progress: Float): Float {
            val p = (progress / FullPeekProgress).coerceIn(0f, 1f)
            return 1f - (1f - p) * (1f - p)
        }
    }
}

/** Whether a back gesture peeks ([PagePeek]); off, it goes back once let go, as the back key does. */
val PredictiveBackKey = booleanPreferencesKey("predictiveBack")

/** The [PagePeek] of the pages around, for their frames to draw. */
val LocalPagePeek = staticCompositionLocalOf<PagePeek?> { null }

/**
 * Takes back for [sceneState] in place of NavDisplay's own handler, and returns the state to give
 * NavDisplay. Pass it to the NavDisplay that takes a scene state, and pages pop through [onBack].
 *
 * A gesture on a page that rests alone drives [peek]. NavDisplay is told the gesture started, so it
 * lays the page underneath out, but never how far it has come, so nothing moves but the peek; once
 * back goes ahead it plays its predictive pop from there, and once the gesture is called off the
 * peek settles back before NavDisplay hears of it. Anything else, such as a back key or a gesture
 * while pages move, just pops.
 */
@Composable
fun <T : Any> rememberPagePeekBack(
    sceneState: SceneState<T>,
    peek: PagePeek,
    onBack: () -> Unit,
): NavigationEventState<SceneInfo<T>> {
    val displayState = rememberNavigationEventState(sceneState)
    // NavDisplay's handler listens to a dispatcher of its own, which only hears what is passed on
    val input = remember { DirectNavigationEventInput() }
    val displayOwner = remember {
        object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = NavigationEventDispatcher().also { it.addInput(input) }
        }
    }
    DisposableEffect(displayOwner) {
        onDispose { displayOwner.navigationEventDispatcher.dispose() }
    }
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides displayOwner) {
        NavigationBackHandler(sceneState, displayState, onBackCompleted = onBack)
    }

    val scope = rememberCoroutineScope()
    val handler = remember(peek) { PeekBackHandler(peek, input, scope) }
    val peeks by rememberPreferenceAsState(PredictiveBackKey, true)
    SideEffect {
        handler.peeks = peeks
        handler.sceneState = sceneState
        handler.isBackEnabled = sceneState.currentScene.previousEntries.isNotEmpty()
    }
    val dispatcher = checkNotNull(LocalNavigationEventDispatcherOwner.current) {
        "No NavigationEventDispatcher was provided via LocalNavigationEventDispatcherOwner"
    }.navigationEventDispatcher
    DisposableEffect(dispatcher, handler) {
        dispatcher.addHandler(handler)
        onDispose { handler.remove() }
    }
    return displayState
}

private class PeekBackHandler(
    private val peek: PagePeek,
    private val input: DirectNavigationEventInput,
    private val scope: CoroutineScope,
) : NavigationEventHandler<NavigationEventInfo>(NavigationEventInfo.None, isBackEnabled = false, isForwardEnabled = false) {
    var sceneState: SceneState<*>? = null
    var peeks = true

    /**
     * The peek as shown. It follows the gesture through a quick spring rather than step for step,
     * as systems report the drag in jumps: one called off may drop straight to nothing.
     */
    private var shown = 0f
    private var velocity = 0f
    private var target = 0f
    private var moving: Job? = null

    /** Run once the peek arrives where it is moving to. */
    private var onArrived: () -> Unit = {}

    /** Whether a gesture is under way. */
    private var dragging = false

    /** Whether the peek is settling back after a gesture was called off. */
    private var settling = false

    /** Where a peek still settling back was when a new gesture took it up again. */
    private var resumedFrom = 0f

    override fun onBackStarted(event: NavigationEvent) {
        log { "started edge=${event.swipeEdge} x=${event.touchX} peekable=${peek.restingKey}" }
        if (settling && peek.isPeeking) {
            // Taken up again before it settled: NavDisplay still has the gesture as started
            settling = false
            dragging = true
            resumedFrom = shown
            onArrived = {}
            return
        }
        resumedFrom = 0f
        // Peeks once the finger moves: a back key starts and completes at once, and pops as usual
        dragging = true
    }

    /** Starts peeking if the page on top rests alone over another. */
    private fun tryPeek(event: NavigationEvent) {
        if (!peeks) return
        val state = sceneState ?: return
        val scene = state.currentScene as? FramedSinglePaneScene<*> ?: return
        val previous = state.previousScenes.lastOrNull() as? FramedSinglePaneScene<*> ?: return
        if (state.overlayScenes.isNotEmpty() || peek.restingKey != scene.key) return
        peek.start(top = scene.key, under = previous.key, swipeEdge = event.swipeEdge)
        moving?.cancel()
        shown = 0f
        velocity = 0f
        target = 0f
        onArrived = {}
        input.backStarted(
            NavigationEvent(
                touchX = event.touchX,
                touchY = event.touchY,
                progress = 0f,
                swipeEdge = event.swipeEdge,
                frameTimeMillis = event.frameTimeMillis,
            )
        )
    }

    override fun onBackProgressed(event: NavigationEvent) {
        // Also a gesture begun while pages still moved, once they rest
        if (dragging && !peek.isPeeking && event.progress > 0f) tryPeek(event)
        if (!peek.isPeeking || settling) return
        val amount = PagePeek.amountFor(event.progress)
        moveTo(amount + (1f - amount) * resumedFrom)
        log { "progress=${event.progress} x=${event.touchX} target=${amount + (1f - amount) * resumedFrom}" }
    }

    override fun onBackCompleted() {
        log { "completed peeking=${peek.isPeeking} amount=${peek.amount}" }
        // The page slides out from where the peek is now
        moving?.cancel()
        onArrived = {}
        settling = false
        dragging = false
        if (peek.isPeeking) peek.committed = true
        // Pops through NavDisplay's handler, which plays the predictive pop if a peek started it
        input.backCompleted()
    }

    override fun onBackCancelled() {
        log { "cancelled peeking=${peek.isPeeking} amount=${peek.amount}" }
        dragging = false
        if (!peek.isPeeking) return
        settling = true
        moveTo(0f) {
            settling = false
            // The page's frame ends the peek once the navigation has it back at rest: until then the
            // page still counts as going away, and would set off
            input.backCancelled()
        }
    }

    /** Moves the peek toward [to] frame by frame, taking a new target up from where it is and how fast it moves. */
    private fun moveTo(to: Float, then: () -> Unit = {}) {
        target = to
        onArrived = then
        if (moving?.isActive == true) return
        moving = scope.launch {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val dt = (now - last) / 1_000_000_000f
                last = now
                // A critically damped spring, stepped exactly: quick to follow the finger, gentler
                // settling back, as Motion.press and Motion.dismiss
                val omega = 2 * PI.toFloat() / (if (settling) SettleResponse else FollowResponse)
                val offset = shown - target
                val decay = exp(-omega * dt)
                val moved = velocity + omega * offset
                shown = target + (offset + moved * dt) * decay
                velocity = (velocity - omega * moved * dt) * decay
                if (abs(shown - target) < Motion.ProgressThreshold && abs(velocity) < Motion.ProgressThreshold) {
                    shown = target
                    velocity = 0f
                }
                peek.amount = shown
                if (shown == target && velocity == 0f) break
            }
            onArrived().also { onArrived = {} }
        }
    }

    private companion object {
        const val FollowResponse = 0.2f
        const val SettleResponse = 0.3f
    }

    /** The gesture as the system reports it, in debug builds, to compare how devices report it. */
    private inline fun log(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d("PagePeek", message())
    }
}
