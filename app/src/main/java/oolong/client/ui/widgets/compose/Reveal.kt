package oolong.client.ui.widgets.compose

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import oolong.client.ui.common.theme.compose.LocalReduceMotion
import oolong.client.ui.common.theme.compose.Motion
import kotlin.math.roundToInt

private val RevealBlur = 10.dp

/**
 * Shows [value]'s content in place while it is not null (design-system.md §7.2): the space opens
 * on `settle` and the content condenses in from a blur; going, the space closes on `dismiss` and
 * the content blurs away, still showing the last value. A value with another [contentKey] while
 * shown fades over in place; by default the content just takes the new value, as a list does. With motion reduced the content only
 * fades; the space still opens and closes, so what follows moves rather than jumps.
 */
@Composable
fun <T : Any> Reveal(
    value: T?,
    modifier: Modifier = Modifier,
    contentKey: (T) -> Any? = { Unit },
    content: @Composable (T) -> Unit,
) {
    var shown by remember { mutableStateOf(value) }
    if (value != null) shown = value
    val progress = remember { Animatable(if (value != null) 1f else 0f, Motion.ProgressThreshold) }
    LaunchedEffect(value != null) {
        if (value != null) {
            progress.animateTo(1f, Motion.settle(Motion.ProgressThreshold))
        } else {
            progress.animateTo(0f, Motion.dismiss(Motion.ProgressThreshold))
            shown = null
        }
    }
    val last = shown ?: return
    val reduced = LocalReduceMotion.current
    Layout(
        modifier = modifier.clipToBounds(),
        content = {
            Box(
                modifier = Modifier
                    .wrapContentHeight(unbounded = true)
                    .graphicsLayer {
                        val f = progress.value.coerceIn(0f, 1f)
                        alpha = ((f - 0.3f) / 0.7f).coerceIn(0f, 1f)
                        if (!reduced) {
                            val scale = 0.96f + 0.04f * f
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            val radius = RevealBlur.toPx() * (1f - f)
                            renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && radius > 0.5f) BlurEffect(radius, radius) else null
                        }
                    },
            ) {
                // Another value while shown fades over in place, the space easing to its size
                AnimatedContent(
                    targetState = last,
                    transitionSpec = { fadeIn(Motion.fadeIn()) togetherWith fadeOut(Motion.fadeOut()) using SizeTransform(clip = false) { _, _ -> Motion.settle() } },
                    contentKey = contentKey,
                    label = "RevealValue",
                ) { content(it) }
            }
        },
    ) { measurables, constraints ->
        val placeable = measurables.single().measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        val height = (placeable.height * progress.value.coerceIn(0f, 1f)).roundToInt()
        layout(placeable.width, height) { placeable.place(0, 0) }
    }
}
