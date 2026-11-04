package oolong.client.ui.page.editor

import android.graphics.Matrix
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import oolong.client.ui.common.theme.compose.Motion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class EditorTool { Crop, Adjust, Mask, Mark, Text }

/**
 * A frame shape: any, the picture's own, or a fixed one given by its longer side over its
 * shorter, which stands either way round.
 */
enum class CropRatio(val long: Int = 0, val short: Int = 0) {
    Free,
    Original,
    Square(1, 1),
    FourThree(4, 3),
    ThreeTwo(3, 2),
    SixteenNine(16, 9),
    FiveFour(5, 4),
    ;

    /** Whether it has a long side, to stand either way round. */
    val oriented: Boolean get() = long > short
}

/** The colours marks and captions come in, the most used first; any other can be picked too. */
val EditorColors = listOf(
    Color(0xFFFF4D4F),
    Color(0xFFFF8A3D),
    Color(0xFFFFC53D),
    Color(0xFF52C41A),
    Color(0xFF4096FF),
    Color(0xFF9B6BFF),
    Color.White,
    Color(0xFF1F1F1F),
    Color(0xFFFF7EB6),
    Color(0xFF19C6C6),
    Color(0xFF2B5BD7),
    Color(0xFF8C5A3C),
    Color(0xFF9AA0A9),
)

/** Sizes on screen, in dp, for the three steps of each tool's size. */
val MaskWidths = floatArrayOf(14f, 24f, 40f)
val MarkWidths = floatArrayOf(3f, 5f, 8f)
val CaptionSizes = floatArrayOf(22f, 32f, 46f)

/**
 * The editor: the picture and its edits, the tool in hand and its options, and which part of the
 * turned picture the screen shows.
 */
@Stable
class EditorState(
    val renderer: EditRenderer,
    private val scope: CoroutineScope,
    private val initial: List<Edit> = emptyList(),
) {
    val whole = Framing.whole(renderer.size)
    val history = EditHistory(whole).apply { restore(initial) }

    /** Which adjustment the ruler changes. */
    var adjusting by mutableStateOf(Adjustment.Exposure)

    /** The adjustments while the ruler is dragged, before they become a step. */
    var draftAdjustments by mutableStateOf<Map<Adjustment, Float>?>(null)

    /** The adjustments as shown now, a drag included. */
    val adjustments: Map<Adjustment, Float> get() = draftAdjustments ?: history.adjustments

    /** The adjustment picked set to [value], as the ruler moves. */
    fun adjust(value: Float) {
        draftAdjustments = adjustments + (adjusting to value)
    }

    fun commitAdjust() {
        val values = draftAdjustments ?: return
        draftAdjustments = null
        if (values.filterValues { it != 0f } != history.adjustments.filterValues { it != 0f }) history.push(Edit.Adjust(values.filterValues { it != 0f }))
    }

    /** Every adjustment back to none. */
    fun resetAdjustments() {
        draftAdjustments = null
        if (!history.adjustments.changesNothing()) history.push(Edit.Adjust(NoAdjustments))
    }

    /** Whether anything differs from how the editor opened, which leaving would lose. */
    // A copy, as a slice of the history's list compares by identity, not by what is in it
    val edited: Boolean get() = history.applied.toList() != initial || draft != null

    var tool by mutableStateOf<EditorTool?>(null)
        private set

    var ratio by mutableStateOf(CropRatio.Free)

    /** Whether fixed shapes stand upright; null follows the picture. */
    private var upright by mutableStateOf<Boolean?>(null)

    /** Whether fixed shapes stand upright: as asked, or as the picture does. */
    val portrait: Boolean
        get() = upright ?: history.framing.turned(renderer.size).let { it.height > it.width }

    /** Stands fixed shapes the other way round, and the frame with them if it has one. */
    fun turnRatio() {
        upright = !portrait
        if (ratio.oriented) pickRatio(ratio)
    }
    var maskStyle by mutableStateOf(MaskStyle.Mosaic)

    /** How masks are laid; null rubs masks out instead, showing the picture again. */
    var maskShape by mutableStateOf<MaskShape?>(MaskShape.Brush)
    var maskSize by mutableIntStateOf(1)

    /**
     * Whether a tap hides what it lands on, a line of text or a thing found around it, rather
     * than masks being laid by hand. What is found is hidden as a masked box, as one drawn is.
     */
    var maskPicking by mutableStateOf(false)

    /** Whether the picture is ready for taps. */
    var pickReady by mutableStateOf(false)

    /** A tap waiting to be looked into: where it was on screen, and whether the finger was held first. */
    class Pick(val at: Offset, val held: Boolean)

    var pendingPick by mutableStateOf<Pick?>(null)

    /** Where a finger on the picture aims, on screen, from the moment it is down until its tap is looked into. */
    var aim by mutableStateOf<Offset?>(null)

    /** Whether the finger is held or moved, rather than tapped: what it would hide then shows, with a loupe. */
    var aiming by mutableStateOf(false)

    /**
     * What the finger aiming at [point], in picture pixels, would do: hide [blocks], what it has
     * passed over, or take away [removing], a mask a tap laid; neither when it finds nothing.
     */
    class Aimed(val point: Offset, val blocks: List<Rect>, val removing: Edit.Mask?)

    var aimed by mutableStateOf<Aimed?>(null)

    /** Something the finger passed over while aiming, in picture pixels, and whether it is a line of text. */
    class Swept(val box: Rect, val text: Boolean)

    /** What the finger has passed over since it went down. */
    var swept by mutableStateOf(emptyList<Swept>())

    /** Adds [found] to what the finger has passed over, leaving out what it has already, or a tap hid. */
    fun sweep(found: List<Swept>) {
        var all = swept
        for (f in found) {
            if (all.any { same(it.box, f.box) } || tappedMaskOver(f.box) != null) continue
            all = all.filterNot { f.box.holds(it.box) } + f
        }
        if (all.size != swept.size || all.zip(swept).any { (a, b) -> a !== b }) swept = all
    }

    /** What the finger has passed over, lines of text stacked one under another joined in blocks. */
    fun sweptBlocks(): List<Rect> {
        val blocks = swept.filter { it.text }.map { it.box }.sortedBy { it.top }.toMutableList()
        var joined = true
        while (joined) {
            joined = false
            loop@ for (i in blocks.indices) {
                for (j in i + 1 until blocks.size) {
                    if (stacked(blocks[i], blocks[j])) {
                        blocks[i] = blocks[i].union(blocks[j])
                        blocks.removeAt(j)
                        joined = true
                        break@loop
                    }
                }
            }
        }
        val things = swept.filterNot { it.text }.map { it.box }.filter { thing -> blocks.none { it.holds(thing) } }
        return blocks + things
    }

    /** Hides [blocks], what a slide passed over, as one step, the finger last [at] a point on screen. */
    fun hideSwept(at: Offset, blocks: List<Rect>) {
        if (blocks.size == 1) return hidePicked(at, blocks[0], held = true)
        val first = history.nextId
        val masks = blocks.mapIndexed { i, box -> pickedMask(first + i, box) }
        history.push(Edit.Group(masks))
        tapped += masks.map { it.id }
        picked = Picked(at, blocks.reduce(Rect::union), null, held = true)
    }

    /** Done aiming, once the finger's tap is looked into or it moves the view instead. */
    fun stopAiming() {
        aim = null
        aiming = false
        swept = emptyList()
    }

    /**
     * The last tap looked into: where it was on screen, and the mask it laid over the box it
     * found, or none when it found nothing small enough.
     */
    class Picked(val at: Offset, val box: Rect?, val id: Int?, val removed: Edit.Mask? = null, val held: Boolean = false)

    var picked by mutableStateOf<Picked?>(null)
        private set

    /** Hides [box], in picture pixels, found by a tap [at] a point on screen. */
    fun hidePicked(at: Offset, box: Rect, held: Boolean = false) {
        val id = history.nextId
        history.push(pickedMask(id, box))
        tapped += id
        picked = Picked(at, box, id, held = held)
    }

    /** The mask a tap lays over [box]; [id] -1 for one only shown while aiming. */
    fun pickedMask(id: Int, box: Rect) = Edit.Mask(id, listOf(box.topLeft, box.bottomRight), MaskWidths[maskSize] * density / scale, maskStyle, MaskShape.Rect)

    /** The masks laid by a tap this time the editor is open. */
    private val tapped = mutableSetOf<Int>()

    /**
     * The mask laid by a tap under [screen], the topmost: a tap there takes it away again. Only
     * a tap on it, give or take a little, so one on the next line hides that line instead.
     */
    fun tappedMaskAt(screen: Offset): Edit.Mask? {
        val point = toPicture(toTurned(screen))
        val reach = 4 * density / scale
        return tappedMasks().lastOrNull { box(it.points).inflate(reach).contains(point) }
    }

    /** The mask laid by a tap over much the same place as [box], which a tap finding it again takes away. */
    fun tappedMaskOver(box: Rect): Edit.Mask? = tappedMasks().lastOrNull { mask ->
        val other = box(mask.points)
        val overlap = box.intersect(other)
        if (overlap.width <= 0f || overlap.height <= 0f) return@lastOrNull false
        val shared = overlap.width * overlap.height
        shared / (box.width * box.height + other.width * other.height - shared) >= 0.7f
    }

    private fun tappedMasks() = history.strokes.filterIsInstance<Edit.Mask>().filter { it.id in tapped }

    /** Takes [mask] away again, tapped [at] a point on screen. */
    fun unhidePicked(at: Offset, mask: Edit.Mask, held: Boolean = false) {
        history.push(Edit.Remove(mask.id))
        picked = Picked(at, box(mask.points), mask.id, removed = mask, held = held)
    }

    /** A tap [at] a point on screen that found nothing small enough to be one thing. */
    fun missedPick(at: Offset) {
        picked = Picked(at, null, null)
    }

    /** What marks draw; null rubs marks out instead. */
    var markKind by mutableStateOf<MarkKind?>(MarkKind.Pen)
    var markColor by mutableStateOf(EditorColors[0])
    var markSize by mutableIntStateOf(1)
    var captionStyle by mutableStateOf(CaptionStyle.Outline)
    var captionColor by mutableStateOf(Color.White)
    var captionSize by mutableIntStateOf(1)

    /** The frame while its edges are dragged, before it becomes a step. */
    var draftRect by mutableStateOf<Rect?>(null)

    /** The stroke being drawn. */
    var live by mutableStateOf<Edit?>(null)

    /** The caption being typed, and its text. */
    var draft by mutableStateOf<Edit.Caption?>(null)
        private set
    var draftText by mutableStateOf(TextFieldValue())

    /** The straightening while the ruler is dragged, before it becomes a step. */
    var draftAngle by mutableStateOf<Float?>(null)

    val framing: Framing
        get() = history.framing.let { framing ->
            framing.copy(rect = draftRect ?: framing.rect, angle = draftAngle ?: framing.angle)
        }

    /** Where the picture may be shown on screen, in pixels. */
    var area by mutableStateOf(Rect.Zero)

    /** The part of the turned picture the screen shows, fitted into [area]. */
    val region = Animatable(Rect(Offset.Zero, renderer.size), Rect.VectorConverter)

    /** The part shown when nothing is zoomed: the whole picture while cropping, the kept part otherwise. */
    val home: Rect get() = if (tool == EditorTool.Crop) Rect(Offset.Zero, framing.turned(renderer.size)) else history.framing.rect

    /** Screen pixels a turned-picture pixel. */
    val scale: Float
        get() {
            val r = region.value
            if (r.width <= 0f || area.width <= 0f) return 1f
            return min(area.width / r.width, area.height / r.height)
        }

    fun toScreen(point: Offset): Offset {
        val r = region.value
        return area.center + (point - r.center) * scale
    }

    fun toTurned(point: Offset): Offset = region.value.center + (point - area.center) / scale

    fun toPicture(turned: Offset): Offset {
        val inverse = Matrix().also { renderer.orient(history.framing).invert(it) }
        val values = floatArrayOf(turned.x, turned.y)
        inverse.mapPoints(values)
        return Offset(values[0], values[1])
    }

    /** From turned-picture pixels to the screen, for drawing. */
    fun screenMatrix(): Matrix = Matrix().apply {
        val r = region.value
        postTranslate(-r.center.x, -r.center.y)
        postScale(scale, scale)
        postTranslate(area.center.x, area.center.y)
    }

    fun select(next: EditorTool?) {
        finishCaption()
        deselect()
        tool = if (next == tool) null else next
        goHome()
        // Text starts with a caption to type, in the middle, unless there are some already
        if (tool == EditorTool.Text && history.captions.isEmpty()) editCaption(screen = area.center)
    }

    /** Whether [area] has been measured and the picture shown in it. */
    var laidOut by mutableStateOf(false)

    /** Whether the picture is left undrawn, while a copy of it flies in or out over the screen. */
    var hidden by mutableStateOf(false)

    /** Where the kept part shows on screen. */
    fun keptOnScreen(): Rect = history.framing.rect.let { Rect(toScreen(it.topLeft), toScreen(it.bottomRight)) }

    /**
     * The picture as it would leave, for a copy to fly: written, as it now stands, where it shows;
     * cancelled, as it was opened with [initial] steps, where it showed then. Null before it is placed.
     */
    fun look(written: Boolean, initial: List<Edit>): Pair<ImageBitmap, Rect>? {
        if (!laidOut || area.width <= 0f) return null
        val side = max(area.width, area.height).toInt()
        return if (written) {
            renderer.render(history.framing, history.strokes, history.captions, side).asImageBitmap() to keptOnScreen()
        } else {
            val opened = EditHistory(whole).apply { restore(initial) }
            renderer.render(opened.framing, opened.strokes, opened.captions, side).asImageBitmap() to fitted(opened.framing.rect.size)
        }
    }

    /** Where a picture of [size] shows when fitted into [area], as the kept part is at home. */
    fun fitted(size: Size): Rect {
        val s = min(area.width / size.width, area.height / size.height)
        val shown = size * s
        return Rect(area.center - Offset(shown.width / 2, shown.height / 2), shown)
    }

    /** Shows [home], as after a tool changes or the frame does. */
    fun goHome(animate: Boolean = true) {
        val home = home
        scope.launch { if (animate) region.animateTo(home, Motion.morph()) else region.snapTo(home) }
    }

    /** Zooms by [zoom] about [centroid] and moves by [pan], on screen. */
    fun transform(centroid: Offset, pan: Offset, zoom: Float) {
        val r = region.value
        val home = home
        // At most eight times closer than home, and no further out than half of it
        val z = zoom.coerceIn(r.width / (home.width * 2), r.width / (home.width / 8))
        val focus = toTurned(centroid)
        val size = Size(r.width / z, r.height / z)
        val center = focus + (r.center - focus) / z - pan / (scale * z)
        scope.launch { region.snapTo(Rect(center - Offset(size.width / 2, size.height / 2), size)) }
    }

    /** As much of a move by [step], on screen, as there is picture past the edge it moves in from. */
    fun edgeRoom(step: Offset): Offset {
        val kept = history.framing.rect
        val shown = Rect(toScreen(kept.topLeft), toScreen(kept.bottomRight))
        fun room(move: Float, before: Float, after: Float) =
            if (move > 0f) move.coerceAtMost(before.coerceAtLeast(0f)) else move.coerceAtLeast(-after.coerceAtLeast(0f))
        return Offset(
            room(step.x, area.left - shown.left, shown.right - area.right),
            room(step.y, area.top - shown.top, shown.bottom - area.bottom),
        )
    }

    /** Once the fingers let go: back home if zoomed out past it or moved off it. */
    fun settle() {
        val r = region.value
        val home = home
        if (r.width >= home.width && r.height >= home.height || !r.overlaps(home)) goHome()
    }

    fun undo() {
        finishCaption()
        deselect()
        val before = history.framing
        history.undo()
        if (history.framing != before) goHome()
    }

    fun redo() {
        finishCaption()
        deselect()
        val before = history.framing
        history.redo()
        if (history.framing != before) goHome()
    }

    fun frame(framing: Framing) {
        history.push(Edit.Frame(framing))
        goHome()
    }

    /** A quarter turn still being shown, in degrees clockwise, easing to none. */
    val turning = Animatable(0f)

    /** A mirroring still being shown, across: from -1, as it was, to 1. */
    val mirroring = Animatable(1f)

    /**
     * How much smaller the picture shows while it is part way through a turn, so that, turned,
     * it still fits on screen whole: none at a quarter, most near an eighth.
     */
    fun turningFit(): Float {
        val degrees = turning.value
        if (degrees == 0f || area.width <= 0f) return 1f
        val shown = home.size * scale
        val radians = Math.toRadians(degrees.toDouble())
        val cos = abs(kotlin.math.cos(radians)).toFloat()
        val sin = abs(kotlin.math.sin(radians)).toFloat()
        val width = shown.width * cos + shown.height * sin
        val height = shown.width * sin + shown.height * cos
        return min(1f, min(area.width / width, area.height / height))
    }

    /**
     * Turns the picture a quarter anticlockwise. It is turned at once and shown where it ends up;
     * the screen starts from how it looked and turns to it.
     */
    fun rotate() {
        history.push(Edit.Frame(history.framing.rotated(renderer.size)))
        scope.launch { region.snapTo(home) }
        scope.launch {
            turning.snapTo(turning.value + 90f)
            turning.animateTo(0f, Motion.settle())
        }
    }

    /** Mirrors the picture, turning it over on screen. */
    fun flip() {
        history.push(Edit.Frame(history.framing.mirrored(renderer.size)))
        scope.launch { region.snapTo(home) }
        scope.launch {
            mirroring.snapTo(-mirroring.value)
            mirroring.animateTo(1f, Motion.settle())
        }
    }

    fun restore() {
        ratio = CropRatio.Free
        frame(whole)
    }

    /** Width over height for [ratio], or null for any. */
    fun aspect(ratio: CropRatio = this.ratio): Float? {
        val turned = history.framing.turned(renderer.size)
        return when (ratio) {
            CropRatio.Free -> null
            CropRatio.Original -> turned.width / turned.height
            else -> if (portrait) ratio.short.toFloat() / ratio.long else ratio.long.toFloat() / ratio.short
        }
    }

    /** Takes [ratio], the frame becoming the largest of that shape about where it is now. */
    fun pickRatio(ratio: CropRatio) {
        this.ratio = ratio
        val aspect = aspect(ratio) ?: return
        val bounds = Rect(Offset.Zero, history.framing.turned(renderer.size))
        var width = bounds.width
        var height = width / aspect
        if (height > bounds.height) {
            height = bounds.height
            width = height * aspect
        }
        val center = history.framing.rect.center
        val left = (center.x - width / 2).coerceIn(0f, bounds.width - width)
        val top = (center.y - height / 2).coerceIn(0f, bounds.height - height)
        val framing = history.framing
        frame(framing.copy(rect = framing.fitted(Rect(left, top, left + width, top + height), renderer.size)))
    }

    /**
     * The frame dragged by [delta] turned pixels: by an edge or corner when [dx] or [dy] is -1
     * (left, top) or 1 (right, bottom), the whole frame when both are 0. It keeps to the picture,
     * at least [least] pixels across, and to the ratio picked.
     */
    fun dragFrame(start: Rect, dx: Int, dy: Int, delta: Offset, least: Float): Rect {
        // Straightened, a frame that would reach past the picture's edge stays where it was
        val next = dragFrameStraight(start, dx, dy, delta, least)
        return if (history.framing.holds(next, renderer.size)) next else draftRect ?: start
    }

    private fun dragFrameStraight(start: Rect, dx: Int, dy: Int, delta: Offset, least: Float): Rect {
        val bounds = Rect(Offset.Zero, history.framing.turned(renderer.size))
        if (dx == 0 && dy == 0) {
            val left = (start.left + delta.x).coerceIn(0f, bounds.width - start.width)
            val top = (start.top + delta.y).coerceIn(0f, bounds.height - start.height)
            return Rect(Offset(left, top), start.size)
        }
        var left = start.left
        var top = start.top
        var right = start.right
        var bottom = start.bottom
        if (dx < 0) left = (left + delta.x).coerceIn(0f, right - least)
        if (dx > 0) right = (right + delta.x).coerceIn(left + least, bounds.width)
        if (dy < 0) top = (top + delta.y).coerceIn(0f, bottom - least)
        if (dy > 0) bottom = (bottom + delta.y).coerceIn(top + least, bounds.height)
        val aspect = aspect() ?: return Rect(left, top, right, bottom)
        // Keep the shape from the corner opposite the one held
        var width = right - left
        var height = width / aspect
        val room = if (dy < 0) bottom else bounds.height - top
        if (height > room) {
            height = room
            width = height * aspect
        }
        if (dx < 0) left = right - width else right = left + width
        if (dy < 0) top = bottom - height else bottom = top + height
        return Rect(left, top, right, bottom)
    }

    /** The straightening dragged to [angle]: the frame kept at [start] shrinks just enough to stay within. */
    fun straighten(start: Rect, angle: Float) {
        draftAngle = angle
        draftRect = history.framing.copy(angle = angle).fitted(start, renderer.size)
    }

    fun commitStraighten() {
        val angle = draftAngle ?: return
        val rect = draftRect ?: history.framing.rect
        draftAngle = null
        draftRect = null
        if (angle != history.framing.angle) history.push(Edit.Frame(history.framing.copy(angle = angle, rect = rect)))
    }

    fun commitFrame() {
        val rect = draftRect ?: return
        draftRect = null
        if (rect != history.framing.rect) history.push(Edit.Frame(history.framing.copy(rect = rect)))
    }

    /** Starts a stroke at [screen] with the tool in hand. */
    fun beginStroke(screen: Offset) {
        val point = toPicture(toTurned(screen))
        val maskShape = maskShape
        val markKind = markKind
        live = when (tool) {
            EditorTool.Mask -> if (maskShape == null) {
                Edit.Erase(listOf(point), MaskWidths[maskSize] * density / scale, Edit.Layer.Masks)
            } else {
                Edit.Mask(
                    history.nextId,
                    if (maskShape == MaskShape.Rect) listOf(point, point) else listOf(point),
                    MaskWidths[maskSize] * density / scale,
                    maskStyle,
                    maskShape,
                )
            }
            EditorTool.Mark -> if (markKind == null) {
                // A rubber as broad as a mask brush: marks are thin, and hard to hit with less
                Edit.Erase(listOf(point), MaskWidths[markSize] * density / scale, Edit.Layer.Marks)
            } else {
                Edit.Mark(
                    history.nextId,
                    markKind,
                    if (markKind.traced || markKind == MarkKind.Number) listOf(point) else listOf(point, point),
                    MarkWidths[markSize] * density / scale,
                    markColor,
                )
            }
            else -> null
        }
    }

    fun extendStroke(screen: Offset) {
        val point = toPicture(toTurned(screen))
        live = when (val stroke = live) {
            is Edit.Mask -> stroke.copy(points = if (stroke.shape == MaskShape.Rect) listOf(stroke.points.first(), point) else stroke.points + point)
            is Edit.Mark -> stroke.copy(
                points = when {
                    stroke.kind.traced -> stroke.points + point
                    // A numbered dot follows the finger until it lifts
                    stroke.kind == MarkKind.Number -> listOf(point)
                    else -> listOf(stroke.points.first(), point)
                },
            )
            is Edit.Erase -> stroke.copy(points = stroke.points + point)
            else -> stroke
        }
    }

    fun endStroke() {
        val stroke = live ?: return
        live = null
        // A line, arrow, box, ellipse or masked box needs some size to be one
        val spanned = (stroke is Edit.Mark && !stroke.kind.traced && stroke.kind != MarkKind.Number) ||
            (stroke is Edit.Mask && stroke.shape == MaskShape.Rect)
        if (spanned) {
            val width = if (stroke is Edit.Mark) stroke.width else (stroke as Edit.Mask).width
            val points = if (stroke is Edit.Mark) stroke.points else (stroke as Edit.Mask).points
            val (a, b) = points.first() to points.last()
            if (abs(a.x - b.x) < width * 2 && abs(a.y - b.y) < width * 2) return
        }
        history.push(stroke)
    }

    /** Screen density, for sizes given in dp. */
    var density = 1f

    /** The caption under [screen], if any. */
    fun captionAt(screen: Offset): Edit.Caption? {
        val turned = toTurned(screen)
        val slop = 8 * density / scale
        return history.captions.lastOrNull { renderer.captionBounds(it, history.framing).inflate(slop).contains(turned) }
    }

    /** Starts typing [caption], or a new caption at [screen]. */
    fun editCaption(caption: Edit.Caption? = null, screen: Offset = area.center) {
        finishCaption()
        val editing = caption ?: Edit.Caption(
            id = history.nextId,
            text = "",
            center = toPicture(toTurned(screen)),
            size = CaptionSizes[captionSize] * density / scale,
            color = captionColor,
            style = captionStyle,
        )
        if (caption != null) {
            captionColor = caption.color
            captionStyle = caption.style
        }
        draft = editing
        draftText = TextFieldValue(editing.text, TextRange(editing.text.length))
    }

    fun styleCaption() {
        draft = draft?.copy(color = captionColor, style = captionStyle, size = CaptionSizes[captionSize] * density / scale)
    }

    /** The caption being dragged, and by how far on screen. */
    var moving by mutableStateOf<Pair<Int, Offset>?>(null)

    /** Where [caption]'s centre lies in the turned picture. */
    fun turnedCenter(caption: Edit.Caption): Offset {
        val values = floatArrayOf(caption.center.x, caption.center.y)
        renderer.orient(history.framing).mapPoints(values)
        return Offset(values[0], values[1])
    }

    /** [caption] moved [by] on screen. */
    fun movedCaption(caption: Edit.Caption, by: Offset): Edit.Caption =
        caption.copy(center = toPicture(toTurned(toScreen(turnedCenter(caption)) + by)))

    fun finishCaption() {
        val caption = draft ?: return
        draft = null
        val text = draftText.text.trimEnd()
        val existing = history.captions.firstOrNull { it.id == caption.id }
        val done = caption.copy(text = text)
        if (existing == null && text.isBlank()) return
        if (existing != done) history.push(done)
    }

    /** Whether anything would be lost by leaving. */
    val changed: Boolean get() = history.canUndo || draft != null

    /** The largest side the frame must keep, in turned pixels. */
    fun leastFrame(): Float = max(48 * density / scale, 16f)

    /** The mask or mark picked up to change, by id. */
    var selected by mutableStateOf<Int?>(null)
        private set

    /** The picked-up mask or mark while a finger moves it, before it becomes a step. */
    var dragged by mutableStateOf<Edit?>(null)

    /** The picked-up mask or mark as it now stands. */
    val selection: Edit? get() = dragged ?: selected?.let { id -> history.strokes.firstOrNull { it.objectId == id } }

    /** The strokes as drawn now, with the one being moved where the finger has it. */
    val shownStrokes: List<Edit>
        get() {
            val moving = dragged ?: return history.strokes
            return history.strokes.map { if (it.objectId == moving.objectId) moving else it }
        }

    /** What can be picked up with the tool in hand: marks with markup, boxed masks with redaction. */
    private fun pickable(edit: Edit): Boolean = when (edit) {
        is Edit.Mark -> tool == EditorTool.Mark || tool == null
        is Edit.Mask -> edit.shape == MaskShape.Rect && (tool == EditorTool.Mask || tool == null)
        else -> false
    }

    /** The mask or mark under [screen] that can be picked up, the topmost first. */
    fun objectAt(screen: Offset): Edit? {
        val point = toPicture(toTurned(screen))
        val slop = 12 * density / scale
        return history.strokes.lastOrNull { pickable(it) && hits(it, point, slop) }
    }

    private fun hits(edit: Edit, point: Offset, slop: Float): Boolean = when (edit) {
        is Edit.Mask -> box(edit.points).inflate(slop).contains(point)
        is Edit.Mark -> when (edit.kind) {
            MarkKind.Pen, MarkKind.Highlighter -> {
                val reach = edit.width * (if (edit.kind == MarkKind.Highlighter) 3.5f else 1f) / 2 + slop
                edit.points.size == 1 && (edit.points[0] - point).getDistance() < reach ||
                    edit.points.zipWithNext().any { (a, b) -> distanceToSegment(point, a, b) < reach }
            }
            MarkKind.Line, MarkKind.Arrow -> distanceToSegment(point, edit.points.first(), edit.points.last()) < edit.width / 2 + slop
            MarkKind.Box, MarkKind.Ellipse -> box(edit.points).inflate(edit.width / 2 + slop).contains(point)
            MarkKind.Number -> (edit.points.first() - point).getDistance() < edit.width * 2.8f + slop
        }
        else -> false
    }

    /** Where [edit] lies in the picture's pixels, its breadth counted. */
    fun bounds(edit: Edit): Rect = when (edit) {
        is Edit.Mask -> box(edit.points)
        is Edit.Mark -> {
            val reach = when (edit.kind) {
                MarkKind.Number -> edit.width * 2.8f
                MarkKind.Highlighter -> edit.width * 1.75f
                else -> edit.width / 2
            }
            box(edit.points).inflate(reach)
        }
        else -> Rect.Zero
    }

    /** Picks [edit] up, and shows its colour, size and kind in the options. */
    fun pick(edit: Edit) {
        selected = edit.objectId
        when (edit) {
            is Edit.Mark -> {
                if (tool != EditorTool.Mark) select(EditorTool.Mark)
                selected = edit.id
                markKind = edit.kind
                markColor = edit.color
                markSize = nearestStep(edit.width, MarkWidths)
            }
            is Edit.Mask -> {
                if (tool != EditorTool.Mask) select(EditorTool.Mask)
                selected = edit.id
                maskShape = edit.shape
                maskStyle = edit.style
            }
            else -> Unit
        }
    }

    fun deselect() {
        selected = null
        dragged = null
    }

    /** Which of [steps], on screen, a [width] in picture pixels comes nearest to now. */
    private fun nearestStep(width: Float, steps: FloatArray): Int =
        steps.indices.minBy { abs(steps[it] * density / scale - width) }

    /** The handles of [edit] that move one end on their own: both ends of what spans two points. */
    fun handles(edit: Edit): List<Offset> = when {
        edit is Edit.Mask -> listOf(edit.points.first(), edit.points.last())
        edit is Edit.Mark && !edit.kind.traced && edit.kind != MarkKind.Number -> listOf(edit.points.first(), edit.points.last())
        else -> emptyList()
    }

    /** [edit] moved by [by], in picture pixels, whole or, given [handle], that end alone. */
    fun moved(edit: Edit, by: Offset, handle: Int? = null): Edit {
        fun shift(points: List<Offset>) = if (handle == null) {
            points.map { it + by }
        } else {
            val end = if (handle == 0) 0 else points.lastIndex
            points.mapIndexed { i, p -> if (i == end) p + by else p }
        }
        return when (edit) {
            is Edit.Mask -> edit.copy(points = shift(edit.points))
            is Edit.Mark -> edit.copy(points = shift(edit.points))
            else -> edit
        }
    }

    /** Ends a move: the moved mask or mark becomes a step. */
    fun commitDrag() {
        val moved = dragged ?: return
        dragged = null
        if (moved != history.strokes.firstOrNull { it.objectId == moved.objectId }) history.push(moved)
    }

    /** Gives the picked-up mark the colour and size in the options, or the boxed mask the style. */
    fun restyle() {
        val edit = selection ?: return
        val changed = when (edit) {
            is Edit.Mark -> edit.copy(color = markColor, width = MarkWidths[markSize] * density / scale)
            is Edit.Mask -> edit.copy(style = maskStyle)
            else -> edit
        }
        if (changed != edit) history.pushOrMerge(changed)
    }

    /** Where a point in the picture's pixels shows on screen. */
    fun pictureToScreen(point: Offset): Offset {
        val values = floatArrayOf(point.x, point.y)
        // As drawn now, a straightening being dragged included
        renderer.orient(framing).mapPoints(values)
        return toScreen(Offset(values[0], values[1]))
    }

    /** Where [edit] shows on screen. */
    fun screenBounds(edit: Edit): Rect {
        val b = bounds(edit)
        val corners = listOf(b.topLeft, b.topRight, b.bottomLeft, b.bottomRight).map(::pictureToScreen)
        return Rect(corners.minOf { it.x }, corners.minOf { it.y }, corners.maxOf { it.x }, corners.maxOf { it.y })
    }

    fun deleteSelection() {
        val id = selected ?: return
        deselect()
        history.push(Edit.Remove(id))
    }
}

/** Whether a mark follows the finger all the way, rather than spanning from where it went down. */
val MarkKind.traced: Boolean get() = this == MarkKind.Pen || this == MarkKind.Highlighter

private fun box(points: List<Offset>): Rect {
    val a = points.first()
    val b = points.last()
    return Rect(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y))
}

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val length = ab.x * ab.x + ab.y * ab.y
    if (length == 0f) return (p - a).getDistance()
    val t = (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / length).coerceIn(0f, 1f)
    return (p - (a + ab * t)).getDistance()
}

/** Whether [a] and [b] are much the same box. */
private fun same(a: Rect, b: Rect): Boolean {
    val overlap = a.intersect(b)
    if (overlap.width <= 0f || overlap.height <= 0f) return false
    val shared = overlap.width * overlap.height
    return shared / (a.width * a.height + b.width * b.height - shared) >= 0.7f
}

/** Whether this holds [other], give or take a pixel. */
private fun Rect.holds(other: Rect) =
    left <= other.left + 1 && top <= other.top + 1 && right >= other.right - 1 && bottom >= other.bottom - 1

private fun Rect.union(other: Rect) = Rect(min(left, other.left), min(top, other.top), max(right, other.right), max(bottom, other.bottom))

/** Whether lines [a] and [b] lie one under the other, side by side and close, as in a paragraph. */
private fun stacked(a: Rect, b: Rect): Boolean {
    val across = min(a.right, b.right) - max(a.left, b.left)
    val gap = max(a.top, b.top) - min(a.bottom, b.bottom)
    return across > 0f && gap <= 0.8f * min(a.height, b.height)
}
