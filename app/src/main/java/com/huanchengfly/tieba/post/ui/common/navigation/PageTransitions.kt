package com.huanchengfly.tieba.post.ui.common.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.Motion
import com.huanchengfly.tieba.post.utils.ThemeUtil
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
    private fun <T> spec(): FiniteAnimationSpec<T> = Motion.settle()

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
     */
    @Composable
    fun PageFrame(content: @Composable () -> Unit) {
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    if (transition.currentState != transition.targetState) {
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
                    val alpha = dim.value
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

/** Shows the top entry alone, in a [PageTransitions.PageFrame]. */
class FramedSinglePaneSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T> =
        FramedSinglePaneScene(entry = entries.last(), previousEntries = entries.dropLast(1))
}

private data class FramedSinglePaneScene<T : Any>(
    val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
) : Scene<T> {
    override val key: Any = entry.contentKey
    override val entries: List<NavEntry<T>> = listOf(entry)
    override val content: @Composable () -> Unit = { PageTransitions.PageFrame { entry.Content() } }
}
