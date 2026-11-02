package com.huanchengfly.tieba.post.ui.common.theme.compose

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
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

    /** Pages, bottom sheets, layout changes. */
    fun <T> settle(visibilityThreshold: T? = null): SpringSpec<T> = motionSpring(1f, 0.5f, visibilityThreshold)

    /** Closing and putting away. */
    fun <T> dismiss(visibilityThreshold: T? = null): SpringSpec<T> = motionSpring(1f, 0.4f, visibilityThreshold)

    /**
     * Where a 0–1 progress counts as arrived. Compose's default for floats, 0.01, cuts the tail
     * of the spring off visibly.
     */
    const val ProgressThreshold = 0.001f
}

private fun <T> motionSpring(damping: Float, response: Float, visibilityThreshold: T?): SpringSpec<T> {
    val omega = 2 * PI.toFloat() / response
    return spring(dampingRatio = damping, stiffness = omega * omega, visibilityThreshold = visibilityThreshold)
}
