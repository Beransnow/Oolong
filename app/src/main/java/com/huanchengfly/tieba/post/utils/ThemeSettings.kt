package com.huanchengfly.tieba.post.utils

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** Light or dark, or whichever the system is in. */
enum class Appearance(val value: String) {
    Light("light"),
    Dark("dark"),
    System("system"),
    ;

    companion object {
        fun of(value: String?) = entries.firstOrNull { it.value == value }
    }
}

/**
 * How the app is themed: three choices that do not depend on each other. Which colours are drawn
 * follows from them and whether the system is dark; switching between light and dark never
 * changes a choice.
 *
 * @param accent a light theme's name, [ThemeUtil.THEME_CUSTOM], or [ACCENT_DYNAMIC]
 * @param darkTheme the dark theme's name, used whenever the app is dark
 * @param wallpaper whether the page shows the picked wallpaper, while light
 */
data class ThemeSettings(
    val appearance: Appearance = Appearance.System,
    val accent: String = ThemeUtil.THEME_DEFAULT,
    val darkTheme: String = ThemeUtil.THEME_AMOLED_DARK,
    val wallpaper: Boolean = false,
) {
    val isDynamic: Boolean get() = accent == ACCENT_DYNAMIC

    /** Whether [name] is the light theme picked, rather than another, dynamic colour or the wallpaper. */
    fun isLightTheme(name: String): Boolean = accent == name && !wallpaper

    fun isDark(systemDark: Boolean): Boolean = when (appearance) {
        Appearance.Light -> false
        Appearance.Dark -> true
        Appearance.System -> systemDark
    }

    /** The name of the theme to draw, as the colours are looked up by. */
    fun themeName(systemDark: Boolean): String = when {
        isDark(systemDark) -> darkTheme
        wallpaper -> ThemeUtil.THEME_TRANSLUCENT
        isDynamic -> ThemeUtil.THEME_DEFAULT
        else -> accent
    }

    /** Light or dark as now, but no longer following the system. */
    fun fixed(systemDark: Boolean): ThemeSettings =
        copy(appearance = if (isDark(systemDark)) Appearance.Dark else Appearance.Light)

    internal fun writeTo(prefs: MutablePreferences) {
        prefs[AppearanceKey] = appearance.value
        prefs[AccentKey] = accent
        prefs[DarkThemeKey] = darkTheme
        prefs[BackgroundKey] = if (wallpaper) BACKGROUND_WALLPAPER else BACKGROUND_DEFAULT
        // Still shown as a switch in the appearance settings
        prefs[OldFollowSystemKey] = appearance == Appearance.System
    }

    companion object {
        const val ACCENT_DYNAMIC = "dynamic"

        private val LIGHT_THEMES = listOf(
            ThemeUtil.THEME_DEFAULT,
            ThemeUtil.THEME_BLUE,
            ThemeUtil.THEME_BLACK,
            ThemeUtil.THEME_PINK,
            ThemeUtil.THEME_RED,
            ThemeUtil.THEME_PURPLE,
            ThemeUtil.THEME_CUSTOM,
        )
        private val DARK_THEMES = listOf(ThemeUtil.THEME_BLUE_DARK, ThemeUtil.THEME_GREY_DARK, ThemeUtil.THEME_AMOLED_DARK)

        private fun darkOrNull(name: String?) = when (name) {
            in DARK_THEMES -> name
            // The grey theme's name before it was renamed
            "dark" -> ThemeUtil.THEME_GREY_DARK
            else -> null
        }
        private const val BACKGROUND_DEFAULT = "default"
        private const val BACKGROUND_WALLPAPER = "wallpaper"

        internal val AppearanceKey = stringPreferencesKey("theme_appearance")
        internal val AccentKey = stringPreferencesKey("theme_accent")
        internal val DarkThemeKey = stringPreferencesKey(ThemeUtil.KEY_DARK_THEME)
        internal val BackgroundKey = stringPreferencesKey("theme_background")

        private val OldThemeKey = stringPreferencesKey(ThemeUtil.KEY_THEME)
        private val OldLightThemeKey = stringPreferencesKey(ThemeUtil.KEY_OLD_THEME)
        private val OldFollowSystemKey = booleanPreferencesKey("follow_system_night")
        private val OldDynamicKey = booleanPreferencesKey(ThemeUtil.KEY_USE_DYNAMIC_THEME)

        fun from(prefs: Preferences): ThemeSettings {
            val defaults = ThemeSettings()
            return ThemeSettings(
                appearance = Appearance.of(prefs[AppearanceKey]) ?: defaults.appearance,
                accent = prefs[AccentKey] ?: defaults.accent,
                darkTheme = darkOrNull(prefs[DarkThemeKey]) ?: defaults.darkTheme,
                wallpaper = prefs[BackgroundKey] == BACKGROUND_WALLPAPER,
            )
        }

        /** Whether [prefs] still hold the settings as one theme name, from before they were split. */
        fun needsMigration(prefs: Preferences): Boolean = prefs[AppearanceKey] == null

        /**
         * The settings that show what the single theme name in [prefs] showed. That name was
         * overwritten on every switch between light and dark, keeping the light theme aside meanwhile.
         */
        fun migrate(prefs: Preferences): ThemeSettings {
            val theme = prefs[OldThemeKey] ?: ThemeUtil.THEME_DEFAULT
            val night = ThemeUtil.isNightMode(theme)
            fun lightOrDefault(name: String?) = name?.takeUnless { ThemeUtil.isNightMode(it) } ?: ThemeUtil.THEME_DEFAULT
            val aside = lightOrDefault(prefs[OldLightThemeKey])
            val light = if (night) aside else theme
            val wallpaper = ThemeUtil.isTranslucentTheme(light)
            // The translucent theme kept the theme picked before it aside
            val base = (if (wallpaper) aside else light).takeIf { it in LIGHT_THEMES } ?: ThemeUtil.THEME_DEFAULT
            return ThemeSettings(
                appearance = when {
                    prefs[OldFollowSystemKey] ?: true -> Appearance.System
                    night -> Appearance.Dark
                    else -> Appearance.Light
                },
                accent = if (prefs[OldDynamicKey] == true && !wallpaper) ACCENT_DYNAMIC else base,
                darkTheme = (if (night) darkOrNull(theme) else null) ?: darkOrNull(prefs[DarkThemeKey]) ?: ThemeUtil.THEME_AMOLED_DARK,
                wallpaper = wallpaper,
            )
        }
    }
}
