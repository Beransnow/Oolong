package oolong.client.ui.page.editor

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.withMatrix
import androidx.core.graphics.withSave
import oolong.client.ui.common.theme.compose.SmoothCornerShape
import oolong.client.ui.common.theme.compose.addSmoothRect
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.ui.graphics.Path as ComposePath

/** Which layer a stroke is drawn on or rubs out. */
private val Edit.layer: Edit.Layer?
    get() = when (this) {
        is Edit.Mask -> Edit.Layer.Masks
        is Edit.Mark -> Edit.Layer.Marks
        is Edit.Erase -> layer
        else -> null
    }

/** A highlighter's breadth, in widths of the pen at the same size. */
private const val HighlighterWidth = 3.5f

/** A numbered dot's radius, in widths of the pen at the same size. */
private const val NumberRadius = 2.8f

/**
 * What a style's preview hides: a round picture in colours and two lines of text, on a light
 * ground, [width] by [height].
 */
private fun styleSample(width: Int, height: Int): Bitmap = createBitmap(width, height).also { bitmap ->
    val w = width.toFloat()
    val h = height.toFloat()
    Canvas(bitmap).apply {
        drawColor(Color(0xFFF1F2F4).toArgb())
        val r = h * 0.24f
        val cx = h * 0.12f + r
        val cy = h / 2
        drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = SweepGradient(cx, cy, intArrayOf(0xFFFF8A3D.toInt(), 0xFFE94B8A.toInt(), 0xFF5B7CFA.toInt(), 0xFF2EC4B6.toInt(), 0xFFFF8A3D.toInt()), null)
        })
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD
            textSize = h * 0.2f
            color = Color(0xFF2B2D31).toArgb()
        }
        val x = cx + r + h * 0.12f
        drawText("用户昵称", x, h * 0.44f, text)
        text.apply {
            typeface = Typeface.DEFAULT
            textSize = h * 0.17f
            color = Color(0xFF6B6F76).toArgb()
        }
        drawText("138 0013 8000", x, h * 0.72f, text)
    }
}

/** The colour a block mask fills with. */
private val BlockColor = Color(0xFF202124)

/**
 * Draws [picture] with its edits, the same way on screen and when written out. Drawing is in the
 * turned picture's pixels: the caller sets up the canvas from there to the screen or the output.
 */
class EditRenderer(val picture: Bitmap) {
    val size = Size(picture.width.toFloat(), picture.height.toFloat())

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /**
     * What is drawn as the picture: the picture itself, or a copy adjusted, perhaps smaller, drawn
     * stretched to the picture's size. Masks take their colours from it too.
     */
    var base by mutableStateOf(picture)

    private var masksFrom: Bitmap? = null
    private var mosaicShader: Shader? = null
    private var blurShader: Shader? = null

    /** The mask shaders for [base], made again when it changes. */
    private fun maskShaders() {
        if (masksFrom === base) return
        masksFrom = base
        mosaicShader = null
        blurShader = null
        boxShaders.clear()
        clearShaders.clear()
    }

    // A boxed mask's own shader, by its style and box, while the base stays the same
    private val boxShaders = HashMap<Pair<MaskStyle, RectF>, Shader?>()

    /**
     * A boxed mask's shader, made from what is inside its [box] alone: mosaic cells laid from its
     * corner, blur kept within it. A mask over text on white then leaves the white as it was,
     * rather than greyed by what lies around the box.
     */
    private fun boxShader(style: MaskStyle, box: RectF): Shader? {
        if (style == MaskStyle.Block) return null
        if (style == MaskStyle.Clear) return clearShader(Edit.Mask(-1, emptyList(), 0f, style, MaskShape.Rect), box, null)
        maskShaders()
        // A box being drawn is a new one on every frame
        if (boxShaders.size > 64) boxShaders.clear()
        return boxShaders.getOrPut(style to RectF(box)) {
            // The box in the base's pixels, which may be fewer than the picture's
            val k = base.width / size.width
            val left = (box.left * k).toInt().coerceIn(0, base.width - 1)
            val top = (box.top * k).toInt().coerceIn(0, base.height - 1)
            val right = ceil(box.right * k).toInt().coerceIn(left + 1, base.width)
            val bottom = ceil(box.bottom * k).toInt().coerceIn(top + 1, base.height)
            val inside = Bitmap.createBitmap(base, left, top, right - left, bottom - top)
            // Where the crop lies in the picture
            val x = left / k
            val y = top / k
            val w = (right - left) / k
            val h = (bottom - top) / k
            when (style) {
                MaskStyle.Mosaic -> {
                    val cell = max(8f, min(size.width, size.height) / 45f)
                    val small = shrink(inside, ceil(w / cell).toInt(), ceil(h / cell).toInt())
                    BitmapShader(small, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                        setLocalMatrix(Matrix().apply {
                            setScale(w / small.width, h / small.height)
                            postTranslate(x, y)
                        })
                    }
                }

                else -> {
                    val factor = max(4f, min(size.width, size.height) / 135f)
                    val small = shrink(inside, ceil(w / factor).toInt(), ceil(h / factor).toInt())
                    repeat(3) { boxBlur(small, radius = 2) }
                    BitmapShader(small, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                        setLocalMatrix(Matrix().apply {
                            setScale(w / small.width, h / small.height)
                            postTranslate(x, y)
                        })
                    }
                }
            }
        }
    }

    // A cleared mask's shader, by the mask, while the base stays the same: a stroke being drawn
    // is a new one on every frame, so only the last few are kept
    private val clearShaders = object : LinkedHashMap<Any, Shader>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, Shader>?) = size > 12
    }

    /**
     * A cleared mask's shader: what lies under [box], or under the brush [path] within it, filled
     * from the pixels around it, as if nothing were there.
     */
    private fun clearShader(edit: Edit.Mask, box: RectF, path: Path?): Shader {
        maskShaders()
        val key: Any = if (path == null) RectF(box) else edit
        return clearShaders.getOrPut(key) {
            // The box and a ring around it to fill from, in the base's pixels
            val k = base.width / size.width
            val ring = 3
            val left = (box.left * k).toInt().minus(ring).coerceIn(0, base.width - 1)
            val top = (box.top * k).toInt().minus(ring).coerceIn(0, base.height - 1)
            val right = (ceil(box.right * k).toInt() + ring).coerceIn(left + 1, base.width)
            val bottom = (ceil(box.bottom * k).toInt() + ring).coerceIn(top + 1, base.height)
            val w = right - left
            val h = bottom - top
            val region = Bitmap.createBitmap(base, left, top, w, h)
            val hole = BooleanArray(w * h)
            if (path == null) {
                val x0 = (box.left * k).toInt() - left
                val y0 = (box.top * k).toInt() - top
                val x1 = ceil(box.right * k).toInt() - left
                val y1 = ceil(box.bottom * k).toInt() - top
                for (y in max(y0, 0) until min(y1, h)) for (x in max(x0, 0) until min(x1, w)) hole[y * w + x] = true
            } else {
                // The stroke laid down a little broader, so its soft edge shows the fill, not what was there
                val shape = createBitmap(w, h, Bitmap.Config.ALPHA_8)
                Canvas(shape).apply {
                    translate(-left.toFloat(), -top.toFloat())
                    scale(k, k)
                    drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = edit.width + 2f / k
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    })
                }
                val alpha = ByteArray(w * h)
                shape.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(alpha))
                for (i in hole.indices) hole[i] = alpha[i] != 0.toByte()
            }
            BitmapShader(filled(region, hole), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply {
                    setScale(1f / k, 1f / k)
                    postTranslate(left / k, top / k)
                })
            }
        }
    }

    /** Mosaic cells as big as about half a line of text in a screenshot, so it can't be read back. */
    private val mosaic: Shader
        get() {
            maskShaders()
            return mosaicShader ?: run {
                val cell = max(8f, min(size.width, size.height) / 45f)
                val small = shrink(base, ceil(size.width / cell).toInt(), ceil(size.height / cell).toInt())
                BitmapShader(small, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    setLocalMatrix(Matrix().apply { setScale(size.width / small.width, size.height / small.height) })
                }.also { mosaicShader = it }
            }
        }

    private val blur: Shader
        get() {
            maskShaders()
            return blurShader ?: run {
                val factor = max(4f, min(size.width, size.height) / 135f)
                val small = shrink(base, ceil(size.width / factor).toInt(), ceil(size.height / factor).toInt())
                repeat(3) { boxBlur(small, radius = 2) }
                BitmapShader(small, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    setLocalMatrix(Matrix().apply { setScale(size.width / small.width, size.height / small.height) })
                }.also { blurShader = it }
            }
        }

    private fun Canvas.drawBase() {
        val base = base
        if (base === picture) drawBitmap(picture, 0f, 0f, bitmapPaint) else drawBitmap(base, null, RectF(0f, 0f, size.width, size.height), bitmapPaint)
    }

    /** From the picture's pixels to the turned picture's, as [framing] turns it. */
    fun orient(framing: Framing): Matrix = Matrix().apply {
        val turned = framing.turned(size)
        postTranslate(-size.width / 2, -size.height / 2)
        postRotate(-90f * framing.turns)
        if (framing.flipped) postScale(-1f, 1f)
        postRotate(framing.angle)
        postTranslate(turned.width / 2, turned.height / 2)
    }

    /** The picture, its strokes, [live] (one being drawn) after them, and its captions but [hidden]. */
    fun draw(
        canvas: Canvas,
        framing: Framing,
        strokes: List<Edit>,
        captions: List<Edit.Caption>,
        live: Edit? = null,
        hidden: Int? = null,
    ) {
        val orient = orient(framing)
        val all = if (live != null) strokes + live else strokes
        canvas.withMatrix(orient) {
            drawBase()
            // Masks, then marks, each on a layer of their own, so rubbing out one leaves the other
            // and shows the picture under it
            for (layer in Edit.Layer.entries) {
                val save = saveLayer(0f, 0f, size.width, size.height, null)
                // Numbered dots count up in the order they were put
                number = 0
                for (stroke in all) if (stroke.layer == layer) drawStroke(this, stroke)
                restoreToCount(save)
            }
        }
        for (caption in captions) if (caption.id != hidden) drawCaption(canvas, caption, orient.map(caption.center))
    }

    private fun maskPaint(style: MaskStyle) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        when (style) {
            MaskStyle.Mosaic -> {
                shader = mosaic
                // Sharp-edged cells: new paints smooth bitmaps by default, which blurs them together
                isFilterBitmap = false
            }
            MaskStyle.Blur -> {
                shader = blur
                isFilterBitmap = true
            }
            MaskStyle.Block -> color = BlockColor.toArgb()
            // Its shader is made for each mask
            MaskStyle.Clear -> isFilterBitmap = true
        }
    }

    /** A dot numbered [number], its radius from the mark's [width]. */
    private fun drawNumber(canvas: Canvas, center: Offset, width: Float, color: Color, number: Int) {
        val radius = width * NumberRadius
        canvas.drawCircle(center.x, center.y, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() })
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD
            textSize = radius * 1.15f
            textAlign = Paint.Align.CENTER
            this.color = (if (color.luma() > 0.6f) Color(0xFF16181C) else Color.White).toArgb()
        }
        canvas.drawText(number.toString(), center.x, center.y - (text.ascent() + text.descent()) / 2, text)
    }

    /**
     * How [style] looks, [width] by [height]: a brush stroke of it across a sample of what is
     * hidden most, a picture and lines of text on a plain ground. The same for every picture,
     * so the styles tell apart on a plain screenshot too.
     */
    fun stylePreview(style: MaskStyle, width: Int, height: Int): Bitmap {
        val sample = EditRenderer(styleSample(width, height))
        val w = width.toFloat()
        val h = height.toFloat()
        // A brush stroke rising gently left to right, over half the picture and the text
        val points = (0..12).map { i ->
            val t = i / 12f
            Offset(w * (0.2f + 0.68f * t), h * (0.6f - 0.18f * t + 0.06f * sin(t * 2 * PI.toFloat())))
        }
        val stroke = Edit.Mask(0, points, h * 0.42f, style)
        return createBitmap(width, height).also { result ->
            Canvas(result).apply {
                sample.run {
                    drawBase()
                    drawStroke(this@apply, stroke)
                }
            }
        }
    }

    /** The number the last numbered dot drawn took. */
    private var number = 0

    private fun drawStroke(canvas: Canvas, edit: Edit) {
        if (edit is Edit.Mark && edit.kind == MarkKind.Number) number++
        when (edit) {
            is Edit.Mask -> {
                val paint = maskPaint(edit.style).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = edit.width
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                if (edit.shape == MaskShape.Rect) {
                    val box = box(edit.points.first(), edit.points.last())
                    val radius = min(min(box.width(), box.height()) * 0.2f, edit.width)
                    boxShader(edit.style, box)?.let { paint.shader = it }
                    canvas.drawSmoothRect(box, radius, paint.apply { style = Paint.Style.FILL })
                } else {
                    val path = smooth(edit.points)
                    if (edit.style == MaskStyle.Clear) {
                        val bounds = RectF().also { path.computeBounds(it, true) }
                        bounds.inset(-edit.width / 2 - 2, -edit.width / 2 - 2)
                        paint.shader = clearShader(edit, bounds, path)
                    }
                    canvas.drawPath(path, paint)
                }
            }

            is Edit.Mark -> {
                val paint = strokePaint(edit.width).apply { color = edit.color.toArgb() }
                val from = edit.points.first()
                val to = edit.points.last()
                when (edit.kind) {
                    MarkKind.Pen -> canvas.drawPath(smooth(edit.points), paint)
                    MarkKind.Highlighter -> canvas.drawPath(smooth(edit.points), paint.apply {
                        // Broad, flat-ended and see-through, so the text under it still reads
                        strokeWidth = edit.width * HighlighterWidth
                        strokeCap = Paint.Cap.BUTT
                        alpha = 110
                    })
                    MarkKind.Line -> canvas.drawLine(from.x, from.y, to.x, to.y, paint)
                    MarkKind.Arrow -> canvas.drawPath(arrow(from, to, edit.width), paint)
                    MarkKind.Box -> canvas.drawSmoothRect(box(from, to), edit.width * 1.5f, paint)
                    MarkKind.Ellipse -> canvas.drawOval(box(from, to), paint)
                    MarkKind.Number -> drawNumber(canvas, from, edit.width, edit.color, number)
                }
            }

            is Edit.Erase -> canvas.drawPath(smooth(edit.points), strokePaint(edit.width).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })

            else -> Unit
        }
    }

    private fun strokePaint(width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** Where [caption] lies drawn at [center], in the turned picture. */
    fun captionBounds(caption: Edit.Caption, framing: Framing): Rect {
        val center = orient(framing).map(caption.center)
        val layout = captionLayout(caption)
        val pad = captionPadding(caption)
        return Rect(
            center.x - layout.width / 2f - pad.x, center.y - layout.height / 2f - pad.y,
            center.x + layout.width / 2f + pad.x, center.y + layout.height / 2f + pad.y,
        )
    }

    private fun captionPadding(caption: Edit.Caption) = Offset(caption.size * 0.35f, caption.size * 0.18f)

    private fun captionLayout(caption: Edit.Caption, paint: TextPaint = captionPaint(caption)): StaticLayout {
        val width = caption.text.lines().maxOf { ceil(Layout.getDesiredWidth(it, paint)).toInt() }.coerceAtLeast(1)
        return StaticLayout.Builder.obtain(caption.text, 0, caption.text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()
    }

    private fun captionPaint(caption: Edit.Caption) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textSize = caption.size
        color = caption.textColor().toArgb()
    }

    private fun drawCaption(canvas: Canvas, caption: Edit.Caption, center: Offset) {
        val paint = captionPaint(caption)
        val layout = captionLayout(caption, paint)
        canvas.withSave {
            translate(center.x - layout.width / 2f, center.y - layout.height / 2f)
            when (caption.style) {
                CaptionStyle.Filled -> {
                    val pad = captionPadding(caption)
                    val radius = caption.size * 0.3f
                    drawSmoothRect(
                        RectF(-pad.x, -pad.y, layout.width + pad.x, layout.height + pad.y), radius,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = caption.color.toArgb() },
                    )
                }

                CaptionStyle.Outline -> {
                    val fill = paint.color
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = caption.size * 0.16f
                    paint.strokeJoin = Paint.Join.ROUND
                    paint.color = caption.outlineColor().toArgb()
                    layout.draw(this)
                    paint.style = Paint.Style.FILL
                    paint.color = fill
                }

                CaptionStyle.Plain -> Unit
            }
            layout.draw(this)
        }
    }

    /**
     * The picture as [framing] turns and cuts it, with [strokes] and [captions], its longer side
     * at most [maxSide].
     */
    fun render(framing: Framing, strokes: List<Edit>, captions: List<Edit.Caption>, maxSide: Int): Bitmap {
        val rect = framing.rect
        val scale = min(1f, maxSide / max(rect.width, rect.height))
        val result = bitmapLike(
            picture,
            (rect.width * scale).roundToInt().coerceAtLeast(1),
            (rect.height * scale).roundToInt().coerceAtLeast(1),
        )
        Canvas(result).apply {
            scale(scale, scale)
            translate(-rect.left, -rect.top)
            draw(this, framing, strokes, captions)
        }
        return result
    }
}

/** The text colour of a caption: on a light fill, dark text. */
fun Edit.Caption.textColor(): Color =
    if (style == CaptionStyle.Filled) (if (color.isLight()) Color(0xFF16181C) else Color.White) else color

/** The outline round an outlined caption: dark, unless the text is dark itself. */
fun Edit.Caption.outlineColor(): Color = if (color.luma() < 0.25f) Color.White else Color.Black.copy(alpha = 0.85f)

internal fun Color.luma() = 0.3f * red + 0.59f * green + 0.11f * blue

private fun Color.isLight() = luma() > 0.6f

private fun Matrix.map(point: Offset): Offset {
    val values = floatArrayOf(point.x, point.y)
    mapPoints(values)
    return Offset(values[0], values[1])
}

/** A line through [points], rounded through each with a curve from midpoint to midpoint. */
private fun smooth(points: List<Offset>): Path = Path().apply {
    val first = points.first()
    moveTo(first.x, first.y)
    if (points.size == 1) {
        lineTo(first.x + 0.1f, first.y)
        return@apply
    }
    for (i in 1 until points.size - 1) {
        val p = points[i]
        val next = points[i + 1]
        quadTo(p.x, p.y, (p.x + next.x) / 2, (p.y + next.y) / 2)
    }
    val last = points.last()
    lineTo(last.x, last.y)
}

/** [rect] with smooth corners of [radius], as the app's own corners are. */
private fun Canvas.drawSmoothRect(rect: RectF, radius: Float, paint: Paint) {
    val path = ComposePath().apply {
        addSmoothRect(rect.width(), rect.height(), radius, SmoothCornerShape.DefaultSmoothing)
        translate(Offset(rect.left, rect.top))
    }
    drawPath(path.asAndroidPath(), paint)
}

private fun box(a: Offset, b: Offset) = RectF(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))

private fun arrow(from: Offset, to: Offset, width: Float): Path = Path().apply {
    moveTo(from.x, from.y)
    lineTo(to.x, to.y)
    val angle = atan2(to.y - from.y, to.x - from.x)
    val head = width * 3.5f + 8f
    for (side in floatArrayOf(-0.5f, 0.5f)) {
        moveTo(to.x - head * cos(angle + side), to.y - head * sin(angle + side))
        lineTo(to.x, to.y)
    }
}

/** [source] shrunk to [width] by [height] by halving first, so every pixel counts toward the result. */
private fun shrink(source: Bitmap, width: Int, height: Int): Bitmap {
    var current = source
    while (current.width / 2 >= width && current.height / 2 >= height) {
        current = current.scale(current.width / 2, current.height / 2)
    }
    return current.scale(width.coerceAtLeast(1), height.coerceAtLeast(1)).copy(Bitmap.Config.ARGB_8888, true)
}

/** Blurs [bitmap] in place, averaging each pixel with [radius] on either side, across then down. */
private fun boxBlur(bitmap: Bitmap, radius: Int) {
    val w = bitmap.width
    val h = bitmap.height
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
    val out = IntArray(w * h)
    fun pass(src: IntArray, dst: IntArray, length: Int, lines: Int, index: (line: Int, i: Int) -> Int) {
        for (line in 0 until lines) {
            for (i in 0 until length) {
                var a = 0
                var r = 0
                var g = 0
                var b = 0
                var n = 0
                for (k in -radius..radius) {
                    val c = src[index(line, (i + k).coerceIn(0, length - 1))]
                    a += c ushr 24
                    r += (c shr 16) and 0xFF
                    g += (c shr 8) and 0xFF
                    b += c and 0xFF
                    n++
                }
                dst[index(line, i)] = ((a / n) shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
    }
    pass(pixels, out, w, h) { line, i -> line * w + i }
    pass(out, pixels, h, w) { line, i -> i * w + line }
    bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
}
