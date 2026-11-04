package oolong.client.ui.page.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * What an edited picture was made from: the picture it started as and the steps taken, kept
 * beside it so it can be opened again where the editing left off.
 */
@Serializable
class EditRecord(val source: String, val width: Int, val height: Int, val steps: List<Step>) {
    @Serializable
    class Step(
        val type: String,
        val id: Int = 0,
        val points: List<Float> = emptyList(),
        val width: Float = 0f,
        val color: Long = 0,
        val kind: String = "",
        val text: String = "",
        val turns: Int = 0,
        val flipped: Boolean = false,
        val angle: Float = 0f,
    )

    /** The steps as edits, for a picture decoded at [width] by [height]; null if it came out another size. */
    fun edits(width: Int, height: Int): List<Edit>? {
        if (width != this.width || height != this.height) return null
        return runCatching { steps.map { it.toEdit() } }.getOrNull()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Where the record of [picture] is kept. */
        fun fileOf(picture: File) = File(picture.path + ".edits")

        fun read(picture: File): EditRecord? = runCatching { json.decodeFromString<EditRecord>(fileOf(picture).readText()) }.getOrNull()

        fun write(picture: File, source: String, width: Int, height: Int, edits: List<Edit>) {
            fileOf(picture).writeText(json.encodeToString(EditRecord(source, width, height, edits.map { it.toStep() })))
        }
    }
}

private fun List<Offset>.flat() = flatMap { listOf(it.x, it.y) }

private fun List<Float>.offsets() = chunked(2).map { (x, y) -> Offset(x, y) }

private fun Edit.toStep(): EditRecord.Step = when (this) {
    is Edit.Frame -> EditRecord.Step(
        type = "frame",
        points = listOf(framing.rect.left, framing.rect.top, framing.rect.right, framing.rect.bottom),
        turns = framing.turns,
        flipped = framing.flipped,
        angle = framing.angle,
    )
    is Edit.Mask -> EditRecord.Step("mask", id, points.flat(), width, kind = "${style.name}/${shape.name}")
    is Edit.Mark -> EditRecord.Step("mark", id, points.flat(), width, color.toArgb().toLong(), kind.name)
    is Edit.Erase -> EditRecord.Step("erase", points = points.flat(), width = width, kind = layer.name)
    is Edit.Caption -> EditRecord.Step("caption", id, listOf(center.x, center.y), size, color.toArgb().toLong(), style.name, text)
    is Edit.Remove -> EditRecord.Step("remove", id)
    is Edit.Adjust -> EditRecord.Step("adjust", points = Adjustment.entries.map { values[it] ?: 0f })
}

private fun EditRecord.Step.toEdit(): Edit = when (type) {
    "frame" -> Edit.Frame(Framing(turns, flipped, Rect(points[0], points[1], points[2], points[3]), angle))
    "mask" -> {
        val (style, shape) = kind.split('/')
        Edit.Mask(id, points.offsets(), width, MaskStyle.valueOf(style), MaskShape.valueOf(shape))
    }
    "mark" -> Edit.Mark(id, MarkKind.valueOf(kind), points.offsets(), width, Color(color.toInt()))
    "erase" -> Edit.Erase(points.offsets(), width, Edit.Layer.valueOf(kind))
    "caption" -> Edit.Caption(id, text, Offset(points[0], points[1]), width, Color(color.toInt()), CaptionStyle.valueOf(kind))
    "remove" -> Edit.Remove(id)
    "adjust" -> Edit.Adjust(Adjustment.entries.zip(points).toMap().filterValues { it != 0f })
    else -> error("Unknown step $type")
}
