package com.huanchengfly.tieba.post.components.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import coil3.size.Size
import coil3.transform.Transformation
import com.huanchengfly.tieba.post.components.transformations.internal.FastBlur
import com.huanchengfly.tieba.post.components.transformations.internal.RSBlur
import com.huanchengfly.tieba.post.components.transformations.internal.SupportRSBlur

/** Blurs the image by [radius], first scaling it down by [sampling]. */
class BlurTransformation(
    context: Context,
    private val radius: Int = 25,
    private val sampling: Int = 1,
) : Transformation() {
    private val context = context.applicationContext

    override val cacheKey: String = "BlurTransformation(radius=$radius, sampling=$sampling)"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val scaled = createBitmap(input.width / sampling, input.height / sampling, input.config ?: Bitmap.Config.ARGB_8888)
        scaled.density = input.density
        Canvas(scaled).apply {
            scale(1 / sampling.toFloat(), 1 / sampling.toFloat())
            drawBitmap(input, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        return try {
            SupportRSBlur.blur(context, scaled, radius)
        } catch (e: NoClassDefFoundError) {
            RSBlur.blur(context, scaled, radius)
        } catch (e: RuntimeException) {
            FastBlur.blur(scaled, radius, true)
        }
    }
}
