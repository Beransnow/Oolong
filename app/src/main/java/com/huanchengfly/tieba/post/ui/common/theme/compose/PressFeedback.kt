package com.huanchengfly.tieba.post.ui.common.theme.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material.LocalContentColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How pressed things respond, in place of Material's ripple (design-system.md §8.1): a veil of
 * the content colour, and for controls that stand on their own a slight shrink.
 */
object PressFeedback {
    /** Full-width rows: the veil only. The app's default indication. */
    val Overlay: IndicationNodeFactory = PressIndication(shrinks = false)

    /** Buttons, chips, icon buttons, cards: shrink and veil. */
    val Scale: IndicationNodeFactory = PressIndication(shrinks = true)
}

/**
 * How far a pressed control's edges move in, the same for every size so small and large controls
 * look equally pressed: an icon button shrinks to about 0.9, a capsule button to 0.95, a card to
 * 0.985.
 */
private val PressedInset = 2.5.dp

private const val MinPressedScale = 0.9f
private const val MaxPressedScale = 0.985f

/** The veil's opacity at full press, of the content colour. */
private const val PressedOverlayAlpha = 0.08f

/** How long a tap shows its press before letting go, about 80% of the way in. */
private const val MinPressMillis = 100L

private class PressIndication(private val shrinks: Boolean) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        PressNode(interactionSource, shrinks)

    override fun equals(other: Any?): Boolean = other is PressIndication && other.shrinks == shrinks

    override fun hashCode(): Int = shrinks.hashCode()
}

private class PressNode(
    private val interactionSource: InteractionSource,
    private val shrinks: Boolean,
) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    /** 0 at rest, 1 fully pressed; the release spring may swing a little past 0. */
    private val progress = Animatable(0f, visibilityThreshold = Motion.ProgressThreshold)

    override fun onAttach() {
        coroutineScope.launch {
            var pressing: Job? = null
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        pressing = launch { progress.animateTo(1f, Motion.press()) { invalidateDraw() } }
                    }

                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        val press = pressing
                        launch {
                            // A quick tap still shows most of the press before letting go
                            withTimeoutOrNull(MinPressMillis) { press?.join() }
                            progress.animateTo(0f, Motion.release()) { invalidateDraw() }
                        }
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val p = progress.value
        if (p == 0f) {
            drawContent()
            return
        }
        val pressedScale = if (shrinks) {
            (1f - 2 * PressedInset.toPx() / size.maxDimension).coerceIn(MinPressedScale, MaxPressedScale)
        } else {
            1f
        }
        val s = 1f - (1f - pressedScale) * p
        scale(s) {
            this@draw.drawContent()
            val alpha = (PressedOverlayAlpha * p).coerceIn(0f, PressedOverlayAlpha)
            if (alpha > 0f) drawRect(currentValueOf(LocalContentColor).copy(alpha = alpha))
        }
    }
}
