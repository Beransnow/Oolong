package oolong.client.ui.common.theme.compose

import androidx.compose.ui.graphics.Color

/** The 0.5dp edge around glass (design-system.md §3). */
val ExtendedColors.glassEdge: Color
    get() = if (isNightMode) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)

/** The dim behind dialogs and bottom sheets (design-system.md §3). */
val ExtendedColors.scrim: Color
    get() = Color.Black.copy(alpha = if (isNightMode) 0.40f else 0.28f)

/** Shadow colours for glass (design-system.md §3). */
val ShadowAmbient = Color.Black.copy(alpha = 0.10f)
val ShadowSpot = Color.Black.copy(alpha = 0.16f)

/**
 * The shadow colour for menus, the thickest glass (design-system.md §2.5, L3). Android scales a
 * shadow colour's alpha by the theme's, about 4% ambient and 19% spot, so the L3 values can only
 * be reached as nearly as solid black gets.
 */
val MenuShadow = Color.Black
