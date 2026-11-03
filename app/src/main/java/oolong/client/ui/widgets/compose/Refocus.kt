package oolong.client.ui.widgets.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import oolong.client.ui.common.theme.compose.Motion

/** How blurred content is as it leaves, and as it starts to come into focus. */
private val RefocusBlur = 6.dp

/**
 * Shows [targetState], swapping it in place by refocusing: the old content blurs and fades away,
 * the new one comes from blurred and a little smaller into focus. Blur is drawn from Android 12;
 * before, the content only fades and grows.
 */
@Composable
fun <T> Refocus(
    targetState: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        contentAlignment = Alignment.Center,
        transitionSpec = {
            (fadeIn(Motion.fadeIn()) + scaleIn(Motion.settle(), initialScale = 0.92f)) togetherWith
                fadeOut(Motion.fadeOut()) using SizeTransform(clip = false) { _, _ -> Motion.settle() }
        },
        label = "Refocus",
    ) { state ->
        val blur by transition.animateFloat(transitionSpec = { Motion.settle() }, label = "RefocusBlur") {
            if (it == EnterExitState.Visible) 0f else 1f
        }
        Box(modifier = Modifier.blurred({ blur })) { content(state) }
    }
}

/**
 * Blurs the content by [amount] of [RefocusBlur], drawing it with room around it for the blur to
 * spread into instead of being cut off square; the room takes no space in the layout.
 */
private fun Modifier.blurred(amount: () -> Float): Modifier = this
    .layout { measurable, constraints ->
        val room = (RefocusBlur * 2).roundToPx()
        val placeable = measurable.measure(constraints.offset(room * 2, room * 2))
        layout(
            constraints.constrainWidth(placeable.width - room * 2),
            constraints.constrainHeight(placeable.height - room * 2),
        ) { placeable.place(-room, -room) }
    }
    .graphicsLayer {
        val radius = amount() * RefocusBlur.toPx()
        renderEffect = if (radius > 0.5f) BlurEffect(radius, radius, TileMode.Decal) else null
    }
    .padding(RefocusBlur * 2)
