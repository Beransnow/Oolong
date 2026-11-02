package com.huanchengfly.tieba.post.ui.common.theme.compose

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * The springs every animation uses (design-system.md §7). Opening may overshoot a little;
 * closing never does.
 */
object Motion {
    /** Pressing and releasing. */
    fun <T> press(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = 0.6f, stiffness = 1500f, visibilityThreshold = visibilityThreshold)

    /** Indicators, switches, segmented controls, small moves. */
    fun <T> snap(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = 0.72f, stiffness = 420f, visibilityThreshold = visibilityThreshold)

    /** A control growing into a menu, panel or dialog. */
    fun <T> morph(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = 0.74f, stiffness = 420f, visibilityThreshold = visibilityThreshold)

    /** Pages, bottom sheets, layout changes. */
    fun <T> settle(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = 1f, stiffness = 400f, visibilityThreshold = visibilityThreshold)

    /** Closing and putting away. */
    fun <T> dismiss(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = 1f, stiffness = 600f, visibilityThreshold = visibilityThreshold)
}
