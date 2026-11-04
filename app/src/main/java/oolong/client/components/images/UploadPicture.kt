package oolong.client.components.images

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.ColorSpace
import android.graphics.Rect
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A picture made ready to upload: the file to send, and its size as it will show. */
class UploadPicture(val file: File, val width: Int, val height: Int)

/** Why a picture could not be made ready to upload, said to the person. */
class UploadPictureException(message: String) : Exception(message)

/**
 * Makes [source] ready to upload into [output], no larger than [maxBytes] and, when it has to be
 * drawn again, no more than [maxPixels].
 *
 * A JPEG, PNG or GIF that is small enough and already upright goes as it is, not a pixel changed,
 * with only where and on what it was taken wiped from its EXIF: a long screenshot stays as sharp
 * as it was. Any other is drawn again as an upright sRGB JPEG, its quality lowered before its
 * size. It is shrunk by averaging every pixel it covers, in linear light, read a strip at a time,
 * so text stays whole and a very long picture never has to be held at once.
 */
fun preparePictureForUpload(source: File, output: File, maxBytes: Long, maxPixels: Long): UploadPicture {
    val format = PictureFormat.of(source)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(source.path, bounds)
    val width = bounds.outWidth
    val height = bounds.outHeight
    if (width <= 0 || height <= 0) throw UploadPictureException("无法读取这张图片")
    val orientation = if (format == PictureFormat.Gif) {
        ExifInterface.ORIENTATION_NORMAL
    } else {
        runCatching { ExifInterface(source).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    }
    val upright = orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED
    if (format in AsTheyAre && upright && source.length() <= maxBytes) {
        source.copyTo(output, overwrite = true)
        if (format == PictureFormat.Gif || wipePrivateExif(output)) return UploadPicture(output, width, height)
    }
    return redraw(source, format, width, height, orientation, output, maxBytes, maxPixels)
}

/** What a server takes as it is. */
private val AsTheyAre = setOf(PictureFormat.Jpeg, PictureFormat.Png, PictureFormat.Gif)

/** The qualities a JPEG is tried at, best first, before the picture is made smaller. */
private val Qualities = intArrayOf(95, 85, 75)

/** The longest side a JPEG can have. */
private const val JpegMaxSide = 65_500

/** About how many bytes of the picture are read at once. */
private const val StripBytes = 8 shl 20

/** Where, when and with what a picture was taken: what goes before a picture is shared. */
private val PrivateTags = listOf(
    ExifInterface.TAG_GPS_VERSION_ID, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LATITUDE,
    ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
    ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
    ExifInterface.TAG_GPS_SATELLITES, ExifInterface.TAG_GPS_STATUS, ExifInterface.TAG_GPS_MEASURE_MODE,
    ExifInterface.TAG_GPS_DOP, ExifInterface.TAG_GPS_SPEED_REF, ExifInterface.TAG_GPS_SPEED,
    ExifInterface.TAG_GPS_TRACK_REF, ExifInterface.TAG_GPS_TRACK, ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
    ExifInterface.TAG_GPS_IMG_DIRECTION, ExifInterface.TAG_GPS_MAP_DATUM, ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
    ExifInterface.TAG_GPS_DEST_LATITUDE, ExifInterface.TAG_GPS_DEST_LONGITUDE_REF, ExifInterface.TAG_GPS_DEST_LONGITUDE,
    ExifInterface.TAG_GPS_DEST_BEARING_REF, ExifInterface.TAG_GPS_DEST_BEARING, ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
    ExifInterface.TAG_GPS_DEST_DISTANCE, ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_GPS_AREA_INFORMATION,
    ExifInterface.TAG_GPS_DIFFERENTIAL, ExifInterface.TAG_GPS_H_POSITIONING_ERROR,
    ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_SOFTWARE,
    ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
    ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
    ExifInterface.TAG_SUBSEC_TIME, ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
    ExifInterface.TAG_ARTIST, ExifInterface.TAG_CAMERA_OWNER_NAME, ExifInterface.TAG_BODY_SERIAL_NUMBER,
    ExifInterface.TAG_LENS_MAKE, ExifInterface.TAG_LENS_MODEL, ExifInterface.TAG_LENS_SERIAL_NUMBER,
    ExifInterface.TAG_IMAGE_UNIQUE_ID, ExifInterface.TAG_USER_COMMENT,
)

/** Wipes [PrivateTags] from [file] without drawing it again; false if that could not be done. */
private fun wipePrivateExif(file: File): Boolean = runCatching {
    val exif = ExifInterface(file)
    val present = PrivateTags.filter { exif.getAttribute(it) != null }
    if (present.isNotEmpty()) {
        present.forEach { exif.setAttribute(it, null) }
        exif.saveAttributes()
    }
}.isSuccess

private fun redraw(
    source: File,
    format: PictureFormat?,
    width: Int,
    height: Int,
    orientation: Int,
    output: File,
    maxBytes: Long,
    maxPixels: Long,
): UploadPicture {
    // Two copies are held at the end, the pixels and the bitmap made of them
    val affordable = Runtime.getRuntime().maxMemory() / 16
    var scale = minOf(1.0, sqrt(min(maxPixels, affordable).toDouble() / (width.toLong() * height)), JpegMaxSide.toDouble() / max(width, height))
    repeat(4) {
        val bitmap = shrink(source, format, width, height, scale, orientation)
        try {
            var bytes = ByteArray(0)
            for (quality in Qualities) {
                bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
                if (bytes.size <= maxBytes) {
                    output.writeBytes(bytes)
                    return UploadPicture(output, bitmap.width, bitmap.height)
                }
            }
            // Still too large at the lowest quality: fewer pixels, by about as much as it is over
            scale *= sqrt(maxBytes.toDouble() / bytes.size) * 0.9
        } finally {
            bitmap.recycle()
        }
    }
    throw UploadPictureException("图片太大，压缩后仍超过限制")
}

/**
 * [source], [width] by [height], made [scale] times as large and turned upright by its EXIF
 * [orientation], in sRGB on white.
 */
private fun shrink(source: File, format: PictureFormat?, width: Int, height: Int, scale: Double, orientation: Int): Bitmap {
    val outWidth = max(1, (width * scale).roundToInt())
    val outHeight = max(1, (height * scale).roundToInt())
    // A JPEG can be read smaller to begin with, averaged as it is decoded; others would only be
    // read a pixel in so many, which breaks text, so they are read whole and averaged here
    var sample = 1
    if (format == PictureFormat.Jpeg) while (sample * 2 <= 1 / scale) sample *= 2
    val reducer = BoxReducer(ceil(width.toDouble() / sample).toInt(), ceil(height.toDouble() / sample).toInt(), outWidth, outHeight, orientation)
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
    }
    val regions = runCatching { regionDecoder(source) }.getOrNull()
    if (regions != null) {
        try {
            // Strips of whole sampled rows, so each starts where the last left off
            val rows = max(sample, (StripBytes / (width.toLong() * 4)).toInt() / sample * sample)
            var top = 0
            while (top < height) {
                val bottom = min(height, top + rows)
                val strip = regions.decodeRegion(Rect(0, top, width, bottom), options) ?: throw UploadPictureException("无法读取这张图片")
                reducer.add(strip)
                strip.recycle()
                top = bottom
            }
        } finally {
            regions.recycle()
        }
    } else {
        // Not readable in parts, as a GIF: read whole
        val whole = BitmapFactory.decodeFile(source.path, options) ?: throw UploadPictureException("无法读取这张图片")
        reducer.add(whole)
        whole.recycle()
    }
    return reducer.finish()
}

private fun regionDecoder(source: File): BitmapRegionDecoder? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        BitmapRegionDecoder.newInstance(source.path)
    } else {
        @Suppress("DEPRECATION")
        BitmapRegionDecoder.newInstance(source.path, false)
    }

/** sRGB values as linear light. */
private val ToLinear = FloatArray(256) { i ->
    val c = i / 255f
    if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
}

private const val LinearSteps = 4096

/** Linear light, in [LinearSteps], as sRGB values. */
private val ToSrgb = IntArray(LinearSteps + 1) { i ->
    val l = i.toFloat() / LinearSteps
    val c = if (l <= 0.0031308f) l * 12.92f else 1.055f * l.pow(1 / 2.4f) - 0.055f
    (c * 255f).roundToInt().coerceIn(0, 255)
}

/**
 * Shrinks a picture [width] by [height], fed a strip of rows at a time from the top, to
 * [outWidth] by [outHeight]: each pixel made the average, in linear light, of all it covers,
 * part pixels by how much of them. Written turned by an EXIF [orientation].
 */
private class BoxReducer(
    private val width: Int,
    private val height: Int,
    private val outWidth: Int,
    private val outHeight: Int,
    private val orientation: Int,
) {
    private val scaleX = width.toDouble() / outWidth
    private val scaleY = height.toDouble() / outHeight
    private val turned = orientation in setOf(
        ExifInterface.ORIENTATION_TRANSPOSE,
        ExifInterface.ORIENTATION_ROTATE_90,
        ExifInterface.ORIENTATION_TRANSVERSE,
        ExifInterface.ORIENTATION_ROTATE_270,
    )
    private val finalWidth = if (turned) outHeight else outWidth
    private val finalHeight = if (turned) outWidth else outHeight
    private val pixels = IntArray(finalWidth * finalHeight)

    // The columns each output column covers: from, to, and the share of the first and last
    private val columnFrom = IntArray(outWidth)
    private val columnTo = IntArray(outWidth)
    private val firstShare = FloatArray(outWidth)
    private val lastShare = FloatArray(outWidth)

    // One source row averaged across, and the output rows being gathered, red, green, blue each
    private val across = FloatArray(outWidth * 3)
    private val gathered = Array(2) { FloatArray(outWidth * 3) }
    private val weights = FloatArray(2)
    private var gatheredRow = 0
    private var row = 0
    private var buffer = IntArray(0)

    init {
        for (x in 0 until outWidth) {
            val from = x * scaleX
            val to = min(width.toDouble(), (x + 1) * scaleX)
            columnFrom[x] = floor(from).toInt()
            columnTo[x] = min(width, ceil(to).toInt())
            firstShare[x] = (min(to, columnFrom[x] + 1.0) - from).toFloat()
            lastShare[x] = (to - (columnTo[x] - 1)).toFloat()
        }
    }

    fun add(strip: Bitmap) {
        val w = min(strip.width, width)
        if (buffer.size < strip.width) buffer = IntArray(strip.width)
        for (y in 0 until strip.height) {
            if (row >= height) return
            strip.getPixels(buffer, 0, strip.width, 0, y, strip.width, 1)
            acrossRow(w)
            downRow()
            row++
        }
    }

    /** Averages the row in [buffer] across into [across]. */
    private fun acrossRow(w: Int) {
        for (x in 0 until outWidth) {
            var r = 0f
            var g = 0f
            var b = 0f
            var total = 0f
            val to = min(columnTo[x], w)
            for (i in columnFrom[x] until to) {
                val share = when (i) {
                    columnFrom[x] -> firstShare[x]
                    columnTo[x] - 1 -> lastShare[x]
                    else -> 1f
                }
                val c = buffer[i]
                // Laid on white, as a JPEG has no transparency
                val a = (c ushr 24) / 255f
                r += share * (a * ToLinear[(c shr 16) and 0xFF] + 1f - a)
                g += share * (a * ToLinear[(c shr 8) and 0xFF] + 1f - a)
                b += share * (a * ToLinear[c and 0xFF] + 1f - a)
                total += share
            }
            if (total > 0f) {
                across[x * 3] = r / total
                across[x * 3 + 1] = g / total
                across[x * 3 + 2] = b / total
            }
        }
    }

    /** Adds the row in [across] to the output rows it falls in. */
    private fun downRow() {
        val from = row.toDouble()
        val to = row + 1.0
        // A source row is never taller than an output row, so it falls in this one, and maybe the next
        val boundary = (gatheredRow + 1) * scaleY
        val here = (min(to, boundary) - from).toFloat()
        gather(0, here)
        if (to > boundary) {
            gather(1, (to - boundary).toFloat())
            emit()
        } else if (to >= boundary) {
            emit()
        }
    }

    private fun gather(slot: Int, share: Float) {
        if (share <= 0f) return
        val into = gathered[slot]
        for (i in across.indices) into[i] += across[i] * share
        weights[slot] += share
    }

    /** Writes the output row gathered, and moves on to the next. */
    private fun emit() {
        if (gatheredRow < outHeight && weights[0] > 0f) write(gatheredRow, gathered[0], weights[0])
        gatheredRow++
        val done = gathered[0]
        gathered[0] = gathered[1]
        gathered[1] = done.also { it.fill(0f) }
        weights[0] = weights[1]
        weights[1] = 0f
    }

    private fun write(y: Int, values: FloatArray, weight: Float) {
        for (x in 0 until outWidth) {
            fun channel(i: Int) = ToSrgb[(values[x * 3 + i] / weight * LinearSteps).roundToInt().coerceIn(0, LinearSteps)]
            val color = (0xFF shl 24) or (channel(0) shl 16) or (channel(1) shl 8) or channel(2)
            pixels[at(x, y)] = color
        }
    }

    /** Where the pixel at [x], [y] goes once turned upright. */
    private fun at(x: Int, y: Int): Int {
        val w = outWidth
        val h = outHeight
        return when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> y * finalWidth + (w - 1 - x)
            ExifInterface.ORIENTATION_ROTATE_180 -> (h - 1 - y) * finalWidth + (w - 1 - x)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> (h - 1 - y) * finalWidth + x
            ExifInterface.ORIENTATION_TRANSPOSE -> x * finalWidth + y
            ExifInterface.ORIENTATION_ROTATE_90 -> x * finalWidth + (h - 1 - y)
            ExifInterface.ORIENTATION_TRANSVERSE -> (w - 1 - x) * finalWidth + (h - 1 - y)
            ExifInterface.ORIENTATION_ROTATE_270 -> (w - 1 - x) * finalWidth + y
            else -> y * finalWidth + x
        }
    }

    fun finish(): Bitmap {
        // A last row only partly covered by the picture
        if (gatheredRow < outHeight && weights[0] > 0f) write(gatheredRow, gathered[0], weights[0])
        return Bitmap.createBitmap(pixels, finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
    }
}
