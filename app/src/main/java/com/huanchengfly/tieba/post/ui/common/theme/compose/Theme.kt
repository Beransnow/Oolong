package com.huanchengfly.tieba.post.ui.common.theme.compose

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.intPreferencesKey
import com.huanchengfly.tieba.post.dataStore
import com.huanchengfly.tieba.post.utils.ColorStyle
import com.huanchengfly.tieba.post.utils.ThemeSettings
import com.huanchengfly.tieba.post.utils.ThemeUtil
import com.huanchengfly.tieba.post.utils.compose.darken
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * A theme's colours. System colours are named for what they are for and suit any page; component
 * colours belong to one kind of component, as the top bar, and differ from the system colours in
 * at least one theme. A colour named `onXxx` goes on top of `xxx`.
 */
@Stable
data class ExtendedColors(
    val theme: String,
    val isNightMode: Boolean,
    // Brand
    /** The brand or theme colour: links, buttons. */
    val accent: Color = Color.Unspecified,
    val onAccent: Color = Color.Unspecified,
    /** Emphasis and selection: black in the default light theme. */
    val emphasis: Color = Color.Unspecified,
    val onEmphasis: Color = Color.White,
    // Surfaces
    /** Behind the page; clear on the translucent theme. */
    val background: Color = Color.Unspecified,
    /** Behind what must stay opaque, as sheets and dialogs, on the translucent theme too. */
    val sheet: Color = Color.Unspecified,
    val card: Color = Color.Unspecified,
    /** A block nested in a card: a quote, a reply, an original post. */
    val inset: Color = Color.Unspecified,
    val chip: Color = Color.Unspecified,
    val onChip: Color = Color.Unspecified,
    val divider: Color = Color.Unspecified,
    // Content
    val text: Color = Color.Unspecified,
    val textSecondary: Color = Color.Unspecified,
    val textDisabled: Color = Color.Unspecified,
    // Components
    /** Top bar titles and icons: solid, darker than body text. */
    val topBarContent: Color = Color.Unspecified,
    val topBarContentSecondary: Color = Color.Unspecified,
    val searchField: Color = Color.Unspecified,
    val onSearchField: Color = Color.Unspecified,
    /** Behind the pull-to-refresh and load-more spinners. */
    val refreshIndicator: Color = Color.Unspecified,
    val menu: Color = Color.Unspecified,
    /** A chip that stands out, as the thread store mark. */
    val invertChip: Color = Color.Unspecified,
    val onInvertChip: Color = Color.Unspecified,
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        ThemeUtil.THEME_DEFAULT,
        false,
    )
}

/** Material's colours for [extendedColors]: its dark ones at night and for light text over a wallpaper. */
@SuppressLint("ConflictingOnColor")
fun getColorPalette(extendedColors: ExtendedColors): Colors {
    val darkTheme = extendedColors.isNightMode || extendedColors.theme == ThemeUtil.THEME_TRANSLUCENT_LIGHT
    // Crossed over: Material's primary is our accent, and its secondary our emphasis
    val primaryColor = extendedColors.accent
    val secondaryColor = extendedColors.emphasis
    return if (darkTheme) {
        darkColors(
            primary = primaryColor,
            primaryVariant = primaryColor.darken(),
            secondary = secondaryColor,
            secondaryVariant = Color(0xFF3F310A),
            onSecondary = Color(0xFFFFFFFF),
            background = extendedColors.background,
            onBackground = extendedColors.text,
            surface = extendedColors.card,
        )
    } else {
        lightColors(
            primary = primaryColor,
            primaryVariant = primaryColor.darken(),
            secondary = secondaryColor,
            secondaryVariant = Color(0xFF000000),
            onSecondary = Color(0xFFFFFFFF),
            background = extendedColors.background,
            onBackground = extendedColors.text,
            surface = extendedColors.card,
        )
    }
}

@Composable
private fun getDynamicColor(
    theme: String,
    tonalPalette: TonalPalette,
): ExtendedColors {
    val isDarkColorPalette = ThemeUtil.isNightMode(theme)
    return if (isDarkColorPalette) {
        if (theme == ThemeUtil.THEME_AMOLED_DARK) {
            getBlackDarkDynamicColor(tonalPalette)
        } else {
            getDarkDynamicColor(tonalPalette)
        }
    } else {
        getLightDynamicColor(tonalPalette)
    }
}

@Composable
private fun getLightDynamicColor(tonalPalette: TonalPalette): ExtendedColors {
    val emphasis = tonalPalette.primary40
    return ExtendedColors(
        theme = "dynamic",
        isNightMode = false,
        accent = tonalPalette.secondary40,
        onAccent = tonalPalette.secondary100,
        emphasis = emphasis,
        background = tonalPalette.neutralVariant99,
        sheet = tonalPalette.neutralVariant99,
        card = tonalPalette.neutralVariant99,
        inset = tonalPalette.neutralVariant95,
        chip = tonalPalette.neutralVariant95,
        onChip = tonalPalette.neutralVariant40,
        divider = tonalPalette.neutralVariant95,
        text = tonalPalette.neutralVariant10,
        textSecondary = tonalPalette.neutralVariant40,
        textDisabled = tonalPalette.neutralVariant70,
        topBarContent = tonalPalette.neutralVariant10,
        topBarContentSecondary = tonalPalette.neutralVariant40,
        searchField = tonalPalette.neutralVariant95,
        onSearchField = tonalPalette.neutralVariant30,
        refreshIndicator = tonalPalette.neutralVariant95,
        menu = tonalPalette.neutralVariant99,
        invertChip = emphasis,
        onInvertChip = tonalPalette.primary100,
    )
}

@Composable
private fun getDarkDynamicColor(tonalPalette: TonalPalette): ExtendedColors {
    val emphasis = tonalPalette.primary80
    return ExtendedColors(
        theme = "dynamic",
        isNightMode = true,
        accent = tonalPalette.secondary80,
        onAccent = tonalPalette.secondary20,
        emphasis = emphasis,
        background = tonalPalette.neutralVariant10,
        sheet = tonalPalette.neutralVariant10,
        card = tonalPalette.neutralVariant20,
        inset = tonalPalette.neutralVariant20,
        chip = tonalPalette.neutralVariant20,
        onChip = tonalPalette.neutralVariant60,
        divider = tonalPalette.neutralVariant20,
        text = tonalPalette.neutralVariant90,
        textSecondary = tonalPalette.neutralVariant70,
        textDisabled = tonalPalette.neutralVariant50,
        topBarContent = tonalPalette.neutralVariant90,
        topBarContentSecondary = tonalPalette.neutralVariant70,
        searchField = tonalPalette.neutralVariant20,
        onSearchField = tonalPalette.neutralVariant70,
        refreshIndicator = tonalPalette.neutralVariant10,
        menu = tonalPalette.neutralVariant20,
        invertChip = emphasis,
        onInvertChip = tonalPalette.primary10,
    )
}

@Composable
private fun getBlackDarkDynamicColor(tonalPalette: TonalPalette): ExtendedColors {
    val emphasis = tonalPalette.primary80
    return ExtendedColors(
        theme = "dynamic",
        isNightMode = true,
        accent = tonalPalette.secondary80,
        onAccent = tonalPalette.secondary20,
        emphasis = emphasis,
        background = tonalPalette.neutralVariant0,
        sheet = tonalPalette.neutralVariant0,
        card = tonalPalette.neutralVariant10,
        inset = tonalPalette.neutralVariant10,
        chip = tonalPalette.neutralVariant10,
        onChip = tonalPalette.neutralVariant50,
        divider = tonalPalette.neutralVariant10,
        text = tonalPalette.neutralVariant90,
        textSecondary = tonalPalette.neutralVariant70,
        textDisabled = tonalPalette.neutralVariant50,
        topBarContent = tonalPalette.neutralVariant90,
        topBarContentSecondary = tonalPalette.neutralVariant70,
        searchField = tonalPalette.neutralVariant10,
        onSearchField = tonalPalette.neutralVariant70,
        refreshIndicator = tonalPalette.neutralVariant10,
        menu = tonalPalette.neutralVariant10,
        invertChip = emphasis,
        onInvertChip = tonalPalette.primary10,
    )
}

/**
 * The colours [settings] give when the app is [dark] or light. [picks] changes when a colour that
 * is set apart from them is, as the custom theme's colour.
 */
@Composable
fun colorsOf(settings: ThemeSettings, dark: Boolean, picks: Any? = null): ExtendedColors {
    val context = LocalContext.current
    val theme = settings.themeNameWhen(dark)
    if (settings.isMonet && !ThemeUtil.isTranslucentTheme(theme)) {
        val palette = remember(settings, picks) {
            if (settings.usesSystemPalette && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicTonalPalette(context)
            } else {
                monetPalette(colorOf(context, settings), settings.monetStyle)
            }
        }
        return getDynamicColor(theme, palette)
    }
    return remember(settings, theme, picks) {
        // The system's colour is drawn as a custom one
        val nowTheme = if (settings.isSystemColor && !dark) ThemeUtil.THEME_CUSTOM else ThemeUtil.getCurrentTheme(theme)
        themeColors(
            nowTheme,
            // The wallpaper takes the theme colour
            if (settings.isSystemColor || ThemeUtil.isTranslucentTheme(nowTheme)) colorOf(context, settings) else pickedColor(context, nowTheme),
            plain = settings.style == ColorStyle.Plain,
            darkAccent = darkAccentOf(context, settings),
        )
    }
}

/** The colour picked in [settings]: a light theme's, the custom one or the system's. */
fun colorOf(context: Context, settings: ThemeSettings): Color =
    if (settings.isSystemColor) systemColor(context) else themeColors(settings.accent, pickedColor(context, settings.accent)).accent

/**
 * The colour [settings] give the dark themes: the light theme's own, but none for the default
 * blue, whose dark themes have their own blue.
 */
fun darkAccentOf(context: Context, settings: ThemeSettings): Color? =
    colorOf(context, settings).takeUnless { settings.accent == ThemeUtil.THEME_DEFAULT }

/** The translucent theme's text colour and the picked colours, which the colours depend on too. */
@Composable
fun rememberColorPicks(): Any? {
    val context = LocalContext.current
    // Settings of their own: changing them leaves the theme name as it was
    val picks by remember {
        context.dataStore.data
            .map { listOf(it[TranslucentTextKey], it[ThemeSettings.CustomColorKey]) }
            .distinctUntilChanged()
    }.collectAsState(initial = null)
    return picks
}

@Composable
fun TiebaLiteTheme(
    content: @Composable () -> Unit
) {
    val theme by remember { ThemeUtil.themeState }
    val settings by remember { ThemeUtil.settingsState }
    // At night the wallpaper's theme is not a dark one by name: ask the settings
    val dark = remember(theme, settings) { ThemeUtil.isDark() }
    val extendedColors = colorsOf(settings, dark = dark, picks = rememberColorPicks())

    val colors = getColorPalette(extendedColors)

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colors = colors,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}

private val TranslucentTextKey = intPreferencesKey("translucent_background_theme")

object ExtendedTheme {
    val colors: ExtendedColors
        @Composable
        get() = LocalExtendedColors.current
}
