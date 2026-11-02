package com.huanchengfly.tieba.post.ui.page.settings.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.DisplayMetrics
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.palette.graphics.Palette
import coil3.request.transformations
import coil3.size.Precision
import coil3.size.Scale
import com.huanchengfly.tieba.post.components.images.BlurTransformation
import com.huanchengfly.tieba.post.components.images.loadBitmap
import com.huanchengfly.tieba.post.dataStore
import com.huanchengfly.tieba.post.utils.ImageUtil
import com.huanchengfly.tieba.post.utils.ThemeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * How the translucent theme draws its wallpaper. The picked image is kept as is; the blurred and
 * dimmed copy the app draws is made from it, so the settings can be changed again later.
 *
 * @param darkText dark text over a light wallpaper, as opposed to light text over a dark one
 * @param dim how far the wallpaper fades to black under light text, or to white under dark text
 * @param blur how strong the blur is, 0 to [MAX_BLUR]
 * @param accent the theme's emphasis colour as ARGB, or null for the default
 */
data class TranslucentWallpaper(
    val darkText: Boolean = false,
    val dim: Float = DEFAULT_DIM,
    val blur: Int = 0,
    val accent: Int? = null,
) {
    /** The colour the wallpaper fades to. */
    val dimColor: Int get() = if (darkText) Color.WHITE else Color.BLACK

    /**
     * The blur for [blur], or null for none: up to [MAX_RADIUS] a plain radius, beyond it the image
     * shrunk first so that the same radius covers more of it.
     */
    fun blurTransformation(context: Context): BlurTransformation? {
        if (blur <= 0) return null
        val sampling = (blur + MAX_RADIUS - 1) / MAX_RADIUS
        return BlurTransformation(context, radius = (blur / sampling).coerceIn(1, MAX_RADIUS), sampling = sampling)
    }

    /** Where [blur] sits on a slider from 0 to 1, as [blurAt] places it. */
    val blurPosition: Float get() = sqrt(blur.toFloat() / MAX_BLUR)

    /** The theme name whose colours go with [darkText]. */
    val variant: String
        get() = if (darkText) ThemeUtil.THEME_TRANSLUCENT_DARK else ThemeUtil.THEME_TRANSLUCENT_LIGHT

    companion object {
        const val DEFAULT_DIM = 0.3f
        const val MAX_DIM = 0.8f
        const val MAX_BLUR = 100

        /** The largest radius the blur takes; stronger blurs shrink the image first instead. */
        private const val MAX_RADIUS = 25

        /**
         * The blur at [position] on a slider from 0 to 1. How blurred an image looks grows ever
         * more slowly with the radius, so the slider gives its first half to the light blurs,
         * where each step shows, and squeezes the strong ones into the rest.
         */
        fun blurAt(position: Float): Int = (MAX_BLUR * position * position).roundToInt().coerceIn(0, MAX_BLUR)

        /** The image picked for the wallpaper, before blurring and dimming. */
        fun source(context: Context) = File(context.filesDir, "cropped_background.jpg")

        suspend fun saved(context: Context): TranslucentWallpaper {
            val prefs = context.dataStore.data.first()
            return TranslucentWallpaper(
                darkText = prefs[ThemeKey] == ThemeUtil.TRANSLUCENT_THEME_DARK,
                dim = prefs[DimKey] ?: DEFAULT_DIM,
                blur = (prefs[BlurKey] ?: 0).coerceIn(0, MAX_BLUR),
                accent = prefs[AccentKey]?.let { runCatching { Color.parseColor(it) }.getOrNull() },
            )
        }

        private val PathKey = stringPreferencesKey(ThemeUtil.KEY_TRANSLUCENT_THEME_BACKGROUND_PATH)
        private val ThemeKey = intPreferencesKey("translucent_background_theme")
        private val DimKey = floatPreferencesKey("translucent_wallpaper_dim")
        private val BlurKey = intPreferencesKey("translucent_background_blur")
        private val AccentKey = stringPreferencesKey(ThemeUtil.KEY_TRANSLUCENT_PRIMARY_COLOR)

        /**
         * Saves [wallpaper] with the drawn wallpaper at [path], all at once and awaited: the theme
         * switch that follows reads them straight away. Returns the path it replaces.
         */
        internal suspend fun save(context: Context, wallpaper: TranslucentWallpaper, path: String): String? {
            var old: String? = null
            context.dataStore.edit {
                old = it[PathKey]
                it[PathKey] = path
                it[ThemeKey] = if (wallpaper.darkText) ThemeUtil.TRANSLUCENT_THEME_DARK else ThemeUtil.TRANSLUCENT_THEME_LIGHT
                it[DimKey] = wallpaper.dim
                it[BlurKey] = wallpaper.blur
                if (wallpaper.accent != null) it[AccentKey] = String.format("#%08X", wallpaper.accent) else it.remove(AccentKey)
            }
            return old
        }
    }
}

/** The wallpaper as the app draws it: [source] at screen size, blurred and dimmed. */
suspend fun Context.renderWallpaper(source: File, wallpaper: TranslucentWallpaper): Bitmap? {
    val metrics: DisplayMetrics = resources.displayMetrics
    val image = loadBitmap(source) {
        size(metrics.widthPixels, metrics.heightPixels)
        scale(Scale.FILL)
        precision(Precision.EXACT)
        memoryCacheKey("translucent:${source.lastModified()}:${wallpaper.blur}")
        wallpaper.blurTransformation(this@renderWallpaper)?.let { transformations(it) }
    } ?: return null
    return withContext(Dispatchers.Default) {
        // A strong blur comes back shrunk; draw it at screen size again
        val out = createBitmap(metrics.widthPixels, metrics.heightPixels)
        Canvas(out).apply {
            drawBitmap(image, null, Rect(0, 0, out.width, out.height), Paint(Paint.FILTER_BITMAP_FLAG))
            drawColor(ColorUtils.setAlphaComponent(wallpaper.dimColor, (wallpaper.dim * 255).toInt()))
        }
        out
    }
}

/** Colours taken from the wallpaper to offer as the accent, most prominent first. */
suspend fun Context.wallpaperColors(source: File): List<Int> {
    // Keyed by when the file changed: a newly picked image is saved under the same name
    val image = loadBitmap(source) {
        size(256)
        memoryCacheKey("translucent-colors:${source.lastModified()}")
    } ?: return emptyList()
    return withContext(Dispatchers.Default) {
        val palette = Palette.from(image).generate()
        listOfNotNull(
            palette.vibrantSwatch,
            palette.lightVibrantSwatch,
            palette.darkVibrantSwatch,
            palette.mutedSwatch,
            palette.lightMutedSwatch,
            palette.darkMutedSwatch,
        ).map { it.rgb }.distinct()
    }
}

/**
 * Saves [wallpaper] and makes the translucent theme current: the drawn wallpaper goes to a new
 * file, which replaces the old one. False when [source] cannot be drawn.
 */
suspend fun Context.applyWallpaper(source: File, wallpaper: TranslucentWallpaper): Boolean {
    val bitmap = renderWallpaper(source, wallpaper) ?: return false
    val file = withContext(Dispatchers.IO) {
        ImageUtil.compressImage(
            bitmap,
            File(filesDir, "background_${System.currentTimeMillis()}.jpg"),
            maxSizeKb = 512,
            initialQuality = 97,
        )
    }
    val old = TranslucentWallpaper.save(this, wallpaper, file.absolutePath)
    if (old != null && old != file.absolutePath) File(old).delete()
    ThemeUtil.setWallpaper(true)
    return true
}
