package oolong.migration

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import oolong.client.ui.common.theme.compose.Corners
import oolong.client.ui.common.theme.compose.PressFeedback
import oolong.client.ui.common.theme.compose.pressScale

/**
 * The app's default look (深海蓝, the plain colour style, the quiet blue night) for the few
 * colours these two screens need; the shapes, type and motion come from :core:ui.
 */
@Immutable
data class HelperColors(
    val night: Boolean,
    val page: Color,
    val sheet: Color,
    val text: Color,
    val textSecondary: Color,
    val emphasis: Color,
    val onEmphasis: Color,
    val scrim: Color,
    val glassEdge: Color,
)

@Composable
fun helperColors(): HelperColors = if (isSystemInDarkTheme()) {
    HelperColors(
        night = true,
        page = Color(0xFF17212B),
        sheet = Color(0xFF17212B),
        text = Color(0xDEEEEEEE),
        textSecondary = Color(0x99EEEEEE),
        emphasis = Color(0xFFEEEEEE),
        onEmphasis = Color(0xFF17212B),
        scrim = Color.Black.copy(alpha = .40f),
        glassEdge = Color.White.copy(alpha = .10f),
    )
} else {
    HelperColors(
        night = false,
        page = Color.White,
        sheet = Color.White,
        text = Color(0xDE000000),
        textSecondary = Color(0x99000000),
        emphasis = Color.Black,
        onEmphasis = Color.White,
        scrim = Color.Black.copy(alpha = .28f),
        glassEdge = Color.Black.copy(alpha = .06f),
    )
}

/**
 * A button as the app's dialogs have them: a capsule, filled with the emphasis colour (primary)
 * or tinted a tenth of the text colour, its edges drawing in when pressed.
 */
@Composable
fun Capsule(
    text: String,
    primary: Boolean,
    colors: HelperColors,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val content = if (primary) colors.onEmphasis else colors.text
    CompositionLocalProvider(LocalContentColor provides content) {
        Box(
            modifier = modifier
                .pressScale(source)
                .clip(Corners.full)
                .background(if (primary) colors.emphasis else colors.text.copy(alpha = .1f))
                .clickable(enabled = enabled, interactionSource = source, indication = PressFeedback.Overlay, onClick = onClick)
                .heightIn(min = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = text,
                style = TextStyle(color = content.copy(alpha = if (enabled) 1f else .5f), fontSize = 16.sp, fontWeight = if (primary) FontWeight.Medium else FontWeight.Normal, textAlign = TextAlign.Center),
            )
        }
    }
}

/** An app icon as a bitmap: adaptive icons are drawables Compose cannot paint by resource. */
fun Drawable.toImageBitmap(size: Int): ImageBitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    setBounds(0, 0, size, size)
    draw(Canvas(bitmap))
    return bitmap.asImageBitmap()
}
