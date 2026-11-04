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

/** A frame shape: width over height of the turned picture, or null for any. */
enum class CropRatio { Free, Original, Square, Classic, Wide }

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
class EditorState(val renderer: EditRenderer, private val scope: CoroutineScope) {
    val whole = Framing.whole(renderer.size)
    val history = EditHistory(whole)

    var tool by mutableStateOf<EditorTool?>(null)
        private set

    var ratio by mutableStateOf(CropRatio.Free)
    var maskStyle by mutableStateOf(MaskStyle.Mosaic)
    var maskShape by mutableStateOf(MaskShape.Brush)
    var maskSize by mutableIntStateOf(1)
    var markKind by mutableStateOf(MarkKind.Pen)
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

    val framing: Framing get() = history.framing.let { framing -> draftRect?.let { framing.copy(rect = it) } ?: framing }

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
        val before = history.framing
        history.undo()
        if (history.framing != before) goHome()
    }

    fun redo() {
        finishCaption()
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
        val portrait = turned.height > turned.width
        return when (ratio) {
            CropRatio.Free -> null
            CropRatio.Original -> turned.width / turned.height
            CropRatio.Square -> 1f
            CropRatio.Classic -> if (portrait) 3f / 4f else 4f / 3f
            CropRatio.Wide -> if (portrait) 9f / 16f else 16f / 9f
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
        frame(history.framing.copy(rect = Rect(left, top, left + width, top + height)))
    }

    /**
     * The frame dragged by [delta] turned pixels: by an edge or corner when [dx] or [dy] is -1
     * (left, top) or 1 (right, bottom), the whole frame when both are 0. It keeps to the picture,
     * at least [least] pixels across, and to the ratio picked.
     */
    fun dragFrame(start: Rect, dx: Int, dy: Int, delta: Offset, least: Float): Rect {
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

    fun commitFrame() {
        val rect = draftRect ?: return
        draftRect = null
        if (rect != history.framing.rect) history.push(Edit.Frame(history.framing.copy(rect = rect)))
    }

    /** Starts a stroke at [screen] with the tool in hand. */
    fun beginStroke(screen: Offset) {
        val point = toPicture(toTurned(screen))
        live = when (tool) {
            EditorTool.Mask -> Edit.Mask(
                if (maskShape == MaskShape.Rect) listOf(point, point) else listOf(point),
                MaskWidths[maskSize] * density / scale,
                maskStyle,
                maskShape,
            )
            EditorTool.Mark -> Edit.Mark(
                markKind,
                if (markKind.traced || markKind == MarkKind.Number) listOf(point) else listOf(point, point),
                MarkWidths[markSize] * density / scale,
                markColor,
            )
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
            id = history.nextCaptionId,
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
}

/** Whether a mark follows the finger all the way, rather than spanning from where it went down. */
val MarkKind.traced: Boolean get() = this == MarkKind.Pen || this == MarkKind.Highlighter
