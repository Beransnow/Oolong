package oolong.client.utils

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeSettingsTest {
    private fun old(
        theme: String? = null,
        aside: String? = null,
        dark: String? = null,
        followSystem: Boolean? = null,
        dynamic: Boolean? = null,
    ): Preferences = mutablePreferencesOf().apply {
        theme?.let { this[stringPreferencesKey("theme")] = it }
        aside?.let { this[stringPreferencesKey("old_theme")] = it }
        dark?.let { this[stringPreferencesKey("dark_theme")] = it }
        followSystem?.let { this[booleanPreferencesKey("follow_system_night")] = it }
        dynamic?.let { this[booleanPreferencesKey("useDynamicColorTheme")] = it }
    }

    @Test
    fun `a new install follows the system with the default themes`() {
        assertEquals(ThemeSettings(), ThemeSettings.migrate(old()))
    }

    @Test
    fun `a light theme in use stays the light theme`() {
        assertEquals(
            ThemeSettings(appearance = Appearance.Light, accent = "pink", style = ColorStyle.Bright, darkTheme = "grey_dark"),
            ThemeSettings.migrate(old(theme = "pink", dark = "grey_dark", followSystem = false)),
        )
    }

    @Test
    fun `at night the light theme comes from where it was kept aside`() {
        assertEquals(
            ThemeSettings(appearance = Appearance.Dark, accent = "red", style = ColorStyle.Bright, darkTheme = "amoled_dark"),
            ThemeSettings.migrate(old(theme = "amoled_dark", aside = "red", dark = "blue_dark", followSystem = false)),
        )
    }

    @Test
    fun `the default theme is plain, and the bright blue is the default colour used throughout`() {
        assertEquals(ColorStyle.Plain, ThemeSettings.migrate(old(theme = "tieba")).style)
        val blue = ThemeSettings.migrate(old(theme = "blue", followSystem = false))
        assertEquals(ThemeUtil.THEME_DEFAULT, blue.accent)
        assertEquals(ColorStyle.Bright, blue.style)
        assertEquals(ThemeUtil.THEME_BLUE, blue.themeName(systemDark = false))
    }

    @Test
    fun `a dark theme picked from the list is kept over the saved one`() {
        val settings = ThemeSettings.migrate(old(theme = "blue_dark", aside = "tieba", dark = "amoled_dark"))
        assertEquals(Appearance.System, settings.appearance)
        assertEquals("blue_dark", settings.darkTheme)
    }

    @Test
    fun `the translucent theme becomes the wallpaper over the theme picked before it`() {
        val settings = ThemeSettings.migrate(old(theme = "translucent", aside = "purple", followSystem = false))
        assertTrue(settings.wallpaper)
        assertEquals("purple", settings.accent)
        assertEquals(ThemeUtil.THEME_TRANSLUCENT, settings.themeName(systemDark = false))
    }

    @Test
    fun `the wallpaper is kept through the night, with light text`() {
        val settings = ThemeSettings.migrate(old(theme = "grey_dark", aside = "translucent"))
        assertTrue(settings.wallpaper)
        assertEquals(ThemeUtil.THEME_DEFAULT, settings.accent)
        assertEquals("grey_dark", settings.darkTheme)
        assertEquals(ThemeUtil.THEME_TRANSLUCENT_LIGHT, settings.themeName(systemDark = true))
    }

    @Test
    fun `dynamic colour becomes the accent unless the wallpaper is in use`() {
        val dynamic = ThemeSettings.migrate(old(theme = "blue", dynamic = true))
        assertEquals(ThemeSettings.ACCENT_DYNAMIC, dynamic.accent)
        assertTrue(dynamic.usesSystemPalette)
        val wallpaper = ThemeSettings.migrate(old(theme = "translucent", aside = "black", dynamic = true))
        assertFalse(wallpaper.isSystemColor)
        assertEquals("black", wallpaper.accent)
    }

    @Test
    fun `old and unknown names fall back`() {
        assertEquals("grey_dark", ThemeSettings.migrate(old(theme = "dark")).darkTheme)
        assertEquals(ThemeUtil.THEME_DEFAULT, ThemeSettings.migrate(old(theme = "white")).accent)
    }

    @Test
    fun `reads back what it writes`() {
        val settings = ThemeSettings(Appearance.Dark, "custom", ColorStyle.Monet, MonetStyle.Vibrant, darkTheme = "blue_dark", wallpaper = true)
        val prefs = mutablePreferencesOf().also { settings.writeTo(it) }
        assertEquals(settings, ThemeSettings.from(prefs))
        assertFalse(ThemeSettings.needsMigration(prefs))
    }

    @Test
    fun `settings saved before the style was read with the style their colour had`() {
        val prefs = mutablePreferencesOf().also { ThemeSettings(accent = "pink").writeTo(it) }
        prefs.remove(stringPreferencesKey("theme_color_style"))
        prefs[stringPreferencesKey("theme_accent")] = "blue"
        val settings = ThemeSettings.from(prefs)
        assertEquals(ThemeUtil.THEME_DEFAULT, settings.accent)
        assertEquals(ColorStyle.Bright, settings.style)
    }

    @Test
    fun `a colour picked for the wallpaper becomes the custom colour while none is kept`() {
        val prefs = mutablePreferencesOf().also { ThemeSettings(wallpaper = true).writeTo(it) }
        prefs[stringPreferencesKey("translucent_primary_color")] = "#FF336699"
        assertTrue(ThemeSettings.hasWallpaperColor(prefs))

        ThemeSettings.moveWallpaperColor(prefs)

        assertFalse(ThemeSettings.hasWallpaperColor(prefs))
        assertEquals("#FF336699", prefs[ThemeSettings.CustomColorKey])
        assertEquals(ThemeUtil.THEME_CUSTOM, ThemeSettings.from(prefs).accent)
    }

    @Test
    fun `a colour picked for the wallpaper leaves a kept custom colour alone`() {
        val prefs = mutablePreferencesOf().also { ThemeSettings(accent = "pink", wallpaper = true).writeTo(it) }
        prefs[ThemeSettings.CustomColorKey] = "#FF000000"
        prefs[stringPreferencesKey("translucent_primary_color")] = "#FF336699"

        ThemeSettings.moveWallpaperColor(prefs)

        assertFalse(ThemeSettings.hasWallpaperColor(prefs))
        assertEquals("#FF000000", prefs[ThemeSettings.CustomColorKey])
        assertEquals("pink", ThemeSettings.from(prefs).accent)
    }

    @Test
    fun `switching between light and dark keeps both themes`() {
        val settings = ThemeSettings(Appearance.System, accent = "pink", darkTheme = "blue_dark")
        assertEquals("pink", settings.themeName(systemDark = false))
        assertEquals("blue_dark", settings.themeName(systemDark = true))
    }

    @Test
    fun `the night switch swaps a fixed look for the other`() {
        assertEquals(Appearance.Dark, ThemeSettings(Appearance.Light).toggledDark(systemDark = true).appearance)
        assertEquals(Appearance.Light, ThemeSettings(Appearance.Dark).toggledDark(systemDark = false).appearance)
    }

    @Test
    fun `while following the system the night switch shows the other look until the system turns`() {
        val switched = ThemeSettings(Appearance.System).toggledDark(systemDark = false)
        assertEquals(Appearance.System, switched.appearance)
        assertTrue(switched.isDark(systemDark = false))

        val turned = switched.settled(systemDark = true)
        assertEquals(ThemeSettings(Appearance.System), turned)
        assertFalse(turned.settled(systemDark = false).isDark(systemDark = false))
    }

    @Test
    fun `the night switch pressed again goes back to following the system`() {
        val settings = ThemeSettings(Appearance.System)
        assertEquals(settings, settings.toggledDark(systemDark = true).toggledDark(systemDark = true))
    }

    @Test
    fun `the other look is kept while the system has not turned`() {
        val switched = ThemeSettings(Appearance.System).toggledDark(systemDark = true)
        assertEquals(switched, switched.settled(systemDark = true))
        val prefs = mutablePreferencesOf().also { switched.writeTo(it) }
        assertEquals(switched, ThemeSettings.from(prefs))
    }
}
