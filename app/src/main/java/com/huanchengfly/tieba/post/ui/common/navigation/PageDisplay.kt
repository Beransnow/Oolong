package com.huanchengfly.tieba.post.ui.common.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.rememberTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KClass

/** A scene by its class and key: recalculated for the same pages, a scene is still the same one. */
data class SceneKey(val type: KClass<*>, val key: Any) {
    constructor(scene: Scene<*>) : this(scene::class, scene.key)
}

/**
 * How the display moves from one scene to another: the content transform, whose animations must
 * all be [PageTransitions.seeked], as the display plays them by fraction; [drive], what takes
 * that fraction from 0 to 1; and [zIndex], where the scene coming in goes against the one going.
 */
class PageTransition(
    val transform: AnimatedContentTransitionScope<SceneKey>.() -> ContentTransform,
    val drive: FiniteAnimationSpec<Float>,
    val zIndex: Float,
)

/**
 * Shows [scenes], with overlays above, moving between them as [transitionFor] describes. What
 * Navigation 3's NavDisplay does, but every transition runs on one fraction that a spring of its
 * own drives, so whatever is told by that transition (slides, the page dim, shared elements)
 * moves as one, and back while pages move turns them round from where they are, as fast as they
 * go, instead of playing the opening backwards.
 *
 * While [peek] holds a peek, the scene under the top one is brought in at a fraction of 0, so it
 * is laid out underneath; [transitionFor] is told, so pages can stay where they are.
 */
@Composable
fun <T : Any> PageDisplay(
    scenes: PageScenes<T>,
    peek: PagePeek?,
    modifier: Modifier = Modifier,
    transitionFor: (from: Scene<T>, to: Scene<T>, peek: Boolean) -> PageTransition,
) {
    val current = scenes.currentScene
    val under = scenes.previousScenes.lastOrNull()
    val peeking = peek != null && peek.holding && !peek.committed && peek.topKey == current.key && under != null
    val wanted = SceneKey(if (peeking) under!! else current)

    // The latest scene for each key, kept while a transition still shows it. The one underneath
    // is kept too, for a peek, unless it shares the key of the current one, as the panes side by
    // side do, when the current one is what shows
    val known = remember { mutableStateMapOf<SceneKey, Scene<T>>() }
    if (under != null && SceneKey(under) != SceneKey(current) && known[SceneKey(under)] !== under) {
        known[SceneKey(under)] = under
    }
    if (known[SceneKey(current)] !== current) known[SceneKey(current)] = current

    val state = remember { SeekableTransitionState(wanted) }
    val transition = rememberTransition(state, label = "Pages")
    val fraction = remember { Fraction() }
    val segment = remember { Segment() }
    val zIndices = remember { mutableStateMapOf(wanted to 0f) }

    // How fast the transition moves while it plays by itself, for turning it round from that pace
    LaunchedEffect(state) {
        var last = state.fraction
        var lastTime = withFrameNanos { it }
        while (true) {
            val time = withFrameNanos { it }
            if (fraction.played) {
                val now = state.fraction
                val dt = (time - lastTime) / 1_000_000_000f
                if (dt > 0f && state.currentState != state.targetState) fraction.velocity = (now - last) / dt
                last = now
            }
            lastTime = time
        }
    }
    // Keyed on the peek too: letting go of one wants the same scene, now to be gone through
    LaunchedEffect(wanted, peeking) {
        val from = state.currentState
        val to = state.targetState
        // After a transition that played by itself, pick up from where it got to
        if (fraction.played) {
            fraction.value = state.fraction
            fraction.played = false
        }
        when (wanted) {
            // Going on, or settled with the scene recalculated
            to -> if (from != to) state.play(wanted, segment.drive, fraction)
            // Turning round: driven, from where it is and as fast as it moves, so it carries on a
            // moment before it comes back
            from -> {
                state.driveTo(0f, segment.drive, fraction)
                state.snapTo(wanted)
            }
            // Somewhere new, from where things are
            else -> {
                val fromScene = known[to] ?: known[from]
                val toScene = known.getValue(wanted)
                val next = fromScene?.let { transitionFor(it, toScene, peeking) }
                segment.transform = next?.transform
                segment.drive = next?.drive ?: segment.drive
                // A page coming in goes over every page still showing, and one coming back under them
                val shownZ = listOf(from, to).mapNotNull { zIndices[it] }
                zIndices[wanted] = when {
                    next == null || next.zIndex == 0f -> zIndices[to] ?: 0f
                    next.zIndex > 0f -> (shownZ.maxOrNull() ?: 0f) + next.zIndex
                    else -> (shownZ.minOrNull() ?: 0f) + next.zIndex
                }
                fraction.value = 0f
                fraction.velocity = 0f
                // Pages still moving to another scene carry on to the end on their own springs,
                // alongside the new move
                if (peeking) {
                    // A peek waits at 0 for the finger to let go
                    state.seekTo(0f, wanted)
                } else {
                    state.play(wanted, segment.drive, fraction)
                }
            }
        }
        // Done with: scenes no longer shown or underneath
        if (state.currentState == state.targetState) {
            val keep = setOf(state.currentState, SceneKey(current)) + scenes.previousScenes.map { SceneKey(it) }
            known.keys.retainAll(keep)
            zIndices.keys.retainAll(keep)
        }
    }

    // Overlays, bottom first. Each entry is drawn by one scene only: overlays first, top down, then
    // the scene coming in, which in a relayout claims the pages it shares with the one going
    val overlays = remember { mutableStateListOf<OverlayScene<T>>().apply { addAll(scenes.overlayScenes.reversed()) } }
    LaunchedEffect(scenes.overlayScenes) {
        scenes.overlayScenes.reversed().forEach { scene -> if (overlays.none { it.key == scene.key }) overlays.add(scene) }
    }
    val shown = (overlays.reversed() + listOfNotNull(known[transition.targetState], known[transition.currentState]))
    val elsewhere = buildMap {
        val claimed = mutableSetOf<Any>()
        shown.forEach { scene ->
            putIfAbsent(SceneKey(scene), claimed.toSet())
            claimed.addAll(scene.entries.map { it.contentKey })
        }
    }

    var contentScope: AnimatedContentScope? = null
    transition.AnimatedContent(
        modifier = modifier,
        transitionSpec = {
            val transform = segment.transform?.invoke(this) ?: PageTransitions.none()
            ContentTransform(
                targetContentEnter = transform.targetContentEnter,
                initialContentExit = transform.initialContentExit,
                targetContentZIndex = zIndices[targetState] ?: 0f,
                sizeTransform = null,
            )
        },
    ) { sceneKey ->
        contentScope = this
        val scene = known[sceneKey] ?: return@AnimatedContent
        // Resumed only once the pages settle, with nothing over them
        val settled = transition.currentState == transition.targetState
        val owner = rememberLifecycleOwner(
            maxLifecycle = if (settled && overlays.isEmpty()) Lifecycle.State.RESUMED else Lifecycle.State.STARTED,
        )
        CompositionLocalProvider(
            LocalLifecycleOwner provides owner,
            LocalNavAnimatedContentScope provides this,
            LocalEntriesShownElsewhere provides (elsewhere[sceneKey] ?: emptySet()),
        ) {
            scene.content()
        }
    }

    // Overlays above the pages; one taken off the stack stays until it has animated away
    overlays.toList().forEach { overlay ->
        key(overlay.key) {
            val owner = rememberLifecycleOwner(
                maxLifecycle = if (scenes.overlayScenes.firstOrNull()?.key == overlay.key) Lifecycle.State.RESUMED else Lifecycle.State.STARTED,
            )
            CompositionLocalProvider(
                LocalLifecycleOwner provides owner,
                LocalNavAnimatedContentScope provides contentScope!!,
                LocalEntriesShownElsewhere provides (elsewhere[SceneKey(overlay)] ?: emptySet()),
            ) {
                overlay.content()
            }
            if (scenes.overlayScenes.none { it.key == overlay.key }) {
                LaunchedEffect(overlay.key) {
                    overlay.onRemove()
                    overlays.remove(overlay)
                }
            }
        }
    }
}

/** Where the transition under way is, and how fast it moves, kept across interruptions. */
private class Fraction {
    var value = 0f
    var velocity = 0f

    /** Whether the transition plays by itself, so [value] is out of date and [velocity] sampled. */
    var played = false
}

/**
 * Plays the transition on to [target] by [spec]. Played, not driven: a move interrupted by another
 * then carries on to its end on its own spring, alongside the new one.
 */
private suspend fun <S> SeekableTransitionState<S>.play(target: S, spec: FiniteAnimationSpec<Float>, fraction: Fraction) {
    fraction.played = true
    animateTo(target, spec)
    fraction.played = false
    fraction.value = 0f
    fraction.velocity = 0f
}

/**
 * Takes the transition to [target] by [spec], from where [fraction] is and as fast as it moves,
 * seeking every frame. Seeking here, not from another coroutine, keeps every frame's fraction to
 * the transition it was worked out for.
 */
private suspend fun <S> SeekableTransitionState<S>.driveTo(target: Float, spec: FiniteAnimationSpec<Float>, fraction: Fraction) {
    val animation = TargetBasedAnimation(spec, Float.VectorConverter, fraction.value, target, AnimationVector1D(fraction.velocity))
    // At the system's animation scale, so removing animations or slowing them down applies here too
    val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    if (scale <= 0f) {
        fraction.value = target
        fraction.velocity = 0f
        if (currentState != targetState) seekTo(target.coerceIn(0f, 1f))
        return
    }
    // As if it started a frame ago, so the first frame already moves instead of only telling the time
    val start = withFrameNanos { it } - FrameNanos
    while (true) {
        val time = ((withFrameNanos { it } - start) / scale).toLong()
        val done = animation.isFinishedFromNanos(time)
        fraction.value = if (done) target else animation.getValueFromNanos(time)
        fraction.velocity = if (done) 0f else animation.getVelocityVectorFromNanos(time).value
        if (currentState != targetState) seekTo(fraction.value.coerceIn(0f, 1f))
        if (done) break
    }
}

private const val FrameNanos = 16_666_667L

/** The transition under way: set as it starts, and kept as it is turned round or carried on. */
private class Segment {
    var transform: (AnimatedContentTransitionScope<SceneKey>.() -> ContentTransform)? = null
    var drive: FiniteAnimationSpec<Float> = PageTransitions.drive()
}
