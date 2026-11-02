package com.huanchengfly.tieba.post.ui.common.theme.compose

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Corner radii (design-system.md §6). Nested corners take the outer radius minus the inset. */
object Radius {
    /** Level badges, small tags. */
    val xs = 4.dp

    /** Images, sub-post blocks, input fields. */
    val sm = 8.dp

    /** Cards, settings groups. */
    val md = 12.dp

    /** Menus, popovers. */
    val lg = 18.dp

    /** Dialogs, bottom sheets, input panels; a 44dp capsule 14dp in from it sits concentric. */
    val xl = 36.dp
}

/** The corner shapes for [Radius], all smooth. */
object Corners {
    val xs: Shape = SmoothCornerShape(Radius.xs)
    val sm: Shape = SmoothCornerShape(Radius.sm)
    val md: Shape = SmoothCornerShape(Radius.md)
    val lg: Shape = SmoothCornerShape(Radius.lg)
    val xl: Shape = SmoothCornerShape(Radius.xl)

    /** Capsules: buttons, bars, switches. */
    val full: Shape = RoundedCornerShape(percent = 50)
}

/** Spacing on a 4dp grid (design-system.md §5). */
object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Fixed sizes (design-system.md §5). */
object Dimens {
    /** Left and right page margin on compact windows. */
    val PageGutter = 16.dp

    /** Left and right page margin on medium and wider windows. */
    val PageGutterWide = 24.dp

    /** The smallest touch target; smaller visuals pad out to it. */
    val MinTouchTarget = 48.dp

    /** The smallest height of a settings row. */
    val SettingsRowMinHeight = 52.dp

    /** The space between settings groups. */
    val SettingsGroupGap = 24.dp
}
