package oolong.client.ui.widgets.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import oolong.client.components.images.loadBitmap
import oolong.client.ui.common.theme.compose.ExtendedTheme

/**
 * A page's own colour, taken from the avatar of whom or what it is about, such as a forum's or a
 * user's: the header's wash, its button, labels and the floating button use it. Leaving the page
 * leaves the colour behind.
 */
val LocalPageAccent = staticCompositionLocalOf { Color.Unspecified }

/**
 * The colour of [avatar], or [Color.Unspecified] until it is known or when the avatar has none, in
 * which case the page keeps the theme's colours.
 */
@Composable
fun rememberAvatarAccent(avatar: String): Color {
    val context = LocalContext.current
    val night = ExtendedTheme.colors.isNightMode
    val swatch by produceState<Int?>(initialValue = null, avatar) {
        value = runCatching {
            val bitmap = context.loadBitmap(avatar) { size(128) } ?: return@runCatching null
            withContext(Dispatchers.Default) {
                // The colour covering most of the avatar among those that are colourful at all
                val palette = Palette.from(bitmap).generate()
                palette.swatches
                    .filter { it.hsl[1] >= 0.3f && it.hsl[2] in 0.15f..0.85f }
                    .maxByOrNull { it.population }?.rgb
                    ?: palette.dominantSwatch?.rgb
            }
        }.getOrNull()
    }
    return remember(swatch, night) { swatch?.let { toned(it, night) } ?: Color.Unspecified }
}

/** The page's colour where there is one, the theme's primary colour otherwise. */
@Composable
fun pageAccentOrPrimary(): Color = LocalPageAccent.current.takeOrElse { ExtendedTheme.colors.emphasis }

/** Text and icons on [pageAccentOrPrimary]. */
@Composable
fun onPageAccent(): Color = when {
    LocalPageAccent.current.isUnspecified -> ExtendedTheme.colors.onAccent
    ExtendedTheme.colors.isNightMode -> Color.Black
    else -> Color.White
}

/**
 * The swatch brought to a lightness that carries white text in the light theme and black text in
 * the dark one; a nearly grey avatar has no colour worth using.
 */
private fun toned(rgb: Int, night: Boolean): Color? {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(rgb, hsl)
    if (hsl[1] < 0.2f) return null
    hsl[1] = hsl[1].coerceIn(0.35f, 0.75f)
    hsl[2] = if (night) 0.66f else 0.38f
    return Color(ColorUtils.HSLToColor(hsl))
}
