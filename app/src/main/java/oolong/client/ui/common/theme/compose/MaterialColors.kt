package oolong.client.ui.common.theme.compose

import androidx.compose.ui.graphics.Color

/** The 0.5dp edge around glass (design-system.md §3). */
val ExtendedColors.glassEdge: Color
    get() = if (isNightMode) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)

/** The dim behind dialogs and bottom sheets (design-system.md §3). */
val ExtendedColors.scrim: Color
    get() = Color.Black.copy(alpha = if (isNightMode) 0.40f else 0.28f)

/**
 * Android scales a shadow colour's alpha by the theme's, by default these, so a shadow is drawn
 * at a given strength by dividing it by them, as far as solid black allows.
 */
private const val SystemAmbientShadowAlpha = 0.039f
private const val SystemSpotShadowAlpha = 0.19f

private fun ambientShadow(strength: Float) = Color.Black.copy(alpha = (strength / SystemAmbientShadowAlpha).coerceAtMost(1f))
private fun spotShadow(strength: Float) = Color.Black.copy(alpha = (strength / SystemSpotShadowAlpha).coerceAtMost(1f))

/** Shadow colours for floating glass, drawn at its strengths (design-system.md §2.5, L2). */
val ShadowAmbient = ambientShadow(0.10f)
val ShadowSpot = spotShadow(0.16f)

/**
 * The shadow colour for menus, the thickest glass (design-system.md §2.5, L3), whose strengths are
 * past what the system draws: solid black, as near as it gets.
 */
val MenuShadow = Color.Black
