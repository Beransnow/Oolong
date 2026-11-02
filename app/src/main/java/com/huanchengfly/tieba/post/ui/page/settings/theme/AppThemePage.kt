package com.huanchengfly.tieba.post.ui.page.settings.theme

import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.datastore.preferences.core.stringPreferencesKey
import coil3.compose.AsyncImage
import com.godaddy.android.colorpicker.HsvColor
import com.godaddy.android.colorpicker.harmony.ColorHarmonyMode
import com.godaddy.android.colorpicker.harmony.HarmonyColorPicker
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.dataStore
import com.huanchengfly.tieba.post.ui.common.prefs.GroupHeader
import com.huanchengfly.tieba.post.ui.common.prefs.PrefsChevron
import com.huanchengfly.tieba.post.ui.common.prefs.PrefsChoiceValue
import com.huanchengfly.tieba.post.ui.common.prefs.widgets.TextPref
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedColors
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.TonalPalette
import com.huanchengfly.tieba.post.ui.common.theme.compose.colorsOf
import com.huanchengfly.tieba.post.ui.common.theme.compose.dynamicTonalPalette
import com.huanchengfly.tieba.post.ui.common.theme.compose.monetPalette
import com.huanchengfly.tieba.post.ui.common.theme.compose.pickedColor
import com.huanchengfly.tieba.post.ui.common.theme.compose.rememberColorPicks
import com.huanchengfly.tieba.post.ui.common.theme.compose.systemColor
import com.huanchengfly.tieba.post.ui.common.theme.compose.themeColors
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.navigation.TranslucentThemeKey
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.ClickMenu
import com.huanchengfly.tieba.post.ui.widgets.compose.Dialog
import com.huanchengfly.tieba.post.ui.widgets.compose.DialogNegativeButton
import com.huanchengfly.tieba.post.ui.widgets.compose.DialogPositiveButton
import com.huanchengfly.tieba.post.ui.widgets.compose.DialogState
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBar
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBarTitle
import com.huanchengfly.tieba.post.ui.widgets.compose.IconButton
import com.huanchengfly.tieba.post.ui.widgets.compose.Switch
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberDialogState
import com.huanchengfly.tieba.post.utils.Appearance
import com.huanchengfly.tieba.post.utils.ColorStyle
import com.huanchengfly.tieba.post.utils.MonetStyle
import com.huanchengfly.tieba.post.utils.ThemeSettings
import com.huanchengfly.tieba.post.utils.ThemeUtil
import com.huanchengfly.tieba.post.utils.appPreferences
import com.huanchengfly.tieba.post.utils.extension.toHexString
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * The theme page: light or dark and the dark background, the colour and how it is used, and the
 * wallpaper. Only the two choices made most often are shown as pictures; the rest are rows.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AppThemePage(
    navigator: Navigator = LocalNavigator.current,
) {
    val context = LocalContext.current
    val settings by remember { ThemeUtil.settingsState }
    val picks = rememberColorPicks()
    val wallpaperPath by remember {
        context.dataStore.data.map { it[stringPreferencesKey(ThemeUtil.KEY_TRANSLUCENT_THEME_BACKGROUND_PATH)] }
    }.collectAsState(initial = null)
    val wallpaper = wallpaperPath?.takeIf { it.isNotEmpty() }?.let(::File)
    val customColorDialogState = rememberDialogState()
    CustomColorDialog(customColorDialogState)

    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = R.string.title_theme)) },
                navigationIcon = { BackNavigationIcon(onBackPressed = { navigator.navigateUp() }) },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(bottom = 24.dp),
        ) {
            GroupHeader(title = stringResource(id = R.string.title_appearance))
            ThemeGroup {
                AppearancePicker(
                    settings = settings,
                    light = colorsOf(settings, dark = false, picks = picks),
                    dark = colorsOf(settings, dark = true, picks = picks),
                    wallpaper = wallpaper.takeIf { settings.wallpaper },
                )
                DarkBackgroundRow(settings, monetDark = colorsOf(settings.copy(darkTheme = ThemeUtil.THEME_BLUE_DARK), dark = true, picks = picks).background)
            }

            GroupHeader(title = stringResource(id = R.string.title_theme_color))
            ThemeGroup {
                AccentGrid(
                    settings = settings,
                    customColor = pickedColor(context, ThemeUtil.THEME_CUSTOM),
                    onCustom = { customColorDialogState.show() },
                )
                ColorStyleRow(settings.style)
                if (settings.isMonet) MonetStyleRow(settings.monetStyle)
            }

            GroupHeader(title = stringResource(id = R.string.title_background))
            ThemeGroup {
                TextPref(
                    title = stringResource(id = R.string.title_wallpaper),
                    onClick = {
                        when {
                            settings.wallpaper -> ThemeUtil.setWallpaper(false)
                            wallpaper == null -> navigator.navigate(TranslucentThemeKey)
                            else -> ThemeUtil.setWallpaper(true)
                        }
                    },
                    trailingContent = { Switch(checked = settings.wallpaper, onCheckedChange = null) },
                )
                if (settings.wallpaper) {
                    TextPref(
                        title = stringResource(id = R.string.title_wallpaper_settings),
                        onClick = { navigator.navigate(TranslucentThemeKey) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = wallpaper,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                PrefsChevron()
                            }
                        },
                    )
                }
            }
        }
    }
}

/** A grey block holding a group of choices, as on the settings pages. */
@Composable
private fun ThemeGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ExtendedTheme.colors.chip)
            .padding(vertical = 4.dp),
        content = content,
    )
}

/** Light, dark or as the system is, each shown as a small home page in its colours. */
@Composable
private fun AppearancePicker(settings: ThemeSettings, light: ExtendedColors, dark: ExtendedColors, wallpaper: File?) {
    val nightOverlay = TranslucentWallpaper.nightOverlay(rememberWallpaperDim())
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppearanceOption(stringResource(id = R.string.appearance_light), settings.appearance == Appearance.Light, { ThemeUtil.setAppearance(Appearance.Light) }) {
            HomePreview(light, wallpaper)
        }
        AppearanceOption(stringResource(id = R.string.appearance_dark), settings.appearance == Appearance.Dark, { ThemeUtil.setAppearance(Appearance.Dark) }) {
            HomePreview(dark, wallpaper, nightOverlay)
        }
        AppearanceOption(stringResource(id = R.string.appearance_system), settings.appearance == Appearance.System, { ThemeUtil.setAppearance(Appearance.System) }) {
            HomePreview(light, wallpaper)
            // The lower right half dark: it turns with the system
            Box(modifier = Modifier.fillMaxSize().clip(LowerRightHalf)) { HomePreview(dark, wallpaper, nightOverlay) }
        }
    }
}

private val LowerRightHalf = GenericShape { size, _ ->
    moveTo(size.width, 0f)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

@Composable
private fun RowScope.AppearanceOption(name: String, selected: Boolean, onClick: () -> Unit, preview: @Composable BoxScope.() -> Unit) {
    val colors = ExtendedTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = name, onClick = onClick)
            .padding(bottom = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 17f)
                .border(2.dp, if (selected) colors.emphasis else Color.Transparent, RoundedCornerShape(16.dp))
                .padding(4.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, colors.divider, RoundedCornerShape(12.dp)),
        ) {
            preview()
            if (selected) SelectedBadge(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = name,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colors.text else colors.textSecondary,
        )
    }
}

@Composable
private fun SelectedBadge(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(20.dp).background(ExtendedTheme.colors.emphasis, CircleShape),
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = ExtendedTheme.colors.onEmphasis, modifier = Modifier.size(14.dp))
    }
}

/** A home page in miniature: the title, the search field, the tabs, two posts and the bottom bar. */
@Composable
private fun BoxScope.HomePreview(colors: ExtendedColors, wallpaper: File?, overlay: Float = 0f) {
    if (wallpaper != null) {
        AsyncImage(model = wallpaper, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        if (overlay > 0f) Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = overlay)))
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.matchParentSize().background(colors.background).padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        Bar(colors.topBarContent, width = 0.45f, height = 6.dp)
        Box(modifier = Modifier.fillMaxWidth().height(10.dp).background(colors.searchField, CircleShape))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(modifier = Modifier.size(width = 18.dp, height = 6.dp).background(colors.emphasis, CircleShape))
            Box(modifier = Modifier.size(width = 14.dp, height = 6.dp).background(colors.chip, CircleShape))
            Box(modifier = Modifier.size(width = 14.dp, height = 6.dp).background(colors.chip, CircleShape))
        }
        repeat(2) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(colors.card).padding(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(colors.emphasis, CircleShape))
                    Bar(colors.text, width = 0.5f, height = 4.dp)
                }
                Bar(colors.textSecondary, width = 0.9f, height = 3.dp)
                Bar(colors.accent, width = 0.6f, height = 3.dp)
            }
        }
    }
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(12.dp)
            .background(colors.chip, CircleShape),
    ) {
        Box(modifier = Modifier.size(5.dp).background(colors.emphasis, CircleShape))
        Box(modifier = Modifier.size(5.dp).background(colors.textSecondary, CircleShape))
        Box(modifier = Modifier.size(5.dp).background(colors.textSecondary, CircleShape))
    }
}

@Composable
private fun Bar(color: Color, width: Float, height: Dp) {
    Box(modifier = Modifier.fillMaxWidth(width).height(height).background(color, CircleShape))
}

/**
 * The dark background, chosen in a menu; a dot shows each one's colour. Monet makes its own dark
 * grey, so there it is that or pure black.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun DarkBackgroundRow(settings: ThemeSettings, monetDark: Color) {
    val options = if (settings.isMonet) {
        listOf(
            Triple(ThemeUtil.THEME_BLUE_DARK, stringResource(id = R.string.dark_background_default), monetDark),
            Triple(ThemeUtil.THEME_AMOLED_DARK, stringArrayResource(id = R.array.dark_theme_names).last(), Color.Black),
        )
    } else {
        val values = stringArrayResource(id = R.array.dark_theme_values)
        val names = stringArrayResource(id = R.array.dark_theme_names)
        values.mapIndexed { index, value -> Triple(value, names[index], themeColors(value).background) }
    }
    val selected = options.firstOrNull { it.first == settings.darkTheme } ?: options.first()
    ClickMenu(
        menuContent = {
            options.forEach { (value, name, color) ->
                DropdownMenuItem(onClick = {
                    // Monet's own grey leaves the theme picked for the other styles as it was
                    if (value != selected.first) ThemeUtil.selectDarkTheme(value)
                    dismiss()
                }) {
                    BackgroundDot(color)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = name, modifier = Modifier.weight(1f))
                    if (value == selected.first) MenuCheck()
                }
            }
        },
    ) {
        TextPref(
            title = stringResource(id = R.string.title_dark_background),
            enabled = true,
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BackgroundDot(selected.third)
                    Spacer(modifier = Modifier.width(8.dp))
                    PrefsChoiceValue(text = selected.second)
                }
            },
        )
    }
}

@Composable
private fun BackgroundDot(color: Color) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .background(color, CircleShape)
            .border(1.dp, ExtendedTheme.colors.divider, CircleShape),
    )
}

@Composable
private fun MenuCheck() {
    Spacer(modifier = Modifier.width(16.dp))
    Icon(Icons.Rounded.Check, contentDescription = null, tint = ExtendedTheme.colors.text, modifier = Modifier.size(20.dp))
}

/**
 * The colours, four to a row, each a round swatch in its colours as the page now uses them: a
 * colour and how it is used, or under Monet the palette made from it, in four parts.
 */
@Composable
private fun AccentGrid(settings: ThemeSettings, customColor: Color?, onCustom: () -> Unit) {
    val names = stringArrayResource(id = R.array.accent_names)
    val context = LocalContext.current
    val dark = ExtendedTheme.colors.isNightMode
    val systemColor = remember { systemColor(context) }

    /** The swatch of [accent], whose colour is [color]. */
    fun fillOf(accent: String, color: Color): Pair<Brush, Color> {
        if (settings.isMonet) {
            val palette = if (accent == ThemeSettings.ACCENT_DYNAMIC && settings.monetStyle == MonetStyle.TonalSpot && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicTonalPalette(context)
            } else {
                monetPalette(color, settings.monetStyle)
            }
            return palette.quarters(dark) to if (dark) palette.primary20 else Color.White
        }
        val light = themeColors(ThemeUtil.THEME_CUSTOM, color, plain = settings.style == ColorStyle.Plain)
        val colors = if (dark) themeColors(settings.darkTheme, darkAccent = color.takeUnless { accent == ThemeUtil.THEME_DEFAULT }) else light
        return Brush.radialGradient(listOf(colors.emphasis, colors.accent)) to colors.onEmphasis
    }

    val swatches = buildList<@Composable RowScope.() -> Unit> {
        ThemeSettings.ACCENTS.forEachIndexed { index, accent ->
            if (accent == ThemeUtil.THEME_CUSTOM) return@forEachIndexed
            add {
                val (fill, content) = remember(settings, dark) { fillOf(accent, themeColors(accent).accent) }
                Swatch(names[index], settings.accent == accent, fill, content, { ThemeUtil.selectAccent(accent) })
            }
        }
        add {
            val selected = settings.accent == ThemeUtil.THEME_CUSTOM
            val (fill, content) = remember(settings, dark, customColor) {
                fillOf(ThemeUtil.THEME_CUSTOM, customColor ?: themeColors(ThemeUtil.THEME_CUSTOM).accent)
            }
            Swatch(
                name = stringResource(id = R.string.title_custom_color),
                selected = selected,
                fill = fill,
                content = content,
                // Picked for the first time, or picked again, it asks for the colour
                onClick = { if (selected || customColor == null) onCustom() else ThemeUtil.selectAccent(ThemeUtil.THEME_CUSTOM) },
                icon = { Icon(Icons.Rounded.ColorLens, contentDescription = null, modifier = Modifier.size(20.dp)) },
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add {
                val (fill, content) = remember(settings, dark) { fillOf(ThemeSettings.ACCENT_DYNAMIC, systemColor) }
                Swatch(
                    name = stringResource(id = R.string.accent_system_wallpaper),
                    selected = settings.isSystemColor,
                    fill = fill,
                    content = content,
                    onClick = { ThemeUtil.selectAccent(ThemeSettings.ACCENT_DYNAMIC) },
                )
            }
        }
    }
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        swatches.chunked(4).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { it() }
                repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/** The palette in four parts, as the system shows its own: the three colours and the background. */
private fun TonalPalette.quarters(dark: Boolean): Brush {
    val parts = if (dark) {
        listOf(primary80, secondary80, neutralVariant30, tertiary80)
    } else {
        listOf(primary40, secondary40, neutralVariant90, tertiary40)
    }
    return Brush.sweepGradient(
        *parts.flatMapIndexed { index, color -> listOf(index / 4f to color, (index + 1) / 4f to color) }.toTypedArray(),
    )
}

@Composable
private fun RowScope.Swatch(
    name: String,
    selected: Boolean,
    fill: Brush,
    content: Color,
    onClick: () -> Unit,
    icon: (@Composable () -> Unit)? = null,
) {
    val colors = ExtendedTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = name, onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .border(2.dp, if (selected) colors.emphasis else Color.Transparent, CircleShape)
                .padding(4.dp)
                .background(fill, CircleShape)
                // Keeps the black swatch apart from a dark background
                .border(1.dp, colors.divider, CircleShape),
        ) {
            CompositionLocalProvider(LocalContentColor provides content) {
                when {
                    selected -> Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    icon != null -> icon()
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            fontSize = 12.sp,
            maxLines = 1,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) colors.text else colors.textSecondary,
        )
    }
}

/** Plain, bright or Monet. */
@Composable
private fun ColorStyleRow(selected: ColorStyle) {
    ChoiceRow(
        title = stringResource(id = R.string.title_color_style),
        selected = selected,
        options = listOf(
            Triple(ColorStyle.Plain, stringResource(id = R.string.color_style_plain), stringResource(id = R.string.color_style_plain_desc)),
            Triple(ColorStyle.Bright, stringResource(id = R.string.color_style_bright), stringResource(id = R.string.color_style_bright_desc)),
            Triple(ColorStyle.Monet, stringResource(id = R.string.color_style_monet), stringResource(id = R.string.color_style_monet_desc)),
        ),
        onSelect = { ThemeUtil.selectStyle(it) },
    )
}

/** How Monet makes its palette, in the styles the system offers. */
@Composable
private fun MonetStyleRow(selected: MonetStyle) {
    ChoiceRow(
        title = stringResource(id = R.string.title_monet_style),
        selected = selected,
        options = listOf(
            Triple(MonetStyle.TonalSpot, stringResource(id = R.string.monet_tonal_spot), stringResource(id = R.string.monet_tonal_spot_desc)),
            Triple(MonetStyle.Vibrant, stringResource(id = R.string.monet_vibrant), stringResource(id = R.string.monet_vibrant_desc)),
            Triple(MonetStyle.Expressive, stringResource(id = R.string.monet_expressive), stringResource(id = R.string.monet_expressive_desc)),
            Triple(MonetStyle.Neutral, stringResource(id = R.string.monet_neutral), stringResource(id = R.string.monet_neutral_desc)),
            Triple(MonetStyle.Rainbow, stringResource(id = R.string.monet_rainbow), stringResource(id = R.string.monet_rainbow_desc)),
            Triple(MonetStyle.FruitSalad, stringResource(id = R.string.monet_fruit_salad), stringResource(id = R.string.monet_fruit_salad_desc)),
            Triple(MonetStyle.Monochrome, stringResource(id = R.string.monet_monochrome), stringResource(id = R.string.monet_monochrome_desc)),
        ),
        onSelect = { ThemeUtil.selectMonetStyle(it) },
    )
}

/** A choice made in a menu that says what each option looks like. */
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun <T> ChoiceRow(title: String, selected: T, options: List<Triple<T, String, String>>, onSelect: (T) -> Unit) {
    ClickMenu(
        menuContent = {
            options.forEach { (value, name, description) ->
                DropdownMenuItem(onClick = {
                    onSelect(value)
                    dismiss()
                }) {
                    Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                        Text(text = name)
                        Text(
                            text = description,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Normal,
                            color = ExtendedTheme.colors.textSecondary,
                        )
                    }
                    if (value == selected) MenuCheck()
                }
            }
        },
    ) {
        TextPref(
            title = title,
            enabled = true,
            trailingContent = { PrefsChoiceValue(text = options.first { it.first == selected }.second) },
        )
    }
}

/** Picks the custom theme's colour on a wheel or as a hex code. */
@Composable
private fun CustomColorDialog(customPrimaryColorDialogState: DialogState) {
    val context = LocalContext.current
    var customPrimaryColor by remember { mutableStateOf(savedCustomColor(context)) }
    Dialog(
        dialogState = customPrimaryColorDialogState,
        title = { Text(text = stringResource(id = R.string.title_custom_theme)) },
        buttons = {
            DialogPositiveButton(
                text = stringResource(id = R.string.button_finish),
                onClick = {
                    context.appPreferences.customPrimaryColor = customPrimaryColor.toHexString()
                    ThemeUtil.selectAccent(ThemeUtil.THEME_CUSTOM)
                }
            )
            DialogNegativeButton(
                text = stringResource(id = R.string.button_cancel),
                onClick = {
                    customPrimaryColor = savedCustomColor(context)
                }
            )
        }
    ) {
        var useInput by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedContent(
                targetState = useInput,
                label = "",
                modifier = Modifier
                    .wrapContentHeight()
                    .animateContentSize()
            ) { input ->
                if (input) {
                    var inputHexColor by remember { mutableStateOf(customPrimaryColor.toHexString()) }
                    val lastValidColor by produceState(
                        initialValue = customPrimaryColor,
                        inputHexColor
                    ) {
                        if ("^#([0-9a-fA-F]{6})$".toRegex().matches(inputHexColor)) {
                            value = Color(inputHexColor.toColorInt())
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Spacer(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(lastValidColor)
                        )
                        OutlinedTextField(
                            value = inputHexColor,
                            onValueChange = {
                                if ("^#([0-9a-fA-F]{0,6})$".toRegex().matches(it)) {
                                    inputHexColor = it
                                }
                            },
                            maxLines = 1,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                cursorColor = ExtendedTheme.colors.emphasis,
                                focusedBorderColor = ExtendedTheme.colors.emphasis,
                                focusedLabelColor = ExtendedTheme.colors.emphasis
                            )
                        )
                        IconButton(
                            onClick = {
                                customPrimaryColor = Color(inputHexColor.toColorInt())
                                useInput = false
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = stringResource(id = R.string.button_sure_default)
                            )
                        }
                    }
                } else {
                    Box {
                        HarmonyColorPicker(
                            harmonyMode = ColorHarmonyMode.ANALOGOUS,
                            color = HsvColor.from(customPrimaryColor),
                            onColorChanged = {
                                customPrimaryColor = it.toColor()
                            },
                            modifier = Modifier.sizeIn(maxWidth = 320.dp, maxHeight = 320.dp)
                        )

                        IconButton(
                            onClick = { useInput = true },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BorderColor,
                                contentDescription = stringResource(id = R.string.desc_input_color)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The custom theme's colour as saved, or its default. */
private fun savedCustomColor(context: Context): Color =
    themeColors(ThemeUtil.THEME_CUSTOM, pickedColor(context, ThemeUtil.THEME_CUSTOM)).accent
