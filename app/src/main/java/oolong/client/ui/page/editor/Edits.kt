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
 * if [flipped], then straightened by [angle] degrees clockwise about its middle; [rect] is the
 * part kept, in the turned picture's pixels, and lies within the straightened picture.
 */
@Immutable
data class Framing(val turns: Int, val flipped: Boolean, val rect: Rect, val angle: Float = 0f) {
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
        return copy(flipped = !flipped, angle = -angle, rect = Rect(width - rect.right, rect.top, width - rect.left, rect.bottom))
    }

    /** Whether [rect] lies wholly within the picture of [size] as this turns and straightens it. */
    fun holds(rect: Rect, size: Size): Boolean {
        val turned = turned(size)
        val center = Offset(turned.width / 2, turned.height / 2)
        val radians = Math.toRadians(-angle.toDouble())
        val cos = kotlin.math.cos(radians).toFloat()
        val sin = kotlin.math.sin(radians).toFloat()
        val slack = 0.5f
        return listOf(rect.topLeft, rect.topRight, rect.bottomLeft, rect.bottomRight).all { corner ->
            // The corner turned back by the straightening lies within the unstraightened picture
            val d = corner - center
            val x = d.x * cos - d.y * sin + center.x
            val y = d.x * sin + d.y * cos + center.y
            x >= -slack && y >= -slack && x <= turned.width + slack && y <= turned.height + slack
        }
    }

    /** [rect] shrunk about its middle, keeping its shape, just enough to lie within the picture. */
    fun fitted(rect: Rect, size: Size): Rect {
        if (holds(rect, size)) return rect
        var low = 0f
        var high = 1f
        repeat(24) {
            val mid = (low + high) / 2
            if (holds(rect.scaled(mid), size)) low = mid else high = mid
        }
        return rect.scaled(low)
    }

    companion object {
        /** The whole picture of [size], as it is. */
        fun whole(size: Size) = Framing(0, false, Rect(Offset.Zero, size))
    }
}

/** How a mask hides: in big cells, blurred, under a solid block, or cleared to what lies around it. */
enum class MaskStyle { Mosaic, Blur, Block, Clear }

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
    data class Mask(
        val id: Int,
        val points: List<Offset>,
        val width: Float,
        val style: MaskStyle,
        val shape: MaskShape = MaskShape.Brush,
    ) : Edit

    /** A [kind] of mark through or between [points], [width] wide. */
    data class Mark(val id: Int, val kind: MarkKind, val points: List<Offset>, val width: Float, val color: Color) : Edit

    /**
     * Rubs out what was drawn on [layer] before it, along a stroke through [points], [width] wide;
     * under rubbed-out masks the picture shows again.
     */
    data class Erase(val points: List<Offset>, val width: Float, val layer: Layer) : Edit

    /** What an [Erase] rubs out: masks or marks. */
    enum class Layer { Masks, Marks }

    /** Takes away the mask, mark or caption [id]. */
    data class Remove(val id: Int) : Edit

    /** Several steps taken as one, undone together: the masks one slide of a finger laid. */
    data class Group(val edits: List<Edit>) : Edit

    /** Adjusts the picture's tone and colour by [values], in place of any adjustment before. */
    data class Adjust(val values: Map<Adjustment, Float>) : Edit

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

    /** The steps applied, those taken together one by one. */
    private val taken: List<Edit> get() = applied.flatMap { if (it is Edit.Group) it.edits else listOf(it) }

    /** The picture's adjustments as they stand. */
    val adjustments: Map<Adjustment, Float>
        get() = taken.lastOrNull { it is Edit.Adjust }?.let { (it as Edit.Adjust).values } ?: NoAdjustments

    val framing: Framing get() = taken.lastOrNull { it is Edit.Frame }?.let { (it as Edit.Frame).framing } ?: whole

    /**
     * The strokes, masks, marks and what rubs them out, in the order they were made; a mask or
     * mark changed later stays in its place, as it now is, and one removed is gone.
     */
    val strokes: List<Edit>
        get() {
            val latest = HashMap<Int, Edit>()
            val taken = taken
            for (edit in taken) {
                when (edit) {
                    is Edit.Remove -> latest.remove(edit.id)
                    else -> edit.objectId?.let { latest[it] = edit }
                }
            }
            val seen = HashSet<Int>()
            return taken.mapNotNull { edit ->
                when (edit) {
                    is Edit.Erase -> edit
                    is Edit.Mask, is Edit.Mark -> edit.objectId?.takeIf(seen::add)?.let(latest::get)
                    else -> null
                }
            }
        }

    /** The captions as they stand, each once, in the order they were added. */
    val captions: List<Edit.Caption>
        get() {
            val latest = LinkedHashMap<Int, Edit.Caption>()
            for (edit in taken) {
                if (edit is Edit.Caption) latest[edit.id] = edit
                if (edit is Edit.Remove) latest.remove(edit.id)
            }
            return latest.values.filter { it.text.isNotBlank() }
        }

    /** An id nothing drawn or written has had. */
    val nextId: Int
        get() = (
            steps.flatMap { if (it is Edit.Group) it.edits else listOf(it) }.maxOfOrNull {
                when (it) {
                    is Edit.Caption -> it.id
                    is Edit.Mask -> it.id
                    is Edit.Mark -> it.id
                    else -> 0
                }
            } ?: 0
            ) + 1

    /** Starts from [edits] already taken, which undo can take back. */
    fun restore(edits: List<Edit>) {
        steps.clear()
        steps.addAll(edits)
        position = steps.size
    }

    fun push(edit: Edit) {
        while (steps.size > position) steps.removeAt(steps.lastIndex)
        steps.add(edit)
        position = steps.size
    }

    /**
     * Pushes [edit], or, when the last step changed the same mask or mark and nothing was undone,
     * puts it in that step's place: a colour slid through many values is one step.
     */
    fun pushOrMerge(edit: Edit) {
        val last = steps.getOrNull(position - 1)
        if (position == steps.size && last != null && last.objectId != null && last.objectId == edit.objectId &&
            steps.subList(0, position - 1).any { step ->
                step.objectId == edit.objectId || (step is Edit.Group && step.edits.any { it.objectId == edit.objectId })
            }
        ) {
            steps[position - 1] = edit
        } else {
            push(edit)
        }
    }

    fun undo() {
        if (canUndo) position--
    }

    fun redo() {
        if (canRedo) position++
    }
}

/** The id of a mask, mark or caption, which later steps change it by; null for other steps. */
val Edit.objectId: Int?
    get() = when (this) {
        is Edit.Mask -> id
        is Edit.Mark -> id
        is Edit.Caption -> id
        else -> null
    }

private fun Rect.scaled(factor: Float): Rect {
    val half = Size(width * factor / 2, height * factor / 2)
    return Rect(center.x - half.width, center.y - half.height, center.x + half.width, center.y + half.height)
}
