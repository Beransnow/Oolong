package com.huanchengfly.tieba.post.ui.widgets.compose

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.huanchengfly.tieba.post.rememberPreferenceAsState
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.Motion
import com.huanchengfly.tieba.post.ui.common.theme.compose.Radius
import com.huanchengfly.tieba.post.ui.common.theme.compose.ShadowAmbient
import com.huanchengfly.tieba.post.ui.common.theme.compose.ShadowSpot
import com.huanchengfly.tieba.post.ui.common.theme.compose.SmoothCornerShape
import com.huanchengfly.tieba.post.ui.common.theme.compose.addSmoothRect
import com.huanchengfly.tieba.post.ui.common.theme.compose.glassEdge
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/** One thing a context menu offers. */
@Immutable
class ContextMenuItem(
    val icon: ImageVector,
    val label: String,
    /** Whether a rule parts it from the items before it. */
    val startsGroup: Boolean = false,
    val onClick: () -> Unit,
)

@Immutable
internal class ContextMenuRequest(
    val items: List<ContextMenuItem>,
    /** What the menu grows out of, in the window: a button, or an empty rect where a finger pressed. */
    val anchor: Rect,
    /** The button's icon, which the menu swallows as it opens and gives back as it closes. */
    val icon: ImageVector?,
    val id: Long,
)

/**
 * The window's context menus: one at a time, growing out of the button or the press that asked
 * for it, over everything else.
 */
@Stable
class ContextMenus internal constructor() {
    internal var request by mutableStateOf<ContextMenuRequest?>(null)
        private set

    /** Where the finger that opened the menu is, in the window, while it stays down. */
    internal var finger by mutableStateOf<Offset?>(null)
        private set

    /** Where that finger last lifted, counted so each lift is heard once. */
    internal var lift by mutableStateOf<Pair<Int, Offset>?>(null)
        private set

    private var shown = 0L

    /**
     * Offers [items], growing out of [anchor] in the window: a button, whose [icon] the menu takes
     * in while it shows, or an empty rect where a long press landed. After a long press the finger
     * stays in charge until it lifts: report it with [moveFinger] and [liftFinger].
     *
     * @return the menu's id, for [isShowing]
     */
    fun show(items: List<ContextMenuItem>, anchor: Rect, icon: ImageVector? = null): Long {
        finger = null
        request = ContextMenuRequest(items, anchor, icon, ++shown)
        return shown
    }

    /** Whether menu [id] is still on screen, opening, open or closing. */
    fun isShowing(id: Long?): Boolean = id != null && request?.id == id

    /** The finger that opened the menu moved to [position] in the window. */
    fun moveFinger(position: Offset) {
        if (request != null) finger = position
    }

    /** The finger that opened the menu lifted at [position]; over an item, that item runs. */
    fun liftFinger(position: Offset) {
        if (request == null) return
        finger = null
        lift = ((lift?.first ?: 0) + 1) to position
    }

    internal fun closed(id: Long) {
        if (request?.id != id) return
        request = null
        finger = null
    }
}

/** The window's context menus; null where there are none, as in previews and tests. */
val LocalContextMenus = staticCompositionLocalOf<ContextMenus?> { null }

/** Draws [content] with room for context menus over it. */
@Composable
fun ContextMenuHost(content: @Composable () -> Unit) {
    val menus = remember { ContextMenus() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    // What a menu's glass blurs, captured only while one shows
    val backdrop = rememberHazeState()
    CompositionLocalProvider(LocalContextMenus provides menus) {
        Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
            Box(modifier = if (menus.request != null) Modifier.hazeSource(backdrop) else Modifier) {
                content()
            }
            menus.request?.let { request ->
                key(request.id) {
                    ContextMenuOverlay(menus = menus, request = request, origin = origin, backdrop = backdrop)
                }
            }
        }
    }
}

/**
 * The menu of [request]. Opening, it swells out of its anchor like a drop: a button's icon blurs
 * away inside it while the items, small and blurred at first, grow with it and only sharpen at
 * the end. Closing runs the other way, back into the icon.
 */
@Composable
private fun ContextMenuOverlay(menus: ContextMenus, request: ContextMenuRequest, origin: Offset, backdrop: HazeState) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val colors = ExtendedTheme.colors

    val fromButton = !request.anchor.isEmpty
    // Out of a button, the button first swells in place, then sets off towards the menu's place
    // as a drop and grows into the menu on the way, its size trailing its position. Out of a
    // press there is nothing to carry: the menu simply grows from where the finger is.
    val swell = remember { Animatable(0f) }
    val move = remember { Animatable(0f) }
    val grow = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (fromButton) {
            swell.animateTo(1f, Motion.press(Motion.ProgressThreshold))
            launch { move.animateTo(1f, MoveSpring) }
            delay(GrowDelay)
        }
        grow.animateTo(1f, Motion.morph(Motion.ProgressThreshold))
    }

    // Closing runs it backwards: a button's menu shrinks into a drop first, which flies home
    fun dismiss() {
        if (closing) return
        closing = true
        scope.launch {
            if (fromButton) {
                val shrink = launch { grow.animateTo(0f, ShrinkSpring) }
                delay(GrowDelay)
                move.animateTo(0f, Motion.dismiss(Motion.ProgressThreshold))
                shrink.join()
            } else {
                grow.animateTo(0f, Motion.dismiss(Motion.ProgressThreshold))
            }
            menus.closed(request.id)
        }
    }

    val items = request.items
    // Where each item sits in the open menu. Taken from layout, as the items grow by scaling,
    // which moves them on screen without laying them out again.
    val itemPlaces = remember { mutableStateListOf(*Array(items.size) { Rect.Zero }) }
    var itemsCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val geometry = remember { MenuGeometry() }
    fun itemAt(position: Offset): Int? {
        val local = position - origin - geometry.bounds.topLeft
        return itemPlaces.indexOfFirst { it.contains(local) }.takeIf { it >= 0 }
    }
    val run: (Int) -> Unit = { index ->
        if (!closing) {
            items[index].onClick()
            dismiss()
        }
    }
    val hovered = menus.finger?.let(::itemAt)
    LaunchedEffect(hovered) {
        if (hovered != null) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    val liftsBefore = remember { menus.lift?.first }
    LaunchedEffect(menus.lift) {
        val (count, lifted) = menus.lift ?: return@LaunchedEffect
        if (count == liftsBefore) return@LaunchedEffect
        itemAt(lifted)?.let(run)
    }

    BackHandler(enabled = !closing) { dismiss() }

    // Menus take no scrim; a tap anywhere else only closes them
    Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { dismiss() } })

    val insets = WindowInsets.safeDrawing
    val anchor = request.anchor.translate(-origin)

    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            // The glass
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        val g = grow.value.coerceIn(0f, 1f)
                        shape = SmoothCornerShape((geometry.corner / density).dp)
                        clip = true
                        shadowElevation = (if (colors.isNightMode) 32.dp else 24.dp).toPx() * (0.3f + 0.7f * g)
                        ambientShadowColor = ShadowAmbient
                        spotShadowColor = ShadowSpot
                    }
                    .menuGlass(backdrop)
                    .drawWithContent {
                        drawContent()
                        val edge = SmoothCornerShape((geometry.corner / density).dp).createOutline(size, layoutDirection, this)
                        drawOutline(edge, colors.glassEdge, style = Stroke(width = 0.5.dp.toPx() * 2))
                    },
            )
            // The button's icon, swallowed by the glass
            Box {
                request.icon?.let { icon ->
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.graphicsLayer {
                            val g = grow.value.coerceIn(0f, 1f)
                            val glass = geometry.glass
                            // Rides in the middle of the drop, and melts into the items as it grows
                            translationX = glass.center.x - anchor.center.x
                            translationY = glass.center.y - anchor.center.y
                            val scale = lerp(1f, ButtonSwell, swell.value) * lerp(1f, 1.8f, g)
                            scaleX = scale
                            scaleY = scale
                            val fade = ((g - 0.1f) / 0.3f).coerceIn(0f, 1f)
                            alpha = 1f - fade
                            val blur = IconBlur.toPx() * fade
                            renderEffect = if (blur > 0.5f) BlurEffect(blur, blur) else null
                        },
                    )
                }
            }
            // The items, at their final size and place, scaled into the glass and clipped by it
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        val g = grow.value
                        val bounds = geometry.bounds
                        val glass = geometry.glass
                        // As wide as the glass, from its top left corner
                        val scale = if (bounds.width > 0f) glass.width / bounds.width else 1f
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = glass.left - bounds.left
                        translationY = glass.top - bounds.top
                        shape = GlassClip(geometry, scale)
                        clip = true
                        alpha = ((g - 0.2f) / 0.3f).coerceIn(0f, 1f)
                        val blur = IconBlur.toPx() * (1f - ((g - 0.45f) / 0.4f).coerceIn(0f, 1f))
                        renderEffect = if (blur > 0.5f) BlurEffect(blur, blur) else null
                    }
                    // Inside the scaling, so items are measured against the menu as laid out
                    .onPlaced { itemsCoordinates = it }
                    .widthIn(min = MenuMinWidth, max = MenuMaxWidth)
                    .width(IntrinsicSize.Max)
                    .padding(vertical = 6.dp),
            ) {
                items.forEachIndexed { index, item ->
                    if (item.startsGroup && index > 0) {
                        Divider(color = colors.divider, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ItemHeight)
                            .onGloballyPositioned { row ->
                                itemsCoordinates?.let { itemPlaces[index] = it.localBoundingBoxOf(row) }
                            }
                            .padding(horizontal = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (hovered == index) colors.chip else Color.Transparent)
                            .clickable { run(index) }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(imageVector = item.icon, contentDescription = null, tint = colors.text, modifier = Modifier.size(20.dp))
                        Text(text = item.label, color = colors.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val (glassMeasurable, iconMeasurable, itemsMeasurable) = measurables
        val items = itemsMeasurable.measure(Constraints())
        val size = Size(items.width.toFloat(), items.height.toFloat())
        val gap = FingerGap.toPx()
        val margin = Margin.toPx()
        val top = insets.getTop(this) + margin
        val bottom = constraints.maxHeight - insets.getBottom(this) - margin
        // A button's menu covers it, from the side it sits on; a press's sits beside the finger
        val fromRight = anchor.center.x > constraints.maxWidth / 2
        val left = when {
            !fromButton -> anchor.center.x - size.width / 2
            fromRight -> anchor.right - size.width
            else -> anchor.left
        }.coerceIn(margin, (constraints.maxWidth - size.width - margin).coerceAtLeast(margin))
        val below = (if (fromButton) anchor.top else anchor.center.y + gap) + size.height <= bottom
        val y = when {
            below && fromButton -> anchor.top
            below -> anchor.center.y + gap
            fromButton -> anchor.bottom - size.height
            else -> anchor.center.y - gap - size.height
        }.coerceAtLeast(top)
        val bounds = Rect(Offset(left, y), size)
        val g = grow.value
        geometry.bounds = bounds
        if (fromButton) {
            // Starts as the round backing a pressed button shows
            val startSize = ButtonRadius.toPx() * 2 * lerp(1f, ButtonSwell, swell.value)
            val m = move.value
            val center = Offset(lerp(anchor.center.x, bounds.center.x, m), lerp(anchor.center.y, bounds.center.y, m))
            val half = Offset(lerp(startSize, bounds.width, g), lerp(startSize, bounds.height, g)) / 2f
            geometry.glass = Rect(center - half, center + half)
            // Round while small, like a drop, easing into the menu's corners
            geometry.corner = min(geometry.glass.minDimension / 2, lerp(startSize / 2, Radius.lg.toPx(), g.coerceIn(0f, 1f)))
        } else {
            // Grows evenly out of the point of the menu nearest the finger
            val pivot = Offset(
                anchor.center.x.coerceIn(bounds.left, bounds.right),
                anchor.center.y.coerceIn(bounds.top, bounds.bottom),
            )
            geometry.glass = Rect(
                lerp(pivot.x, bounds.left, g),
                lerp(pivot.y, bounds.top, g),
                lerp(pivot.x, bounds.right, g),
                lerp(pivot.y, bounds.bottom, g),
            )
            geometry.corner = Radius.lg.toPx() * g.coerceAtLeast(0f)
        }
        val glassPlaceable = with(geometry.glass) {
            glassMeasurable.measure(Constraints.fixed(width.roundToInt().coerceAtLeast(0), height.roundToInt().coerceAtLeast(0)))
        }
        val icon = iconMeasurable.measure(Constraints())
        layout(constraints.maxWidth, constraints.maxHeight) {
            glassPlaceable.place(geometry.glass.left.roundToInt(), geometry.glass.top.roundToInt())
            icon.place(
                (anchor.center.x - icon.width / 2f).roundToInt(),
                (anchor.center.y - icon.height / 2f).roundToInt(),
            )
            items.place(bounds.left.roundToInt(), bounds.top.roundToInt())
        }
    }
}

/**
 * A menu's thick glass (design-system.md §2.5): the backdrop blurred under a veil of the sheet
 * colour, thicker at night; an opaque sheet where glass is off or unsupported.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
private fun Modifier.menuGlass(backdrop: HazeState): Modifier {
    val colors = ExtendedTheme.colors
    val reduceTransparency by rememberPreferenceAsState(ReduceTransparencyKey, false)
    val tier = LocalGlassTier.current ?: if (reduceTransparency) GlassTier.Solid else GlassTier.forDevice()
    if (tier == GlassTier.Solid) return background(colors.sheet)
    val style = remember(colors.sheet, colors.isNightMode) {
        HazeBlurStyle {
            backgroundColor(colors.sheet)
            blurRadius(MenuBlur)
            noiseFactor(0f)
            colorEffects(listOf(HazeColorEffect.tint(colors.sheet.copy(alpha = if (colors.isNightMode) 0.7f else 0.4f))))
        }
    }
    return hazeBlur(input = HazeInput.Sources(backdrop), style = style)
}

private class MenuGeometry {
    var bounds = Rect.Zero
    var glass = Rect.Zero
    var corner = 0f
}

/** The glass, as seen from the items scaled by [scale] at its top left corner, so they show only within it. */
private class GlassClip(private val geometry: MenuGeometry, private val scale: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val glass = geometry.glass
        if (scale <= 0f || glass.isEmpty) return Outline.Rectangle(Rect.Zero)
        return Outline.Generic(
            Path().apply {
                addSmoothRect(glass.width / scale, glass.height / scale, geometry.corner / scale, SmoothCornerShape.DefaultSmoothing)
            },
        )
    }
}

/** The drop's way to the menu's place, which runs ahead of its growing. */
private val MoveSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 500f, visibilityThreshold = Motion.ProgressThreshold)

/** The menu shrinking back into a drop, ahead of its way home. */
private val ShrinkSpring = spring<Float>(dampingRatio = 1f, stiffness = 900f, visibilityThreshold = Motion.ProgressThreshold)

/** How far growing trails moving, and moving home trails shrinking. */
private const val GrowDelay = 40L

/** How much a button swells as its menu opens, and lands back from as it closes. */
internal const val ButtonSwell = 1.1f

private val ButtonRadius = 20.dp
private val FingerGap = 20.dp
private val Margin = 12.dp
private val IconBlur = 20.dp
private val MenuMinWidth = 200.dp
private val MenuMaxWidth = 280.dp
private val ItemHeight = 46.dp
private val MenuBlur = 24.dp
