package oolong.client.ui.page.editor

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * How the picture is turned and cut: [turns] quarters anticlockwise, then mirrored left to right
 * if [flipped]; [rect] is the part kept, in the turned picture's pixels.
 */
@Immutable
data class Framing(val turns: Int, val flipped: Boolean, val rect: Rect) {
    /** The picture of [size] turned as this turns it. */
    fun turned(size: Size): Size = if (turns % 2 == 0) size else Size(size.height, size.width)

    /** Turned once more anticlockwise, the kept part turning with it, in a picture of [size]. */
    fun rotated(size: Size): Framing {
        val width = turned(size).width
        // Mirrored, a quarter more the other way turns it the same way on screen
        return copy(turns = (turns + if (flipped) 3 else 1) % 4, rect = Rect(rect.top, width - rect.right, rect.bottom, width - rect.left))
    }

    /** Mirrored, the kept part mirrored with it, in a picture of [size]. */
    fun mirrored(size: Size): Framing {
        val width = turned(size).width
        return copy(flipped = !flipped, rect = Rect(width - rect.right, rect.top, width - rect.left, rect.bottom))
    }

    companion object {
        /** The whole picture of [size], as it is. */
        fun whole(size: Size) = Framing(0, false, Rect(Offset.Zero, size))
    }
}

enum class MaskStyle { Mosaic, Blur, Block }

/** How a mask is laid: painted along the finger, or as a box dragged corner to corner. */
enum class MaskShape { Brush, Rect }

/**
 * What a mark draws: a pen or highlighter line through the points; a line, arrow, box or ellipse
 * from the first to the last; or a numbered dot at the first, numbered in the order dots were put.
 */
enum class MarkKind { Pen, Highlighter, Line, Arrow, Box, Ellipse, Number }

enum class CaptionStyle { Outline, Filled, Plain }

/**
 * One step of an edit. Points are in the picture's own pixels, as decoded, so turning and cutting
 * the picture later never loses or moves what was drawn.
 */
@Immutable
sealed interface Edit {
    data class Frame(val framing: Framing) : Edit

    /** Hides what lies under a stroke through [points], [width] wide, or the box they span. */
    data class Mask(val points: List<Offset>, val width: Float, val style: MaskStyle, val shape: MaskShape = MaskShape.Brush) : Edit

    /** A [kind] of mark through or between [points], [width] wide. */
    data class Mark(val kind: MarkKind, val points: List<Offset>, val width: Float, val color: Color) : Edit

    /**
     * Text centred on [center], [size] pixels high, always upright however the picture is turned.
     * A later caption with the same [id] replaces it; one with blank [text] removes it.
     */
    data class Caption(
        val id: Int,
        val text: String,
        val center: Offset,
        val size: Float,
        val color: Color,
        val style: CaptionStyle,
    ) : Edit
}

/**
 * The edits made so far, as a list of steps and how many of them count: undo and redo move that
 * count, a new step drops the ones undone.
 */
@Stable
class EditHistory(private val whole: Framing) {
    private val steps = mutableStateListOf<Edit>()

    var position by mutableIntStateOf(0)
        private set

    val canUndo: Boolean get() = position > 0
    val canRedo: Boolean get() = position < steps.size

    val applied: List<Edit> get() = steps.subList(0, position)

    val framing: Framing get() = applied.lastOrNull { it is Edit.Frame }?.let { (it as Edit.Frame).framing } ?: whole

    /** The strokes, masks and marks together, in the order they were made. */
    val strokes: List<Edit> get() = applied.filter { it is Edit.Mask || it is Edit.Mark }

    /** The captions as they stand, each once, in the order they were added. */
    val captions: List<Edit.Caption>
        get() {
            val latest = LinkedHashMap<Int, Edit.Caption>()
            for (edit in applied) if (edit is Edit.Caption) latest[edit.id] = edit
            return latest.values.filter { it.text.isNotBlank() }
        }

    /** An id no caption has had. */
    val nextCaptionId: Int get() = (steps.filterIsInstance<Edit.Caption>().maxOfOrNull { it.id } ?: 0) + 1

    fun push(edit: Edit) {
        while (steps.size > position) steps.removeAt(steps.lastIndex)
        steps.add(edit)
        position = steps.size
    }

    fun undo() {
        if (canUndo) position--
    }

    fun redo() {
        if (canRedo) position++
    }
}
