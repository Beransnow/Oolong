package oolong.client.ui.widgets.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import oolong.client.ui.common.theme.compose.Corners
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Motion
import oolong.client.ui.common.theme.compose.SmoothCornerShape
import kotlin.math.roundToInt

/**
 * A switch (design-system.md §9.1): a filled capsule track with a white pill knob. Held, the knob
 * grows a little past the track and turns to clear glass; it can be dragged across or tapped.
 *
 * It adds no touch area of its own: it sits in a row that toggles it when tapped.
 */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val dragged by source.collectIsDraggedAsState()
    // 0 off, 1 on: follows the finger while dragged; lifts into a lens while held or moved
    val knob = rememberElasticPress(
        initialValue = if (checked) 1f else 0f,
        valueRange = 0f..1f,
        pressedScale = HeldScale,
        velocityScale = KnobVelocityScale,
    )
    val isHeld = enabled && (pressed || dragged)
    LaunchedEffect(isHeld) {
        if (isHeld) knob.press() else knob.release()
    }

    val haptics = LocalHapticFeedback.current
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    val change: (Boolean) -> Unit = { value ->
        haptics.performHapticFeedback(if (value) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        currentOnCheckedChange?.invoke(value)
    }

    var dragging by remember { mutableStateOf(false) }
    // Toggled from anywhere, as by its row, the knob lifts, crosses and settles
    LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        if (!dragging && knob.targetValue != target) knob.moveTo(target)
    }
    // Where the finger has taken the knob, which it follows through a stiff spring
    var reach by remember { mutableFloatStateOf(0f) }
    val travelPx = with(LocalDensity.current) { KnobTravel.toPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val dragState = rememberDraggableState { delta ->
        val d = if (rtl) -delta else delta
        reach = (reach + d / travelPx).coerceIn(0f, 1f)
        knob.follow(reach)
    }

    val colors = ExtendedTheme.colors
    val offTrack = colors.text.copy(alpha = if (colors.isNightMode) 0.22f else 0.12f)
    // The track shows the state, not where the knob is dragged to
    val trackOn by animateFloatAsState(if (checked) 1f else 0f, Motion.snap(), label = "track")
    val track = lerp(offTrack, colors.emphasis, trackOn)
    val glassState = rememberHazeState()
    val glassAvailable = isGlassAvailable()

    Box(
        modifier
            .then(
                if (onCheckedChange != null) {
                    Modifier
                        .toggleable(
                            value = checked,
                            onValueChange = change,
                            enabled = enabled,
                            role = Role.Switch,
                            interactionSource = source,
                            indication = null,
                        )
                        .draggable(
                            state = dragState,
                            orientation = Orientation.Horizontal,
                            enabled = enabled,
                            interactionSource = source,
                            onDragStarted = {
                                reach = knob.targetValue
                                dragging = true
                            },
                            onDragStopped = {
                                val on = knob.targetValue >= 0.5f
                                dragging = false
                                knob.moveTo(if (on) 1f else 0f)
                                if (on != checked) change(on)
                            },
                        )
                } else {
                    Modifier
                }
            )
            .size(TrackWidth, TrackHeight)
            .graphicsLayer { alpha = if (enabled) 1f else DisabledAlpha },
        contentAlignment = Alignment.CenterStart,
    ) {
        // What the held knob sees: the track, and clear space around it as far as the knob reaches
        Box(
            Modifier
                .requiredSize(TrackWidth + LensReachWidth, TrackHeight + LensReachHeight)
                .hazeSource(glassState),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(TrackWidth, TrackHeight)
                    // Filled in its shape rather than clipped: a clip's edge is anti-aliased coarsely, or not at all, on some GPUs
                    .background(track, Corners.full)
            )
        }
        // Swells a little apart on each axis, so it wobbles, and is drawn out along the way it
        // moves when moved fast. Sized by layout, so the glass sees all it covers.
        val held = knob.pressProgress
        val knobWidth = KnobWidth * knob.scaleX * knob.stretchX
        val knobHeight = KnobHeight * knob.scaleY * knob.stretchY
        val p = if (rtl) 1f - knob.value else knob.value
        val knobShape = SmoothCornerShape.Capsule
        Box(
            Modifier
                .offset {
                    val centre = KnobInset.toPx() + KnobTravel.toPx() * p + KnobWidth.toPx() / 2
                    IntOffset((centre - knobWidth.toPx() / 2).roundToInt(), 0)
                }
                .requiredSize(knobWidth, knobHeight)
                .shadow(KnobElevation, knobShape, clip = false, ambientColor = KnobAmbient, spotColor = KnobSpot)
                // The white knob fades out as the glass fades in, where glass is drawn
                .background(Color.White.copy(alpha = if (glassAvailable) 1f - held else 1f), knobShape)
                .controlLens(glassState, knobShape, held)
        )
    }
}

private val TrackWidth = 50.dp
private val TrackHeight = 26.dp
private val KnobWidth = 30.dp
private val KnobHeight = 20.dp
private val KnobInset = (TrackHeight - KnobHeight) / 2
private val KnobTravel = TrackWidth - KnobWidth - KnobInset * 2

/** How much the knob grows while held, standing out past the track like the navigation lens. */
private const val HeldScale = 1.5f

/** Travels a second at which the knob is drawn out the most. */
private const val KnobVelocityScale = 50f

/** How far past the track the held knob can reach, grown and drawn out, for its glass to see. */
private val LensReachWidth = 30.dp
private val LensReachHeight = 18.dp

private val KnobElevation = 2.dp
private val KnobAmbient = Color.Black.copy(alpha = 0.12f)
private val KnobSpot = Color.Black.copy(alpha = 0.24f)

private const val DisabledAlpha = 0.38f
