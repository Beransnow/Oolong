package com.huanchengfly.tieba.post.ui.common.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigationevent.NavigationEvent
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.Motion
import com.huanchengfly.tieba.post.ui.common.theme.compose.Radius
import com.huanchengfly.tieba.post.utils.ThemeUtil
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/** Whether the latest change to the back stack removed pages, as opposed to adding them. */
val LocalIsPop = compositionLocalOf { false }

/**
 * Page transitions in the style of a navigation stack: the new page slides in
 * over the current one, which drifts a little the same way and darkens.
 *
 * The translucent theme keeps full-width slides with no overlap, because its
 * pages are transparent over a shared wallpaper.
 */
object PageTransitions {
    /** How far the page underneath moves, as a fraction of the container width. */
    private const val UnderPageOffset = 0.3f

    /**
     * Black overlay alpha on the page underneath once it is fully covered.
     * Dark themes need more: their background is already near black, so only
     * text and images darken.
     */
    private const val UnderPageDim = 0.12f
    private const val UnderPageDimNight = 0.35f

    private val ShadowWidth = 12.dp
    private const val ShadowAlpha = 0.12f

    /** A spring, so a transition interrupted halfway carries on from where it is. */
    private fun <T> spec(): FiniteAnimationSpec<T> = Motion.page()

    /** Whether pages overlap as they slide, which follows the theme. */
    @Composable
    fun rememberOverlap(): State<Boolean> {
        val overlap = !ThemeUtil.isTranslucentTheme(ExtendedTheme.colors.theme)
        val overlapState = remember { mutableStateOf(overlap) }
        SideEffect { overlapState.value = overlap }
        return overlapState
    }

    private fun underPageOffset(fullOffset: Int, overlap: Boolean): Int =
        if (overlap) (fullOffset * UnderPageOffset).roundToInt() else fullOffset

    /** Opening a page, for scenes or for the content of one pane. */
    fun <S> transitionSpec(
        overlap: State<Boolean>,
    ): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        slideIntoContainer(
            AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = spec<IntOffset>(),
            initialOffset = { it }
        ) togetherWith slideOutOfContainer(
            AnimatedContentTransitionScope.SlideDirection.End,
            animationSpec = spec<IntOffset>(),
            targetOffset = { -underPageOffset(it, overlap.value) }
        )
    }

    fun <S> popTransitionSpec(
        overlap: State<Boolean>,
    ): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        slideIntoContainer(
            AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = spec<IntOffset>(),
            initialOffset = { -underPageOffset(it, overlap.value) }
        ) togetherWith slideOutOfContainer(
            AnimatedContentTransitionScope.SlideDirection.End,
            animationSpec = spec<IntOffset>(),
            targetOffset = { it }
        )
    }

    /**
     * Going back after a peek ([PagePeek]): the page underneath is already in place, so only the
     * page on top moves, sliding out from where the peek left it. A tween, as NavDisplay plays it
     * by seeking.
     */
    fun <S> peekPopTransitionSpec(): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        EnterTransition.None togetherWith slideOutOfContainer(
            AnimatedContentTransitionScope.SlideDirection.End,
            animationSpec = tween(PeekExitMillis, easing = PeekExitEasing),
            targetOffset = { it }
        )
    }

    private const val PeekExitMillis = 400

    /**
     * The curve of [Motion.page] setting off from rest, a critically damped spring with a response
     * of 0.35s, over [PeekExitMillis]: the slide out keeps the pace of any other pop.
     */
    private val PeekExitEasing = Easing { fraction ->
        val span = 2 * PI.toFloat() / 0.35f * PeekExitMillis / 1000f
        val x = fraction * span
        (1f - (1f + x) * exp(-x)) / (1f - (1f + span) * exp(-span))
    }

    /** The page on top at the furthest peek: its scale, its shift toward the finger as a fraction of its width, and its corners. */
    private const val PeekScale = 0.9f
    private const val PeekShift = 0.05f
    private val PeekRadius = Radius.xl
    private val PeekElevation = 16.dp

    /** How much of the dim on the page underneath lifts at the furthest peek. */
    private const val PeekDimRelief = 0.3f

    /**
     * A quick cross-fade, for changes that are not a step deeper or back: one page swapped for
     * another at the same level, or the same pages laid out again for a new window size.
     */
    fun crossFade(): ContentTransform =
        fadeIn(tween(CrossFadeMillis)) togetherWith fadeOut(tween(CrossFadeMillis))

    private const val CrossFadeMillis = 200

    /**
     * Gives a page an opaque background, so the page underneath doesn't show
     * through while the two overlap, plus the dim and the edge shadow. Works on
     * whatever area the page is given, so a scene can frame each of its panes.
     *
     * A page framed alone passes its content [key], so that it can be peeked past ([PagePeek]).
     */
    @Composable
    fun PageFrame(key: Any? = null, content: @Composable () -> Unit) {
        if (ThemeUtil.isTranslucentTheme(ExtendedTheme.colors.theme)) {
            content()
            return
        }

        // Entering on a pop means coming back from underneath; leaving on a push means being covered
        val isPop = LocalIsPop.current
        val underPageDim = if (ExtendedTheme.colors.isNightMode) UnderPageDimNight else UnderPageDim
        val transition = LocalNavAnimatedContentScope.current.transition
        val dim = transition.animateFloat(
            transitionSpec = { spec() },
            label = "PageDim",
        ) { state ->
            when (state) {
                EnterExitState.PreEnter -> if (isPop) underPageDim else 0f
                EnterExitState.Visible -> 0f
                EnterExitState.PostExit -> if (isPop) 0f else underPageDim
            }
        }
        // Where the page is, against the host that pages slide within
        val host = LocalPageHost.current
        val pageX = remember { mutableFloatStateOf(0f) }
        val pageWidth = remember { mutableFloatStateOf(0f) }
        val peek = if (key != null) LocalPagePeek.current else null
        if (peek != null) {
            // Rests once the scenes are still and this page is the one shown
            LaunchedEffect(peek, key) {
                snapshotFlow {
                    val scenes = transition.parentTransition
                    val still = if (scenes != null) scenes.currentState == scenes.targetState else !transition.isRunning
                    still && transition.targetState == EnterExitState.Visible
                }.collect { resting ->
                    if (resting) peek.restingKey = key else if (peek.restingKey == key) peek.restingKey = null
                }
            }
            DisposableEffect(peek, key) {
                onDispose {
                    if (peek.restingKey == key) peek.restingKey = null
                    if (peek.topKey == key) peek.reset()
                }
            }
        }
        val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    pageX.floatValue = it.positionInRoot().x
                    pageWidth.floatValue = it.size.width.toFloat()
                    if (peek != null && peek.topKey == key && host != null && host.width > 0f) {
                        val slid = (pageX.floatValue - host.left) * (if (ltr) 1f else -1f)
                        peek.exit = (slid / host.width).coerceIn(0f, 1f)
                    }
                }
                .then(
                    if (peek != null) {
                        Modifier.graphicsLayer {
                            val amount = if (peek.topKey == key) peek.amount else 0f
                            if (amount > 0f) {
                                val scale = 1f - (1f - PeekScale) * amount
                                scaleX = scale
                                scaleY = scale
                                val toward = if (peek.edge == NavigationEvent.EDGE_RIGHT) -1f else 1f
                                translationX = toward * size.width * PeekShift * amount
                                shape = RoundedCornerShape(PeekRadius * amount)
                                clip = true
                                shadowElevation = PeekElevation.toPx() * amount
                            }
                        }
                    } else {
                        Modifier
                    }
                )
                .drawWithContent {
                    // Whether pages are moving, by the scene transition: a page's own transition
                    // can be left mid-way, still running, when a push is interrupted by back
                    val scenes = transition.parentTransition
                    val moving = if (scenes != null) {
                        scenes.currentState != scenes.targetState
                    } else {
                        transition.currentState != transition.targetState
                    }
                    if (moving) {
                        val shadowWidth = ShadowWidth.toPx()
                        val ltr = layoutDirection == LayoutDirection.Ltr
                        val left = if (ltr) -shadowWidth else size.width
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = if (ltr) {
                                    listOf(Color.Transparent, Color.Black.copy(alpha = ShadowAlpha))
                                } else {
                                    listOf(Color.Black.copy(alpha = ShadowAlpha), Color.Transparent)
                                },
                                startX = left,
                                endX = left + shadowWidth,
                            ),
                            topLeft = Offset(left, 0f),
                            size = Size(shadowWidth, size.height),
                        )
                    }
                    drawContent()
                    val width = pageWidth.floatValue
                    val alpha = if (peek != null && peek.underKey == key) {
                        // Underneath a peek: lifting a little as the peek grows, and going as the
                        // page on top slides out
                        underPageDim * (1f - PeekDimRelief * peek.amount) * (1f - peek.exit)
                    } else if (host != null && width > 0f && abs(width - host.width) < 1f) {
                        // The page underneath sits to the start side of where it rests, covered on
                        // a push and coming back on a pop, while the page on top comes and goes on
                        // the end side and is never dimmed. How dim follows how far it is from
                        // rest, so the dim eases with the slide and turns back with it when the
                        // transition is interrupted
                        val towardStart = (host.left - pageX.floatValue) * (if (layoutDirection == LayoutDirection.Rtl) -1f else 1f)
                        val underneath = towardStart > 0f
                        val distance = abs(towardStart)
                        if (underneath) underPageDim * (distance / (width * UnderPageOffset)).coerceIn(0f, 1f) else 0f
                    } else if (moving) {
                        // A pane of a wider scene: its own animation, only while pages move
                        dim.value
                    } else {
                        0f
                    }
                    if (alpha > 0f) {
                        drawRect(Color.Black.copy(alpha = alpha))
                    }
                }
                .background(ExtendedTheme.colors.background)
        ) {
            content()
        }
    }
}

/** Where pages slide within: the area the navigation lays them out in, which itself stays put. */
@Stable
class PageHost {
    var left by mutableFloatStateOf(0f)
    var width by mutableFloatStateOf(0f)
}

/** The [PageHost] around the pages, which pages measure their slide against. */
val LocalPageHost = staticCompositionLocalOf<PageHost?> { null }

/** Records where [host] is laid out; apply to the container the pages slide within. */
fun Modifier.pageHost(host: PageHost): Modifier = onGloballyPositioned {
    host.left = it.positionInRoot().x
    host.width = it.size.width.toFloat()
}

/** Shows the top entry alone, in a [PageTransitions.PageFrame]. */
class FramedSinglePaneSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T> =
        FramedSinglePaneScene(entry = entries.last(), previousEntries = entries.dropLast(1))
}

internal data class FramedSinglePaneScene<T : Any>(
    val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
) : Scene<T> {
    override val key: Any = entry.contentKey
    override val entries: List<NavEntry<T>> = listOf(entry)
    override val content: @Composable () -> Unit = { PageTransitions.PageFrame(key = entry.contentKey) { entry.Content() } }
}
