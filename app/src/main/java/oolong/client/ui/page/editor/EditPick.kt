package oolong.client.ui.page.editor

import android.os.Build
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
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
import oolong.client.ui.common.theme.compose.Motion

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
    var prepared by remember(state.renderer) { mutableStateOf<AutoMask.Prepared?>(null) }
    LaunchedEffect(state.maskPicking, download) {
        if (!state.maskPicking || download != AutoMaskDownload.Ready || prepared != null) return@LaunchedEffect
        val engine = files.engine() ?: return@LaunchedEffect
        val picture = state.renderer.picture
        val whole = Rect(0f, 0f, picture.width.toFloat(), picture.height.toFloat())
        try {
            val lines = withContext(Dispatchers.Default) { engine.detect(picture) }
            state.pickLines = lines.map { Rect(it.left, it.top, it.right, it.bottom).intersect(whole) }
            prepared = withContext(Dispatchers.Default) { engine.prepare(picture, lines) }
            state.pickReady = true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Left to boxing by hand, as before it was chosen
            Log.w("TapToHide", "could not look the picture over", e)
            state.pickLines = null
            state.maskPicking = false
            state.maskShape = MaskShape.Rect
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.pendingPick }.filterNotNull().collect { at ->
            state.pendingPick = null
            val ready = prepared ?: return@collect
            val engine = files.engine() ?: return@collect
            val point = state.toPicture(state.toTurned(at))
            val found = try {
                withContext(Dispatchers.Default) { engine.tap(ready, point.x, point.y) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("TapToHide", "could not look into a tap", e)
                return@collect
            }
            val box = found.box
            if (box != null) {
                state.hidePicked(at, Rect(box.left, box.top, box.right, box.bottom))
            } else {
                state.missedPick(at)
                view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }
}

/**
 * What tap-to-hide shows on the picture. While it looks the picture over, it dims, a band of light
 * sweeps down it, and its lines of text light up one after another as they are found; all of it
 * goes once the picture is ready. A tap's box grows out of the finger into what it hides; a tap
 * that found nothing rings out and fades.
 */
@Stable
internal class PickEffect(
    private val sweep: State<Float>,
    /** 1 while looking, to 0 once ready. */
    val looking: Animatable<Float, *>,
    /** From 0 as the lines are found, to 1 once all have lit. */
    val lines: Animatable<Float, *>,
    /** From 0 as a tap's box grows, to 1 once it has faded. */
    val tap: Animatable<Float, *>,
) {
    fun draw(scope: DrawScope, state: EditorState) = with(scope) {
        val w = state.renderer.size.width
        val h = state.renderer.size.height
        val look = looking.value
        if (look > 0f) {
            val corners = listOf(Offset.Zero, Offset(w, 0f), Offset(w, h), Offset(0f, h)).map(state::pictureToScreen)
            val outline = Path().apply {
                moveTo(corners[0].x, corners[0].y)
                corners.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            val bounds = Rect(corners.minOf { it.x }, corners.minOf { it.y }, corners.maxOf { it.x }, corners.maxOf { it.y })
            clipPath(outline) {
                drawRect(Color.Black.copy(alpha = 0.32f * look))
                // A soft band of light, from above the picture to below it
                val band = bounds.height * 0.18f
                val y = bounds.top - band + (bounds.height + 2 * band) * sweep.value
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.5f to Color.White.copy(alpha = 0.26f * look),
                        1f to Color.Transparent,
                        startY = y - band,
                        endY = y + band,
                    ),
                    topLeft = Offset(bounds.left, y - band),
                    size = androidx.compose.ui.geometry.Size(bounds.width, 2 * band),
                )
            }
            // Lines light up from the top down, each over a short while
            state.pickLines?.forEach { line ->
                val lit = ((lines.value * 1.4f - line.top / h * 0.4f) / 1f).coerceIn(0f, 1f)
                if (lit <= 0f) return@forEach
                val path = boxPath(state, line, 3.dp.toPx() / state.scale)
                drawPath(path, Color.White.copy(alpha = 0.12f * lit * look), style = Stroke(6.dp.toPx(), join = StrokeJoin.Round))
                drawPath(path, Color.White.copy(alpha = 0.75f * lit * look), style = Stroke(1.2.dp.toPx(), join = StrokeJoin.Round))
            }
        }
        val picked = state.picked ?: return@with
        val t = tap.value
        if (t >= 1f) return@with
        val box = picked.box
        if (box != null) {
            // Grows out of the finger over the first half, then fades
            val grow = (t * 2f).coerceAtMost(1f)
            val fade = 1f - ((t - 0.5f) * 2f).coerceIn(0f, 1f)
            val target = boxCorners(state, box)
            val path = Path().apply {
                target.map { lerp(picked.at, it, grow) }.let { c ->
                    moveTo(c[0].x, c[0].y)
                    c.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
            }
            // A dark halo under the light line, so it shows on a light picture as on a dark one
            drawPath(path, Color.Black.copy(alpha = 0.3f * fade), style = Stroke(6.dp.toPx(), join = StrokeJoin.Round))
            drawPath(path, Color.White.copy(alpha = 0.95f * fade), style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
        } else {
            val radius = 28.dp.toPx() * t
            drawCircle(Color.Black.copy(alpha = 0.3f * (1f - t)), radius = radius, center = picked.at, style = Stroke(5.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.9f * (1f - t)), radius = radius, center = picked.at, style = Stroke(2.dp.toPx()))
        }
    }

    private fun boxCorners(state: EditorState, box: Rect) =
        listOf(box.topLeft, box.topRight, box.bottomRight, box.bottomLeft).map(state::pictureToScreen)

    private fun boxPath(state: EditorState, box: Rect, pad: Float) = Path().apply {
        val c = boxCorners(state, box.inflate(pad))
        moveTo(c[0].x, c[0].y)
        c.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
}

@Composable
internal fun rememberPickEffect(state: EditorState, filesReady: Boolean): PickEffect {
    val sweep = rememberInfiniteTransition(label = "pickSweep").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "pickSweep",
    )
    val effect = remember { PickEffect(sweep, Animatable(0f), Animatable(0f), Animatable(1f)) }
    // Looking while picking, its files here and the picture not ready yet
    val looking = state.tool == EditorTool.Mask && state.maskPicking && filesReady && !state.pickReady
    LaunchedEffect(looking) {
        if (looking) effect.looking.animateTo(1f, Motion.snap()) else effect.looking.animateTo(0f, tween(400))
    }
    LaunchedEffect(state.pickLines) {
        if (state.pickLines != null) effect.lines.animateTo(1f, tween(900, easing = LinearEasing))
    }
    LaunchedEffect(state.picked) {
        if (state.picked == null) return@LaunchedEffect
        effect.tap.snapTo(0f)
        launch { effect.tap.animateTo(1f, tween(if (state.picked?.box != null) 700 else 450)) }
    }
    return effect
}
