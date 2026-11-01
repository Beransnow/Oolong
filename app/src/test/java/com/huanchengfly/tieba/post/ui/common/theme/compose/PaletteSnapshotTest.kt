package com.huanchengfly.tieba.post.ui.common.theme.compose

import android.content.Context
import androidx.compose.material.MaterialTheme
import androidx.compose.material.primarySurface
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.huanchengfly.tieba.post.dataStore
import com.huanchengfly.tieba.post.testing.LegacyAppRule
import com.huanchengfly.tieba.post.utils.ThemeUtil
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Every colour every theme gives, against [GOLDEN]. Restructuring the palette must not change
 * a single one; run with `-DupdatePalette=true` to write the file anew after a deliberate change.
 */
@RunWith(RobolectricTestRunner::class)
class PaletteSnapshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** A theme as the user sets it up: its name, the translucent text setting and dynamic colour. */
    private data class Setup(val name: String, val theme: String, val darkText: Boolean = false, val dynamic: Boolean = false)

    private val setups = listOf(
        "tieba", "black", "blue", "purple", "pink", "red", "custom",
        "blue_dark", "grey_dark", "amoled_dark",
    ).map { Setup(it, it) } + listOf(
        Setup("translucent_light", ThemeUtil.THEME_TRANSLUCENT, darkText = false),
        Setup("translucent_dark", ThemeUtil.THEME_TRANSLUCENT, darkText = true),
        Setup("dynamic_light", ThemeUtil.THEME_DEFAULT, dynamic = true),
        Setup("dynamic_dark", ThemeUtil.THEME_BLUE_DARK, dynamic = true),
        Setup("dynamic_amoled", ThemeUtil.THEME_AMOLED_DARK, dynamic = true),
    )

    @Test
    fun `no theme's colours change`() {
        var latest: Pair<ExtendedColors, androidx.compose.material.Colors>? = null
        val captured = mutableMapOf<String, String>()
        composeRule.setContent {
            TiebaLiteTheme {
                val colors = ExtendedTheme.colors
                val material = MaterialTheme.colors
                SideEffect { latest = colors to material }
            }
        }
        for (next in setups) {
            runBlocking {
                context.dataStore.edit {
                    it[intPreferencesKey("translucent_background_theme")] =
                        if (next.darkText) ThemeUtil.TRANSLUCENT_THEME_DARK else ThemeUtil.TRANSLUCENT_THEME_LIGHT
                    it[booleanPreferencesKey("useDynamicColorTheme")] = next.dynamic
                }
            }
            // Through another theme first: the two translucent setups share one theme name, and the
            // theme only recomposes when that name changes
            composeRule.runOnIdle { ThemeUtil.themeState.value = if (next.theme == "tieba") "black" else "tieba" }
            composeRule.waitForIdle()
            composeRule.runOnIdle { ThemeUtil.themeState.value = next.theme }
            composeRule.waitForIdle()
            val (colors, material) = checkNotNull(latest)
            captured[next.name] = describe(next.name, colors, material)
        }
        val actual = setups.joinToString("\n") { captured.getValue(it.name) } + "\n"

        val golden = File(GOLDEN)
        if (System.getProperty("updatePalette") == "true" || !golden.exists()) {
            golden.parentFile?.mkdirs()
            golden.writeText(actual)
            if (System.getProperty("updatePalette") != "true") fail("Wrote $GOLDEN; check it in")
            return
        }
        assertEquals(golden.readText(), actual)
    }

    private fun describe(name: String, colors: ExtendedColors, material: androidx.compose.material.Colors): String {
        val fields = ExtendedColors::class.java.declaredFields
            .filter { it.type == Long::class.javaPrimitiveType }
            .sortedBy { it.name }
            .map { field ->
                field.isAccessible = true
                field.name to Color(field.getLong(colors).toULong())
            }
        val derived = listOf(
            "~pullRefreshIndicator" to colors.pullRefreshIndicator,
            "~loadMoreIndicator" to colors.loadMoreIndicator,
            "~menuBackground" to colors.menuBackground,
            "~invertChipBackground" to colors.invertChipBackground,
            "~invertChipContent" to colors.invertChipContent,
            "m.primary" to material.primary,
            "m.secondary" to material.secondary,
            "m.onSecondary" to material.onSecondary,
            "m.background" to material.background,
            "m.onBackground" to material.onBackground,
            "m.surface" to material.surface,
            "m.onSurface" to material.onSurface,
            "m.primarySurface" to material.primarySurface,
        )
        return (fields + derived).joinToString("\n") { (field, color) ->
            "$name ${field.padEnd(22)} ${"%08X".format(color.toArgb())}"
        } + "\n$name isNightMode ${colors.isNightMode} isLight ${material.isLight}"
    }

    private companion object {
        const val GOLDEN = "src/test/resources/theme/palette.txt"
    }
}
