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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import oolong.client.ui.common.theme.compose.Motion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class EditorTool { Crop, Mask, Mark, Text }

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
    var laidOut = false

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

    fun rotate() = frame(history.framing.rotated(renderer.size))

    fun flip() = frame(history.framing.mirrored(renderer.size))

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
        renderer.orient(history.framing).mapPoints(values)
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
