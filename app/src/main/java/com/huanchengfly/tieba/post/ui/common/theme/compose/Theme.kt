package com.huanchengfly.tieba.post.ui.common.theme.compose

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.material.Colors
import androidx.compose.material.ContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.huanchengfly.tieba.post.App
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.rememberPreferenceAsState
import com.huanchengfly.tieba.post.utils.ThemeUtil
import com.huanchengfly.tieba.post.utils.compose.darken

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

@SuppressLint("ConflictingOnColor")
fun getColorPalette(
    darkTheme: Boolean,
    extendedColors: ExtendedColors
): Colors {
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
 * The colours of [theme]. [nowTheme] is the variant to draw, which for the translucent theme
 * normally comes from the saved settings; previews pass another.
 */
@Composable
internal fun getThemeColorForTheme(theme: String, nowTheme: String = ThemeUtil.getCurrentTheme(theme)): ExtendedColors {
    val context = LocalContext.current
    fun color(attr: Int) = Color(App.ThemeDelegate.getColorByAttr(context, attr, nowTheme))
    val textColor = color(R.attr.colorText)
    val emphasis = color(R.attr.colorNewPrimary)
    val card = color(R.attr.colorCard)
    val sheet = color(R.attr.colorWindowBackground)
    val translucent = ThemeUtil.isTranslucentTheme(nowTheme)
    val night = ThemeUtil.isNightMode(nowTheme)
    return ExtendedColors(
        theme = nowTheme,
        isNightMode = night,
        accent = color(R.attr.colorAccent),
        onAccent = color(R.attr.colorOnAccent),
        emphasis = emphasis,
        background = color(R.attr.colorBackground),
        sheet = sheet,
        card = card,
        inset = color(R.attr.colorFloorCard),
        chip = color(R.attr.colorChip),
        onChip = color(R.attr.colorOnChip),
        divider = color(R.attr.colorDivider),
        text = textColor.copy(alpha = ContentAlpha.high),
        textSecondary = textColor.copy(alpha = ContentAlpha.medium),
        textDisabled = color(R.attr.color_text_disabled),
        topBarContent = color(R.attr.colorToolbarItem),
        topBarContentSecondary = color(R.attr.colorToolbarItemSecondary),
        searchField = color(R.attr.colorToolbarSurface),
        onSearchField = color(R.attr.colorOnToolbarSurface),
        // Over a wallpaper the spinners and menus need an opaque surface
        refreshIndicator = if (translucent) sheet else color(R.attr.colorIndicator),
        menu = if (translucent) sheet else card,
        invertChip = if (night) emphasis.copy(alpha = 0.3f) else emphasis,
        onInvertChip = if (night) emphasis else color(R.attr.colorOnAccent),
    )
}

@Composable
fun TiebaLiteTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val theme by remember { ThemeUtil.themeState }
    val isDynamicTheme by rememberPreferenceAsState(
        key = booleanPreferencesKey("useDynamicColorTheme"),
        defaultValue = false
    )
    val isDarkColorPalette by remember {
        derivedStateOf {
            ThemeUtil.isNightMode(theme) ||
                (ThemeUtil.isTranslucentTheme(theme) && theme.contains("light"))
        }
    }

    val useDynamicTheme = isDynamicTheme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val extendedColors = if (!useDynamicTheme || ThemeUtil.isTranslucentTheme(theme)) {
        getThemeColorForTheme(theme)
    } else {
        getDynamicColor(theme, dynamicTonalPalette(context))
    }

    val colors = getColorPalette(isDarkColorPalette, extendedColors)

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colors = colors,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}

object ExtendedTheme {
    val colors: ExtendedColors
        @Composable
        get() = LocalExtendedColors.current
}
