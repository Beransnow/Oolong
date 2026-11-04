package oolong.client.ui.page.editor

import android.os.Build
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import oolong.client.components.automask.AutoMask
import oolong.client.components.automask.AutoMaskDownload
import oolong.client.components.automask.AutoMaskFiles
import oolong.client.ui.widgets.compose.AuroraState
import oolong.client.ui.widgets.compose.auroraColor
import oolong.client.ui.widgets.compose.drawAuroraLight

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface AutoMaskEntry {
    fun autoMaskFiles(): AutoMaskFiles
}

@Composable
internal fun rememberAutoMaskFiles(): AutoMaskFiles {
    val context = LocalContext.current
    return remember { EntryPointAccessors.fromApplication(context.applicationContext, AutoMaskEntry::class.java).autoMaskFiles() }
}

/**
 * Tap-to-hide behind the editor: once it is chosen and its files are here, finds the picture's
 * lines of text, then readies the rest, and looks into each tap.
 */
@Composable
internal fun TapToHide(state: EditorState, files: AutoMaskFiles) {
    val download by files.download.collectAsState()
    val view = LocalView.current
    val density = LocalDensity.current
    var prepared by remember(state.renderer) { mutableStateOf<AutoMask.Prepared?>(null) }
    LaunchedEffect(state.maskPicking, download) {
        if (!state.maskPicking || download != AutoMaskDownload.Ready || prepared != null) return@LaunchedEffect
        val engine = files.engine() ?: return@LaunchedEffect
        val picture = state.renderer.picture
        try {
            prepared = withContext(Dispatchers.Default) { engine.prepare(picture) }
            state.pickReady = true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Left to boxing by hand, as before it was chosen
            Log.w("TapToHide", "could not look the picture over", e)
            state.maskPicking = false
            state.maskShape = MaskShape.Rect
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.pendingPick }.filterNotNull().collect { at ->
            state.pendingPick = null
            // A tap on a mask a tap laid takes it away again
            state.tappedMaskAt(at)?.let { mask ->
                state.unhidePicked(at, mask)
                return@collect
            }
            val ready = prepared ?: return@collect
            val engine = files.engine() ?: return@collect
            val point = state.toPicture(state.toTurned(at))
            // Near enough is a fingertip on screen, however far the picture is zoomed out
            val reach = with(density) { TextReach.toPx() } / state.scale
            val found = try {
                withContext(Dispatchers.Default) { engine.tap(ready, point.x, point.y, reach) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("TapToHide", "could not look into a tap", e)
                return@collect
            }
            val box = found.box
            if (box != null) {
                val rect = Rect(box.left, box.top, box.right, box.bottom)
                // Found where a tap hid already: taken away, rather than hidden twice over
                val same = state.tappedMaskOver(rect)
                if (same != null) state.unhidePicked(at, same) else state.hidePicked(at, rect)
            } else {
                state.missedPick(at)
                view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }
}

/**
 * What tap-to-hide shows on the picture. While it looks the picture over, the aurora's light
 * spreads over it from the middle, drifting quicker than on the landing page, and gathers back
 * and fades once the picture is ready. A tap's mask spreads out of the finger to fill its box,
 * a faint rim of the same light at its edge; a tap that found nothing gives off a little glow.
 */
@Stable
internal class PickEffect(
    val aurora: AuroraState,
    /** 1 while looking, to 0 once ready. */
    val looking: Animatable<Float, *>,
    /** From 0 as a tap's mask spreads, to 1 once it fills its box and the rim has faded. */
    val tap: Animatable<Float, *>,
) {
    /** The light over the picture, while it is looked over. */
    fun drawLooking(scope: DrawScope, state: EditorState) = with(scope) {
        val look = looking.value
        if (look <= 0f) return@with
        val corners = pictureCorners(state)
        clipPath(polygon(corners)) {
            drawRect(Color.Black.copy(alpha = 0.3f * look))
            val bounds = Rect(corners.minOf { it.x }, corners.minOf { it.y }, corners.maxOf { it.x }, corners.maxOf { it.y })
            drawAuroraLight(aurora, bounds, dark = true, pace = LookingPace, sway = 2.2f, fill = true, alpha = look)
        }
    }

    /**
     * The circle a tap's mask shows within, on screen: spreading out of the finger as it is
     * laid, drawing back into it as it is taken away; null once it is done.
     */
    fun spread(state: EditorState): Pair<Offset, Float>? {
        val picked = state.picked ?: return null
        if (picked.box == null || tap.value >= SpreadEnd) return null
        return picked.at to radius(state, picked, tap.value)
    }

    private fun radius(state: EditorState, picked: EditorState.Picked, t: Float): Float {
        val reach = boxCorners(state, picked.box ?: return 0f).maxOf { (it - picked.at).getDistance() }
        val eased = spreadEasing((t / SpreadEnd).coerceIn(0f, 1f))
        return reach * if (picked.removed != null) 1f - eased else eased
    }

    /** The rim of light at the spreading mask's edge, or a tap's glow when it found nothing. */
    fun drawTap(scope: DrawScope, state: EditorState) = with(scope) {
        val picked = state.picked ?: return@with
        val t = tap.value
        if (t >= 1f) return@with
        val glow = auroraColor(1)
        if (picked.box != null) {
            val r = radius(state, picked, t)
            // Bright while spreading, fading once it has filled the box
            val fade = 1f - ((t - SpreadEnd * 0.6f) / (1f - SpreadEnd * 0.6f)).coerceIn(0f, 1f)
            val width = 18.dp.toPx()
            if (r <= 0f || fade <= 0f) return@with
            clipPath(polygon(boxCorners(state, picked.box))) {
                drawCircle(
                    Brush.radialGradient(
                        ((r - width) / r).coerceAtLeast(0f) to Color.Transparent,
                        0.985f to glow.copy(alpha = 0.55f * fade),
                        1f to Color.Transparent,
                        center = picked.at,
                        radius = r,
                    ),
                    radius = r,
                    center = picked.at,
                    blendMode = BlendMode.Plus,
                )
            }
        } else {
            // A small glow that swells a little and goes
            val r = 36.dp.toPx() * (0.5f + 0.5f * spreadEasing(t))
            drawCircle(
                Brush.radialGradient(
                    0f to glow.copy(alpha = 0.6f * (1f - t)),
                    1f to Color.Transparent,
                    center = picked.at,
                    radius = r,
                ),
                radius = r,
                center = picked.at,
                blendMode = BlendMode.Plus,
            )
        }
    }

    private fun pictureCorners(state: EditorState): List<Offset> {
        val w = state.renderer.size.width
        val h = state.renderer.size.height
        return listOf(Offset.Zero, Offset(w, 0f), Offset(w, h), Offset(0f, h)).map(state::pictureToScreen)
    }

    private fun boxCorners(state: EditorState, box: Rect) =
        listOf(box.topLeft, box.topRight, box.bottomRight, box.bottomLeft).map(state::pictureToScreen)

    private fun polygon(corners: List<Offset>) = Path().apply {
        moveTo(corners[0].x, corners[0].y)
        corners.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }

    companion object {
        /** How much quicker the light drifts while the picture is looked over: it is at work. */
        private const val LookingPace = 3f

        /** The part of a tap's time the mask spends spreading; the rim fades over the rest. */
        private const val SpreadEnd = 0.6f

        /** Quick out of the finger, settling as it reaches the box's far corner. */
        private fun spreadEasing(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)
    }
}

@Composable
internal fun rememberPickEffect(state: EditorState, filesReady: Boolean): PickEffect {
    val effect = remember { PickEffect(AuroraState(spread = 0f), Animatable(0f), Animatable(1f)) }
    // Looking while picking, its files here and the picture not ready yet
    val looking = state.tool == EditorTool.Mask && state.maskPicking && filesReady && !state.pickReady
    LaunchedEffect(looking) {
        if (looking) {
            launch { effect.looking.animateTo(1f, tween(300)) }
            effect.aurora.spread.animateTo(1f, tween(1200, easing = FastOutSlowInEasing))
        } else {
            // Gathers back to the middle as it fades, the way it came
            launch { effect.aurora.spread.animateTo(0f, tween(700, easing = FastOutSlowInEasing)) }
            effect.looking.animateTo(0f, tween(700))
        }
    }
    // The light drifts only while it shows
    LaunchedEffect(looking || effect.looking.value > 0f) {
        if (looking || effect.looking.value > 0f) effect.aurora.drift()
    }
    LaunchedEffect(state.picked) {
        if (state.picked == null) return@LaunchedEffect
        effect.tap.snapTo(0f)
        effect.tap.animateTo(1f, tween(if (state.picked?.box != null) 520 else 420, easing = LinearEasing))
    }
    return effect
}

/** How far from a line of text, on screen, a tap still counts as on it. */
private val TextReach = 12.dp
