package com.huanchengfly.tieba.post.ui.page.settings.theme

import android.content.Context
import android.graphics.Color
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.huanchengfly.tieba.post.dataStore
import com.huanchengfly.tieba.post.testing.LegacyAppRule
import com.huanchengfly.tieba.post.utils.ThemeUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class TranslucentWallpaperTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() = runTest {
        context.dataStore.edit { it.clear() }
    }

    @Test
    fun `light text fades the wallpaper to black, dark text to white`() {
        assertEquals(Color.BLACK, TranslucentWallpaper(darkText = false).dimColor)
        assertEquals(ThemeUtil.THEME_TRANSLUCENT_LIGHT, TranslucentWallpaper(darkText = false).variant)
        assertEquals(Color.WHITE, TranslucentWallpaper(darkText = true).dimColor)
        assertEquals(ThemeUtil.THEME_TRANSLUCENT_DARK, TranslucentWallpaper(darkText = true).variant)
    }

    @Test
    fun `the blur slider gives its first half to the light blurs`() {
        assertEquals(0, TranslucentWallpaper.blurAt(0f))
        assertEquals(25, TranslucentWallpaper.blurAt(0.5f))
        assertEquals(TranslucentWallpaper.MAX_BLUR, TranslucentWallpaper.blurAt(1f))
        for (blur in listOf(0, 9, 25, 64, 100)) {
            assertEquals(blur, TranslucentWallpaper.blurAt(TranslucentWallpaper(blur = blur).blurPosition))
        }
    }

    @Test
    fun `strong blurs shrink the image instead of passing the largest radius`() {
        assertNull(TranslucentWallpaper(blur = 0).blurTransformation(context))
        assertEquals("BlurTransformation(radius=25, sampling=1)", TranslucentWallpaper(blur = 25).blurTransformation(context)?.cacheKey)
        assertEquals("BlurTransformation(radius=13, sampling=2)", TranslucentWallpaper(blur = 26).blurTransformation(context)?.cacheKey)
        assertEquals("BlurTransformation(radius=25, sampling=4)", TranslucentWallpaper(blur = 100).blurTransformation(context)?.cacheKey)
    }

    @Test
    fun `starts from the defaults when nothing was saved`() = runTest {
        assertEquals(TranslucentWallpaper(), TranslucentWallpaper.saved(context))
    }

    @Test
    fun `reads back what was saved and replaces the old wallpaper`() = runTest {
        val first = TranslucentWallpaper(darkText = true, dim = 0.5f, blur = 12, accent = 0xFF336699.toInt())
        assertNull(TranslucentWallpaper.save(context, first, "/a.jpg"))
        assertEquals(first, TranslucentWallpaper.saved(context))

        val second = first.copy(accent = null)
        assertEquals("/a.jpg", TranslucentWallpaper.save(context, second, "/b.jpg"))
        assertEquals(second, TranslucentWallpaper.saved(context))
    }

    @Test
    fun `ignores a saved accent that is not a colour`() = runTest {
        context.dataStore.edit { it[stringPreferencesKey(ThemeUtil.KEY_TRANSLUCENT_PRIMARY_COLOR)] = "not a colour" }

        assertNull(TranslucentWallpaper.saved(context).accent)
        assertEquals("not a colour", context.dataStore.data.first()[stringPreferencesKey(ThemeUtil.KEY_TRANSLUCENT_PRIMARY_COLOR)])
    }
}
