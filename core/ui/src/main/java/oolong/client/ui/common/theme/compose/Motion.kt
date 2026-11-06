package oolong.client.ui.common.theme.compose

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.PI

/**
 * The springs every animation uses (design-system.md §7), given as damping and response, the
 * time in seconds one oscillation would take, as iOS and HyperOS describe theirs. Opening may
 * overshoot a little; closing never does.
 */
object Motion {
    /** Pressing down: quick, no bounce. */
    fun <T> press(visibilityThreshold: T? = null): SpringSpec<T> = motionSpring(1f, 0.2f, visibilityThreshold)

    /** Letting go: slower than pressing. */
    fun <T> release(visibilityThreshold: T? = null): SpringSpec<T> = motionSpring(0.9f, 0.35f, visibilityThreshold)

    /** Indicators, switches, segmented controls, small moves. */
    fun <T> snap(visibilityThreshold: T? = null): SpringSpec<T> = motionSpring(0.82f, 0.35f, visibilityThreshold)

    /** A control growing into a menu, panel or dialog. */
    fun <T> morph(visibilityThreshold: T? = null): SpringSpec<T> = motionSpring(0.85f, 0.4f, visibilityThreshold)

    /** Pages sliding in and out: quicker than [settle], as a push or pop follows a tap. */
    fun <T> page(visibilityThreshold: T? = null): SpringSpec<T> = motionSpring(1f, 0.35f, visibilityThreshold)

    /** Bottom sheets, dialogs, layout changes. */
    fun <T> settle(visibilityThreshold: T? = null): SpringSpec<T> = motionSpring(1f, 0.4f, visibilityThreshold)

    /** A dialog coming up: a little quicker than [page], with a touch of give at the end. */
    fun <T> present(visibilityThreshold: T? = null): SpringSpec<T> = motionSpring(0.9f, 0.3f, visibilityThreshold)

    /**
     * Opacity appearing. Opacity may use a short fixed fade (design-system.md §7.2): a spring
     * starts from rest, so something fading in on one stays nearly invisible at first.
     */
    fun <T> fadeIn(): TweenSpec<T> = tween(durationMillis = 120, easing = LinearOutSlowInEasing)

    /** Opacity going away, quicker than [fadeIn]. */
    fun <T> fadeOut(): TweenSpec<T> = tween(durationMillis = 100, easing = FastOutLinearInEasing)

    /** Closing and putting away: quicker than opening. */
    fun <T> dismiss(visibilityThreshold: T? = null): SpringSpec<T> = motionSpring(1f, 0.3f, visibilityThreshold)

    /**
     * Where a 0–1 progress counts as arrived. Compose's default for floats, 0.01, cuts the tail
     * of the spring off visibly.
     */
    const val ProgressThreshold = 0.001f
}

/**
 * Whether motion is reduced (design-system.md §7.2), by the app's setting or with the system's
 * animations off: what grows out of something else, such as a page out of a card or a picture
 * out of its thumbnail, fades in place instead, and what comes into focus only fades.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

private fun <T> motionSpring(damping: Float, response: Float, visibilityThreshold: T?): SpringSpec<T> {
    val omega = 2 * PI.toFloat() / response
    return spring(dampingRatio = damping, stiffness = omega * omega, visibilityThreshold = visibilityThreshold)
}
