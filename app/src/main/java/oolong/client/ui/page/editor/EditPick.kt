package oolong.client.ui.page.editor

import android.os.Build
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
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
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import oolong.client.components.automask.AutoMask
import oolong.client.components.automask.AutoMaskDownload
import oolong.client.components.automask.AutoMaskFiles
import oolong.client.ui.widgets.compose.AuroraState
import oolong.client.ui.widgets.compose.auroraColor
import oolong.client.ui.widgets.compose.drawAuroraLight
import kotlin.math.sign

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
    // One look into the picture at a time: aiming and tapping share the decoder
    val lock = remember { Mutex() }
    suspend fun look(ready: AutoMask.Prepared, point: Offset): Rect? {
        val engine = files.engine() ?: return null
        // Near enough is a fingertip on screen, however far the picture is zoomed out
        val reach = with(density) { TextReach.toPx() } / state.scale
        val found = lock.withLock { withContext(Dispatchers.Default) { engine.tap(ready, point.x, point.y, reach) } }
        return found.box?.let { Rect(it.left, it.top, it.right, it.bottom) }
    }
    // What the finger would hide, looked into again as it moves or the picture moves under it
    LaunchedEffect(state, prepared) {
        val ready = prepared ?: return@LaunchedEffect
        snapshotFlow { state.aim?.let { it to state.toPicture(state.toTurned(it)) } }.filterNotNull().conflate().collect { (at, point) ->
            val removing = state.tappedMaskAt(at)
            state.aimed = if (removing != null) {
                EditorState.Aimed(point, null, removing)
            } else {
                val box = try {
                    look(ready, point)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w("TapToHide", "could not look where the finger aims", e)
                    return@collect
                }
                val same = box?.let(state::tappedMaskOver)
                EditorState.Aimed(point, box.takeIf { same == null }, same)
            }
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.pendingPick }.filterNotNull().collect { pick ->
            state.pendingPick = null
            val at = pick.at
            val point = state.toPicture(state.toTurned(at))
            // Held, it does what it showed, once that is for where the finger let go
            val aimed = state.aimed?.takeIf { pick.held && (it.point - point).getDistance() < 1f }
            try {
                when {
                    aimed?.removing != null -> state.unhidePicked(at, aimed.removing, held = true)
                    aimed?.box != null -> state.hidePicked(at, aimed.box, held = true)
                    aimed != null -> missed(state, view, at)
                    else -> {
                        // A tap on a mask a tap laid takes it away again
                        state.tappedMaskAt(at)?.let { mask ->
                            state.unhidePicked(at, mask, pick.held)
                            return@collect
                        }
                        val ready = prepared ?: return@collect
                        val rect = try {
                            look(ready, point)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Log.w("TapToHide", "could not look into a tap", e)
                            return@collect
                        }
                        if (rect != null) {
                            // Found where a tap hid already: taken away, rather than hidden twice over
                            val same = state.tappedMaskOver(rect)
                            if (same != null) state.unhidePicked(at, same, pick.held) else state.hidePicked(at, rect, pick.held)
                        } else {
                            missed(state, view, at)
                        }
                    }
                }
            } finally {
                state.stopAiming()
            }
        }
    }
}

private fun missed(state: EditorState, view: View, at: Offset) {
    state.missedPick(at)
    view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
}

/**
 * What tap-to-hide shows on the picture. While it looks the picture over, the aurora's light
 * spreads over it from the middle, drifting quicker than on the landing page, and gathers back
 * and fades once the picture is ready. A tap's mask spreads out of the finger to fill its box,
 * a faint rim of the same light at its edge; a tap that found nothing gives off a little glow.
 * A finger held shows what it would hide, lit, and a loupe over the finger to aim with.
 */
@Stable
internal class PickEffect(
    val aurora: AuroraState,
    /** 1 while looking, to 0 once ready. */
    val looking: Animatable<Float, *>,
    /** From 0 as a tap's mask spreads, to 1 once it fills its box and the rim has faded. */
    val tap: Animatable<Float, *>,
    /** 1 while a finger aims, to 0 once it is done. */
    val hold: Animatable<Float, *>,
    /** Which side of the finger the loupe is on: 0 above, -1 or 1 to the left or right. */
    val side: Animatable<Float, *>,
) {
    /** Where the finger last aimed, on screen, kept for the loupe to fade out at. */
    var lastAim by mutableStateOf<Offset?>(null)

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
        // Held, it was shown already
        if (picked.box == null || picked.held || tap.value >= SpreadEnd) return null
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
            if (picked.held) return@with
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

    /**
     * What the aiming finger would do, as [draw] draws the picture with a mask added or one left
     * out; null when the picture is drawn as it is.
     */
    fun aimedEdits(state: EditorState): Pair<Edit?, Int?>? {
        val aimed = state.aimed?.takeIf { state.aiming } ?: return null
        return when {
            aimed.removing != null -> null to aimed.removing.id
            aimed.box != null -> state.pickedMask(-1, aimed.box) to null
            else -> null
        }
    }

    /** A soft light over what the finger would hide, fading as the finger lets go. */
    fun drawAimed(scope: DrawScope, state: EditorState) = with(scope) {
        val h = hold.value
        val aimed = state.aimed ?: return@with
        val box = aimed.box ?: aimed.removing?.let { mask -> Rect(mask.points.first(), mask.points.last()) } ?: return@with
        if (h <= 0f) return@with
        clipPath(polygon(boxCorners(state, box))) {
            drawRect(auroraColor(1).copy(alpha = 0.22f * h), blendMode = BlendMode.Plus)
        }
    }

    /**
     * The loupe: [draw] drawn larger around the finger, in a circle above it, or beside it when
     * there is no room above.
     */
    fun drawLoupe(scope: DrawScope, state: EditorState, draw: DrawScope.() -> Unit) = with(scope) {
        val h = hold.value
        val at = state.aim ?: lastAim ?: return@with
        if (h <= 0f) return@with
        val r = LoupeSize.toPx() / 2
        val above = Offset(at.x, at.y - r - LoupeLift.toPx())
        val beside = Offset(at.x + side.value.sign * (r + LoupeAside.toPx()), at.y)
        val s = kotlin.math.abs(side.value)
        val c = Offset(
            (above.x + (beside.x - above.x) * s).coerceIn(state.area.left + r, state.area.right - r),
            above.y + (beside.y - above.y) * s,
        )
        // Grows out of the finger and back into it
        val center = at + (c - at) * h
        val radius = r * (0.4f + 0.6f * h)
        val k = LoupeZoom * radius / r
        drawCircle(
            Brush.radialGradient(0.8f to Color.Black.copy(alpha = 0.35f * h), 1f to Color.Transparent, center = center, radius = radius * 1.25f),
            radius = radius * 1.25f,
            center = center,
        )
        val circle = Path().apply { addOval(Rect(center, radius)) }
        clipPath(circle) {
            drawRect(LoupeBackground)
            withTransform({
                translate(center.x - at.x, center.y - at.y)
                scale(k, k, pivot = at)
            }) { draw() }
            // Where the finger aims: a small dot with a dark rim, seen on light and dark alike
            drawCircle(Color.Black.copy(alpha = 0.45f), radius = 3.5.dp.toPx(), center = center)
            drawCircle(Color.White, radius = 2.5.dp.toPx(), center = center)
        }
        drawCircle(Color.White.copy(alpha = 0.35f * h), radius = radius, center = center, style = Stroke(1.dp.toPx()))
    }

    /** Which side the loupe goes for a finger [at] a point: above, unless that leaves the picture's room. */
    fun sideFor(state: EditorState, at: Offset, density: Float): Float {
        val r = LoupeSize.value * density / 2
        if (at.y - LoupeLift.value * density - 2 * r >= state.area.top) return 0f
        // Away from the nearer edge, staying put while there is room
        val room = (r * 2 + LoupeAside.value * density)
        val current = side.targetValue
        return when {
            current < 0f && at.x - room >= state.area.left -> -1f
            current > 0f && at.x + room <= state.area.right -> 1f
            at.x - state.area.left > state.area.right - at.x -> -1f
            else -> 1f
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

        /** The loupe across, how far above the finger it floats, how far beside when not above. */
        private val LoupeSize = 128.dp
        private val LoupeLift = 40.dp
        private val LoupeAside = 36.dp

        /** How much larger than on screen the loupe shows the picture. */
        private const val LoupeZoom = 2.5f

        private val LoupeBackground = Color(0xFF111111)

        /** Quick out of the finger, settling as it reaches the box's far corner. */
        private fun spreadEasing(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)
    }
}

@Composable
internal fun rememberPickEffect(state: EditorState, filesReady: Boolean): PickEffect {
    val effect = remember { PickEffect(AuroraState(spread = 0f), Animatable(0f), Animatable(1f), Animatable(0f), Animatable(0f)) }
    val density = LocalDensity.current.density
    LaunchedEffect(state.aiming) {
        if (state.aiming) {
            effect.hold.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
        } else {
            effect.hold.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
        }
    }
    // Follows the finger, moving the loupe to the side, smoothly, when it runs out of room above
    LaunchedEffect(state) {
        snapshotFlow { state.aim }.filterNotNull().collect { at ->
            effect.lastAim = at
            val side = effect.sideFor(state, at, density)
            if (side != effect.side.targetValue) {
                launch {
                    // Straight to the other side, not through above, when it swaps sides
                    if (side * effect.side.value < 0f) effect.side.snapTo(side) else effect.side.animateTo(side, tween(200, easing = FastOutSlowInEasing))
                }
            }
        }
    }
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
