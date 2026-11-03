package oolong.client.ui.page.landing

import android.content.Intent
import android.net.Uri
import android.os.Process
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.SPLASH_ICON_MILLIS
import oolong.client.findActivity
import oolong.client.migration.LegacyImport
import oolong.client.toastShort
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.TextStyles
import oolong.client.ui.navigation.LoginKey
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.page.settings.about.LensCenter
import oolong.client.ui.page.settings.about.LensRadius
import oolong.client.ui.page.settings.about.drawLensShadow
import oolong.client.ui.page.settings.about.drawMark
import oolong.client.ui.page.settings.about.drawPaintedLens
import oolong.client.ui.page.settings.about.drawRim
import oolong.client.ui.widgets.compose.Button
import oolong.client.ui.widgets.compose.TextButton
import oolong.client.ui.widgets.compose.controlLens
import oolong.client.ui.widgets.compose.isGlassAvailable
import oolong.client.utils.appPreferences
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class Stage {
    /** First launch: the name, and a way in. */
    Welcome,

    /** The old app's data coming in; the app starts again when it is in. */
    Importing,

    /** Started again after an import. */
    Done,

    Gone,
}

/**
 * The page shown over everything on first launch, and once after an import: the mark and the
 * name come out of slowly moving light, then the way in. Leaving it fades it away over the home
 * page, which is already there under it.
 */
@Composable
fun LandingHost() {
    val context = LocalContext.current
    var stage by rememberSaveable {
        mutableStateOf(
            when {
                LegacyImport.consumeImported(context) -> Stage.Done
                !context.appPreferences.landingShown -> Stage.Welcome
                else -> Stage.Gone
            }
        )
    }
    if (stage == Stage.Gone) return
    Landing(stage = stage, onStage = { stage = it })
}

@Composable
private fun Landing(stage: Stage, onStage: (Stage) -> Unit) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val dark = ExtendedTheme.colors.isNightMode
    val glass = isGlassAvailable()
    val state = remember { LandingState() }
    val page = rememberHazeState()

    var source by remember { mutableStateOf(LegacyImport.source(context)) }
    // Back from the system's uninstall screen, the old app may be gone
    LifecycleResumeEffect(Unit) {
        source = LegacyImport.source(context)
        onPauseOrDispose {}
    }

    fun leave(then: () -> Unit = {}) {
        if (state.leaving) return
        state.leaving = true
        then()
        scope.launch {
            state.leave.animateTo(1f, Leave)
            context.appPreferences.landingShown = true
            onStage(Stage.Gone)
        }
    }

    val export = rememberLauncherForActivityResult(LegacyImport.Export) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        onStage(Stage.Importing)
        scope.launch {
            val ok = state.import { LegacyImport.receive(context, uri) }
            val activity = context.findActivity()
            if (ok && activity != null) {
                LegacyImport.restart(activity)
            } else {
                context.toastShort(R.string.toast_import_legacy_failed)
                onStage(Stage.Welcome)
                state.enter(this, waitForSplash = false, quick = true)
            }
        }
    }

    LaunchedEffect(Unit) {
        // A stage that comes back with the activity is already shown
        if (stage == Stage.Importing) return@LaunchedEffect
        state.enter(this, waitForSplash = true, quick = stage == Stage.Done)
    }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = (now - last) / 1e9f
                last = now
                state.time += dt
                state.pull *= exp(-dt * 2.4f)
            }
        }
    }

    val text = ExtendedTheme.colors.text
    val textSecondary = ExtendedTheme.colors.textSecondary
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - state.leave.value }
            // Nothing reaches the page under it; a finger draws the light a little towards it
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach {
                            state.finger = it.position
                            state.pull = 1f
                            it.consume()
                        }
                    }
                }
            },
    ) {
        Box(Modifier.fillMaxSize().hazeSource(page)) {
            Aurora(state, dark, Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                Canvas(
                    modifier = Modifier
                        .size(MarkSize)
                        .onGloballyPositioned { state.mark = it }
                        .condense({ state.paper.value }, blur = 28.dp, scale = .96f)
                        // The icon floats a little above the light, its shadow tinted with its own blue
                        .shadow(
                            elevation = 28.dp,
                            shape = CircleShape,
                            ambientColor = if (dark) Color.Black.copy(alpha = .5f) else MarkShadow.copy(alpha = .45f),
                            spotColor = if (dark) Color.Black.copy(alpha = .7f) else MarkShadow.copy(alpha = .8f),
                        ),
                ) {
                    // The launcher icon without its lens, which is drawn over it as glass
                    drawMark(size.width / 100f, withLens = false)
                }
                // At least as tall as the name and the line under it, so the mark stays put between stages
                Box(Modifier.heightIn(min = TextsHeight).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    when (stage) {
                        Stage.Importing -> Text(
                            text = stringResource(R.string.landing_importing),
                            modifier = Modifier.padding(top = 40.dp).condense({ state.progress.value }, blur = 6.dp, rise = 4.dp),
                            style = TextStyles.body.copy(color = textSecondary),
                        )

                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(if (stage == Stage.Done) R.string.landing_imported else R.string.app_name),
                                modifier = Modifier
                                    .padding(top = 32.dp)
                                    .condense({ state.name.value }, blur = 16.dp, rise = 8.dp, fadeFaster = 1.15f),
                                style = if (stage == Stage.Done) DoneTitle.copy(color = text) else Name.copy(color = text),
                            )
                            Text(
                                text = stringResource(if (stage == Stage.Done) R.string.landing_imported_body else R.string.landing_slogan),
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .condense({ state.tag.value }, blur = 8.dp, rise = 6.dp),
                                style = TextStyles.body.copy(color = textSecondary),
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1.4f))
                Column(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                        .condense({ state.actions.value }, blur = 4.dp, rise = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val legacy = source == LegacyImport.Source.Ready
                    when (stage) {
                        Stage.Welcome -> {
                            if (source != LegacyImport.Source.None) {
                                Text(
                                    text = stringResource(if (legacy) R.string.landing_import_hint else R.string.tip_import_legacy_update),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    style = TextStyles.caption.copy(color = textSecondary, textAlign = TextAlign.Center),
                                )
                            }
                            if (legacy) {
                                Action(stringResource(R.string.landing_import), primary = true) { export.launch(Unit) }
                                Action(stringResource(R.string.landing_skip_import), primary = false) { leave() }
                            } else {
                                Action(stringResource(R.string.title_login), primary = true) { leave { navigator.navigate(LoginKey) } }
                                Action(stringResource(R.string.landing_skip_login), primary = false) { leave() }
                            }
                        }

                        Stage.Done -> {
                            Action(stringResource(R.string.landing_start), primary = true) { leave() }
                            if (source != LegacyImport.Source.None) {
                                Action(stringResource(R.string.landing_uninstall_legacy), primary = false) {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", LegacyImport.PACKAGE, null)))
                                    }
                                }
                            } else {
                                Spacer(Modifier.height(ActionHeight))
                            }
                        }

                        else -> Spacer(Modifier.height(ActionHeight * 2 + 6.dp))
                    }
                }
            }
        }
        Lens(state, glass, page)
    }
}

/** The mark's lens, over the light and the paper, which it bends as glass where glass is drawn. */
@Composable
private fun Lens(state: LandingState, glass: Boolean, page: dev.chrisbanes.haze.HazeState) {
    val density = LocalDensity.current
    val root = remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { root.value = it })
    val diameter = MarkSize * (LensRadius * 2 / 100f)
    Box(
        modifier = Modifier
            .offset {
                val mark = state.mark
                val parent = root.value
                if (mark == null || parent == null || !mark.isAttached || !parent.isAttached) return@offset IntOffset.Zero
                val center = parent.localPositionOf(mark, LensCenter * (mark.size.width / 100f))
                val half = with(density) { diameter.toPx() } / 2
                // It settles onto the paper from a little way up and to the right
                val drift = (1f - state.lens.value) * with(density) { 10.dp.toPx() }
                IntOffset((center.x - half + drift).roundToInt(), (center.y - half - drift).roundToInt())
            }
            .size(diameter)
            .condense({ state.lens.value }, blur = 20.dp, fadeFaster = 1.2f)
            .drawBehind { drawLensShadow(size.width / 2) }
            .controlLens(page, CircleShape, alpha = 1f)
            .drawWithContent {
                drawContent()
                val radius = size.width / 2
                val center = Offset(radius, radius)
                // While importing, the glass fills like a cup, white as the paper
                val fill = state.fill.value
                if (fill > 0f) {
                    clipPath(Path().apply { addOval(Rect(center, radius)) }) {
                        val top = size.height * (1f - fill)
                        val wave = sin(state.time * 4f) * radius * .06f
                        val surface = Path().apply {
                            moveTo(0f, top + wave)
                            quadraticTo(size.width / 2, top - wave * 2, size.width, top - wave)
                            lineTo(size.width, size.height)
                            lineTo(0f, size.height)
                            close()
                        }
                        drawPath(surface, Brush.verticalGradient(listOf(Color(0xE6FFFFFF), Color(0xB3DCE7F8)), startY = top, endY = size.height))
                    }
                }
                if (glass) {
                    drawRim(center, radius, strokeWidth = radius * .045f, alpha = 1f)
                } else {
                    drawPaintedLens(radius, 1f)
                }
            },
    )
}

/** A full-width button of the page's two: the way in, filled, and the other, its label only. */
@Composable
private fun Action(text: String, primary: Boolean, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth()
    if (primary) {
        Button(onClick = onClick, modifier = modifier, minHeight = ActionHeight) { Text(text, style = ActionLabel) }
    } else {
        TextButton(onClick = onClick, modifier = modifier, color = ExtendedTheme.colors.text) { Text(text, style = ActionLabel.copy(fontWeight = FontWeight.Normal)) }
    }
}

/** The light behind the page: soft colours drifting slowly, opening out of the middle at first. */
@Composable
private fun Aurora(state: LandingState, dark: Boolean, modifier: Modifier) {
    val palette = if (dark) AuroraDark else AuroraLight
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val t = state.time
        val spread = state.spread.value
        drawRect(palette.background)
        Blobs.forEachIndexed { i, blob ->
            val (color, strength) = palette.blobs[i]
            // Every colour starts at the middle and spreads, as dye does in water
            var x = lerp(.5f, blob.x + sin(t * blob.speed * 1.6f * 2 * PI.toFloat() + blob.phase) * blob.dx, spread) * w
            var y = lerp(.36f, blob.y + cos(t * blob.speed * 1.6f * 5.1f + blob.phase) * blob.dy, spread) * h
            state.finger?.let { finger ->
                val dx = finger.x - x
                val dy = finger.y - y
                val d = hypot(dx, dy) + 1f
                val pull = state.pull * 60.dp.toPx() * exp(-d / 260.dp.toPx())
                x += dx / d * pull
                y += dy / d * pull
            }
            val r = blob.radius * w * lerp(.08f, 1f, spread) * (1f + sin(t * .4f + i) * .06f)
            val a = strength * lerp(.2f, 1f, spread)
            val center = Offset(x, y)
            drawCircle(
                brush = Brush.radialGradient(
                    // Close to a Gaussian, so no edge shows where the colour runs out
                    0f to color.copy(alpha = a),
                    .2f to color.copy(alpha = a * .86f),
                    .4f to color.copy(alpha = a * .58f),
                    .6f to color.copy(alpha = a * .3f),
                    .8f to color.copy(alpha = a * .1f),
                    1f to color.copy(alpha = 0f),
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
                blendMode = if (dark) BlendMode.Plus else BlendMode.SrcOver,
            )
        }
        // The edges stay quiet; fading from the ground's own colour keeps them from turning grey
        val edge = if (dark) Color.Black.copy(alpha = .45f) else palette.background.copy(alpha = .85f)
        drawRect(
            Brush.radialGradient(
                0f to edge.copy(alpha = 0f),
                .25f to edge.copy(alpha = 0f),
                1f to edge,
                center = Offset(w / 2, h * .46f),
                radius = h * .8f,
            ),
        )
    }
}

@Stable
private class LandingState {
    val spread = Animatable(0f)
    val paper = Animatable(0f)
    val lens = Animatable(0f)
    val name = Animatable(0f)
    val tag = Animatable(0f)
    val actions = Animatable(0f)
    val progress = Animatable(0f)
    val fill = Animatable(0f)
    val leave = Animatable(0f)

    var leaving = false
    var time by mutableFloatStateOf(0f)
    var pull by mutableFloatStateOf(0f)
    var finger by mutableStateOf<Offset?>(null)
    var mark: LayoutCoordinates? by mutableStateOf(null)

    /**
     * The entrance: the light opens, the paper comes out of it, the lens settles on it, then the
     * words and the way in. After the splash, so none of it plays under the icon; [quick] when
     * the page was seen a moment ago, as after the restart that ends an import.
     */
    fun enter(scope: CoroutineScope, waitForSplash: Boolean, quick: Boolean) {
        scope.launch {
            if (waitForSplash) {
                delay((Process.getStartUptimeMillis() + SPLASH_ICON_MILLIS - SystemClock.uptimeMillis()).coerceAtLeast(0))
            }
            val steps = if (quick) QuickSteps else Steps
            launch { spread.animateTo(1f, Spread) }
            launch {
                delay(steps[0])
                paper.animateTo(1f, Emerge)
            }
            launch {
                delay(steps[1])
                lens.animateTo(1f, Drift)
            }
            launch {
                delay(steps[2])
                name.animateTo(1f, Emerge)
            }
            launch {
                delay(steps[3])
                tag.animateTo(1f, Rise)
            }
            launch {
                delay(steps[4])
                actions.animateTo(1f, Rise)
            }
        }
    }

    /**
     * The words and buttons go, the glass fills while [work] runs, for long enough to be seen
     * whatever its speed; returns what [work] did.
     */
    suspend fun import(work: suspend () -> Boolean): Boolean = coroutineScope {
        launch { actions.animateTo(0f, Leave) }
        launch { tag.animateTo(0f, Leave) }
        launch { name.animateTo(0f, Leave) }
        launch {
            delay(300)
            progress.animateTo(1f, Rise)
        }
        val result = async { runCatching { work() }.getOrDefault(false) }
        fill.animateTo(1f, tween(FillMillis))
        result.await().also { ok ->
            if (!ok) {
                fill.animateTo(0f, Leave)
                progress.snapTo(0f)
            }
        }
    }
}

/** Positions are fractions of the page's width and height; speeds are turns per second. */
private class Blob(val x: Float, val y: Float, val radius: Float, val dx: Float, val dy: Float, val speed: Float, val phase: Float)

private val Blobs = listOf(
    Blob(.5f, .38f, .62f, .18f, .10f, .07f, 0f),
    Blob(.28f, .62f, .55f, .14f, .16f, .05f, 2f),
    Blob(.78f, .26f, .5f, .12f, .12f, .06f, 4f),
    Blob(.62f, .78f, .58f, .2f, .1f, .045f, 1f),
    Blob(.2f, .2f, .42f, .1f, .14f, .08f, 3f),
)

private class AuroraPalette(val background: Color, val blobs: List<Pair<Color, Float>>)

private val AuroraLight = AuroraPalette(
    Color(0xFFEEF1FA),
    listOf(Color(0xFF4F7FE8) to .5f, Color(0xFF8B6CF0) to .42f, Color(0xFF3CC6C0) to .4f, Color(0xFFF5A97F) to .38f, Color(0xFFF08BB8) to .34f),
)
private val AuroraDark = AuroraPalette(
    Color(0xFF090C16),
    listOf(Color(0xFF3D6EF0) to .55f, Color(0xFF7B5CF0) to .5f, Color(0xFF20B3AD) to .4f, Color(0xFFE98A5A) to .3f, Color(0xFFD9659C) to .32f),
)

/**
 * Comes into focus: from blurred, a little lower and smaller, and transparent, to sharp as
 * [progress] goes from 0 to 1. Blur is drawn from Android 12; before, it only fades and rises.
 * The layer is drawn with room around the content, which the blur spreads into instead of being
 * cut off square at the content's edges; the room takes no space in the layout.
 */
private fun Modifier.condense(
    progress: () -> Float,
    blur: Dp,
    rise: Dp = 0.dp,
    scale: Float = 1f,
    fadeFaster: Float = 1f,
): Modifier = this
    .layout { measurable, constraints ->
        val room = (blur * 2).roundToPx()
        val placeable = measurable.measure(constraints.offset(room * 2, room * 2))
        layout((placeable.width - room * 2).coerceAtLeast(0), (placeable.height - room * 2).coerceAtLeast(0)) {
            placeable.place(-room, -room)
        }
    }
    .graphicsLayer {
        val p = progress()
        alpha = (p * fadeFaster).coerceIn(0f, 1f)
        translationY = (1f - p) * rise.toPx()
        val s = lerp(scale, 1f, p)
        scaleX = s
        scaleY = s
        val radius = (1f - p.coerceAtMost(1f)) * blur.toPx()
        renderEffect = if (radius > .5f) BlurEffect(radius, radius, TileMode.Decal) else null
    }
    .padding(blur * 2)

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Critically damped, as slow as [response] seconds: seen once, the page takes its time. */
private fun <T> slow(response: Float): AnimationSpec<T> {
    val omega = 2 * PI.toFloat() / response
    return spring(dampingRatio = 1f, stiffness = omega * omega)
}

private val Spread = spring<Float>(dampingRatio = 1f, stiffness = 1.21f)
private val Emerge = slow<Float>(1.5f)
private val Drift = slow<Float>(1.9f)
private val Rise = slow<Float>(1.2f)
private val Leave = slow<Float>(.6f)

/** When the paper, lens, name, words and buttons start, in milliseconds. */
private val Steps = longArrayOf(350, 650, 1150, 1750, 2150)
private val QuickSteps = longArrayOf(100, 300, 800, 1200, 1550)
private const val FillMillis = 2200

private val MarkSize = 120.dp
private val MarkShadow = Color(0xFF2F57B0)
private val ActionHeight = 52.dp

/** The name, its gap and the line under it. */
private val TextsHeight = 32.dp + 56.dp + 8.dp + 28.dp
private val Name = TextStyle(fontSize = 46.sp, lineHeight = 56.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
private val DoneTitle = TextStyle(fontSize = 30.sp, lineHeight = 56.sp, fontWeight = FontWeight.SemiBold)
private val ActionLabel = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium)
