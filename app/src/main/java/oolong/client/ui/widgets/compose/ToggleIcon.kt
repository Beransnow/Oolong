package oolong.client.ui.widgets.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import oolong.client.ui.common.theme.compose.LocalReduceMotion
import oolong.client.ui.common.theme.compose.Motion

/** How small the [on] icon starts as it comes in. */
private const val FromScale = 0.6f

/**
 * An icon with an on and an off look, such as a like's outlined and filled heart. Turning on,
 * the [on] icon grows in with a little bounce over the [off] one; turning off, it shrinks away
 * without one. Interrupted, it turns back from where it is.
 */
@Composable
fun ToggleIcon(
    checked: Boolean,
    on: ImageVector,
    off: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current.copy(alpha = LocalContentAlpha.current),
) {
    val progress = remember { Animatable(if (checked) 1f else 0f, Motion.ProgressThreshold) }
    LaunchedEffect(checked) {
        progress.animateTo(
            if (checked) 1f else 0f,
            if (checked) Motion.pop(Motion.ProgressThreshold) else Motion.dismiss(Motion.ProgressThreshold),
        )
    }
    // With motion reduced the two only cross-fade
    val reduced = LocalReduceMotion.current
    Box(modifier = modifier) {
        Icon(
            imageVector = off,
            contentDescription = if (checked) null else contentDescription,
            tint = tint,
            modifier = Modifier.graphicsLayer { alpha = (1f - progress.value).coerceIn(0f, 1f) },
        )
        Icon(
            imageVector = on,
            contentDescription = if (checked) contentDescription else null,
            tint = tint,
            modifier = Modifier.graphicsLayer {
                val p = progress.value
                alpha = p.coerceIn(0f, 1f)
                if (!reduced) {
                    val scale = FromScale + (1f - FromScale) * p.coerceAtLeast(0f)
                    scaleX = scale
                    scaleY = scale
                }
            },
        )
    }
}
