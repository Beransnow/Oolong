package com.huanchengfly.tieba.post.ui.common.navigation

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.Navigator
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.utils.ThemeUtil
import com.ramcosta.composedestinations.animations.defaults.RootNavGraphDefaultAnimations
import com.ramcosta.composedestinations.animations.rememberAnimatedNavHostEngine
import com.ramcosta.composedestinations.manualcomposablecalls.ManualComposableCalls
import com.ramcosta.composedestinations.navigation.DependenciesContainerBuilder
import com.ramcosta.composedestinations.scope.DestinationScope
import com.ramcosta.composedestinations.spec.DestinationSpec
import com.ramcosta.composedestinations.spec.DestinationStyle
import com.ramcosta.composedestinations.spec.NavGraphSpec
import com.ramcosta.composedestinations.spec.NavHostEngine
import com.ramcosta.composedestinations.spec.Route
import kotlin.math.roundToInt

/**
 * Page transitions in the style of a navigation stack: the new page slides in
 * over the current one, which drifts a little the same way and darkens.
 *
 * The translucent theme keeps full-width slides with no overlap, because its
 * pages are transparent over a shared wallpaper.
 */
object PageTransitions {
    private const val DurationMillis = 400

    /** How far the page underneath moves, as a fraction of the container width. */
    private const val UnderPageOffset = 0.3f

    /** Black overlay alpha on the page underneath once it is fully covered. */
    private const val UnderPageDim = 0.12f

    private val ShadowWidth = 12.dp
    private const val ShadowAlpha = 0.12f

    private val Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    private fun <T> spec(): FiniteAnimationSpec<T> = tween(DurationMillis, easing = Easing)

    @Composable
    @OptIn(ExperimentalAnimationApi::class)
    fun rememberNavHostEngine(): NavHostEngine {
        val overlap = !ThemeUtil.isTranslucentTheme(ExtendedTheme.colors.theme)
        val overlapState = remember { mutableStateOf(overlap) }
        SideEffect { overlapState.value = overlap }
        val engine = rememberAnimatedNavHostEngine(
            navHostContentAlignment = Alignment.TopStart,
            rootDefaultAnimations = defaultAnimations(overlapState),
        )
        return remember(engine) { FramedNavHostEngine(engine) }
    }

    private fun underPageOffset(fullOffset: Int, overlap: Boolean): Int =
        if (overlap) (fullOffset * UnderPageOffset).roundToInt() else fullOffset

    private fun defaultAnimations(overlap: State<Boolean>) = RootNavGraphDefaultAnimations(
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = spec<IntOffset>(),
                initialOffset = { it }
            )
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = spec<IntOffset>(),
                targetOffset = { -underPageOffset(it, overlap.value) }
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = spec<IntOffset>(),
                initialOffset = { -underPageOffset(it, overlap.value) }
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = spec<IntOffset>(),
                targetOffset = { it }
            )
        },
    )

    /**
     * Gives a page an opaque background, so the page underneath doesn't show
     * through while the two overlap, plus the dim and the edge shadow.
     */
    @Composable
    private fun PageFrame(
        scope: AnimatedVisibilityScope,
        entry: NavBackStackEntry,
        navController: NavController,
        content: @Composable () -> Unit,
    ) {
        if (ThemeUtil.isTranslucentTheme(ExtendedTheme.colors.theme)) {
            content()
            return
        }

        // A page entering with saved state is coming back from underneath a
        // popped page; a new page has none.
        var shownBefore by rememberSaveable { mutableStateOf(false) }
        val returning = remember { shownBefore }
        SideEffect { shownBefore = true }

        val transition = scope.transition
        val dim = transition.animateFloat(
            transitionSpec = { spec() },
            label = "PageDim",
        ) { state ->
            when (state) {
                EnterExitState.PreEnter -> if (returning) UnderPageDim else 0f
                EnterExitState.Visible -> 0f
                EnterExitState.PostExit ->
                    if (navController.previousBackStackEntry?.id == entry.id) UnderPageDim else 0f
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

    private class FramedDestination<T>(
        private val wrapped: DestinationSpec<T>,
    ) : DestinationSpec<T> by wrapped {
        // Inside Content(), `destination` is the scope's, i.e. this wrapper.
        @Composable
        override fun DestinationScope<T>.Content() {
            val scope = this as? AnimatedVisibilityScope
            if (scope == null) {
                with(wrapped) { Content() }
                return
            }
            PageFrame(scope, navBackStackEntry, navController) {
                with(wrapped) { Content() }
            }
        }
    }

    private class FramedNavHostEngine(
        private val delegate: NavHostEngine,
    ) : NavHostEngine {
        override val type: NavHostEngine.Type
            get() = delegate.type

        @Composable
        override fun rememberNavController(
            vararg navigators: Navigator<out NavDestination>,
        ): NavHostController = delegate.rememberNavController(*navigators)

        @Composable
        override fun NavHost(
            modifier: Modifier,
            route: String,
            startRoute: Route,
            navController: NavHostController,
            builder: NavGraphBuilder.() -> Unit,
        ) = delegate.NavHost(modifier, route, startRoute, navController, builder)

        override fun NavGraphBuilder.navigation(
            navGraph: NavGraphSpec,
            builder: NavGraphBuilder.() -> Unit,
        ) = with(delegate) { navigation(navGraph, builder) }

        // ManualComposableCalls is library-restricted but part of the signature being overridden.
        @SuppressLint("RestrictedApi")
        override fun <T> NavGraphBuilder.composable(
            destination: DestinationSpec<T>,
            navController: NavHostController,
            dependenciesContainerBuilder: @Composable DependenciesContainerBuilder<*>.() -> Unit,
            manualComposableCalls: ManualComposableCalls,
        ) {
            val framed = when (destination.style) {
                is DestinationStyle.Default, is DestinationStyle.Animated -> FramedDestination(destination)
                else -> destination
            }
            with(delegate) {
                composable(framed, navController, dependenciesContainerBuilder, manualComposableCalls)
            }
        }
    }
}
