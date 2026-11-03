package oolong.client.ui.page.settings.custom

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.collections.immutable.persistentListOf
import oolong.client.R
import oolong.client.dataStore
import oolong.client.rememberPreferenceAsMutableState
import oolong.client.rememberPreferenceAsState
import oolong.client.ui.common.navigation.PredictiveBackKey
import oolong.client.ui.common.prefs.PrefsChoiceValue
import oolong.client.ui.common.prefs.PrefsLinkValue
import oolong.client.ui.common.prefs.PrefsScreen
import oolong.client.ui.common.prefs.widgets.ListPref
import oolong.client.ui.common.prefs.widgets.SwitchPref
import oolong.client.ui.common.prefs.widgets.TextPref
import oolong.client.ui.navigation.AppThemeKey
import oolong.client.ui.navigation.FontSizeKey
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.navigation.SearchPlacementKey
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.page.main.SearchAtTopKey
import oolong.client.ui.widgets.compose.BackNavigationIcon
import oolong.client.ui.widgets.compose.ChromeStyle
import oolong.client.ui.widgets.compose.ChromeStyleKey
import oolong.client.ui.widgets.compose.DefaultGlassClarity
import oolong.client.ui.widgets.compose.GlassClarityKey
import oolong.client.ui.widgets.compose.GlassMaterialKey
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTier
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.GlassTopBarTitle
import oolong.client.ui.widgets.compose.Sizes
import oolong.client.ui.widgets.compose.Slider
import oolong.client.ui.widgets.compose.available
import oolong.client.ui.widgets.compose.choiceMenuItems
import oolong.client.ui.widgets.compose.chosenGlassTier
import oolong.client.ui.widgets.compose.clickMenu
import oolong.client.ui.widgets.compose.menuAnchor
import oolong.client.ui.widgets.compose.rememberMenuTriggerState
import oolong.client.utils.AppIconUtil
import oolong.client.utils.LauncherIcons
import oolong.client.utils.ThemeSettings
import oolong.client.utils.ThemeUtil
import oolong.client.utils.appPreferences
import kotlin.math.roundToInt

/** The colour in use, by the name the theme page gives it. */
@Composable
private fun currentThemeName(): String {
    val settings by ThemeUtil.settingsState
    val names = stringArrayResource(id = R.array.accent_names)
    return when {
        settings.isSystemColor -> stringResource(id = R.string.accent_system_wallpaper)
        settings.accent == ThemeUtil.THEME_CUSTOM -> stringResource(id = R.string.title_custom_color)
        else -> names.getOrNull(ThemeSettings.ACCENTS.indexOf(settings.accent)).orEmpty()
    }
}

/** Appearance: the theme, night mode, text size and the app's icon, and the glass effects. */
@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeUiApi::class)
@Composable
fun CustomSettingsPage(
    navigator: Navigator = LocalNavigator.current
) {
    val context = LocalContext.current
    val themeGroup = stringResource(id = R.string.settings_group_theme)
    val textAndIconGroup = stringResource(id = R.string.settings_group_text_and_icon)
    val effectsGroup = stringResource(id = R.string.settings_group_effects)
    val glassTiers = remember { GlassTier.available }
    val glassTier = chosenGlassTier()
    GlassScaffold(
        largeTitle = true,
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = R.string.title_settings_custom)) },
                navigationIcon = {
                    BackNavigationIcon(onBackPressed = { navigator.navigateUp() })
                }
            )
        },
    ) { paddingValues ->
        PrefsScreen(
            dataStore = context.dataStore,
            dividerThickness = 0.dp,
            modifier = Modifier.fillMaxSize(),
            contentPadding = paddingValues,
        ) {
            prefsGroup(themeGroup) {
                prefsItem {
                    TextPref(
                        title = stringResource(id = R.string.title_theme),
                        onClick = { navigator.navigate(AppThemeKey) },
                        trailingContent = { PrefsLinkValue(text = currentThemeName()) },
                    )
                }
            }
            prefsGroup(header = { Spacer(modifier = Modifier.height(8.dp)) }) {
                prefsItem { LanguagePref() }
                prefsItem {
                    val atTop by rememberPreferenceAsState(SearchAtTopKey, false)
                    TextPref(
                        title = stringResource(id = R.string.title_search_placement),
                        onClick = { navigator.navigate(SearchPlacementKey) },
                        trailingContent = {
                            PrefsLinkValue(text = stringResource(id = if (atTop) R.string.search_place_top else R.string.search_place_bottom))
                        },
                    )
                }
            }
            prefsGroup(textAndIconGroup) {
                prefsItem {
                    TextPref(
                        title = stringResource(id = R.string.title_custom_font_size),
                        onClick = {
                            navigator.navigate(FontSizeKey)
                        },
                        trailingContent = {
                            PrefsLinkValue(text = "${(context.appPreferences.fontScale * 100).roundToInt()}%")
                        },
                    )
                }
                prefsItem {
                    ListPref(
                        key = "app_icon",
                        title = stringResource(id = R.string.settings_app_icon),
                        defaultValue = LauncherIcons.DEFAULT_ICON,
                        entries = mapOf(
                            LauncherIcons.NEW_ICON to stringResource(id = R.string.app_icon_new),
                            LauncherIcons.NEW_ICON_INVERT to stringResource(id = R.string.app_icon_new_inverted),
                            LauncherIcons.OLD_ICON to stringResource(id = R.string.app_icon_old),
                        ),
                        icons = mapOf(
                            LauncherIcons.NEW_ICON to {
                                Image(
                                    painter = rememberDrawablePainter(
                                        drawable = LocalContext.current.getDrawable(
                                            R.drawable.ic_launcher_new_round
                                        )
                                    ),
                                    contentDescription = stringResource(id = R.string.app_icon_new),
                                    modifier = Modifier.size(Sizes.Medium)
                                )
                            },
                            LauncherIcons.NEW_ICON_INVERT to {
                                Image(
                                    painter = rememberDrawablePainter(
                                        drawable = LocalContext.current.getDrawable(
                                            R.drawable.ic_launcher_new_invert_round
                                        )
                                    ),
                                    contentDescription = stringResource(id = R.string.app_icon_new_inverted),
                                    modifier = Modifier.size(Sizes.Medium)
                                )
                            },
                            LauncherIcons.OLD_ICON to {
                                Image(
                                    painter = rememberDrawablePainter(
                                        drawable = LocalContext.current.getDrawable(
                                            R.drawable.ic_launcher_round
                                        )
                                    ),
                                    contentDescription = stringResource(id = R.string.app_icon_old),
                                    modifier = Modifier.size(Sizes.Medium)
                                )
                            },
                        ),
                        onValueChange = { AppIconUtil.setIcon(icon = it) },
                        useSelectedAsSummary = true,
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    prefsItem {
                        val supportThemedIcons = remember {
                            persistentListOf(
                                LauncherIcons.NEW_ICON,
                                //                            LauncherIcons.NEW_ICON_INVERT,
                            )
                        }
                        val currentLauncherIcon by rememberPreferenceAsState(
                            key = stringPreferencesKey("app_icon"),
                            defaultValue = LauncherIcons.NEW_ICON
                        )
                        val isCurrentSupportThemedIcon by remember {
                            derivedStateOf {
                                supportThemedIcons.contains(currentLauncherIcon)
                            }
                        }
                        SwitchPref(
                            key = "useThemedIcon",
                            title = stringResource(id = R.string.title_settings_use_themed_icon),
                            defaultChecked = false,
                            enabled = isCurrentSupportThemedIcon,
                            onCheckedChange = {
                                AppIconUtil.setIcon(isThemed = it)
                            },
                            summary = stringResource(id = R.string.tip_settings_use_themed_icon_summary_not_supported).takeUnless { isCurrentSupportThemedIcon },
                        )
                    }
                }
            }
            prefsGroup(effectsGroup) {
                prefsItem { ChromeStylePref() }
                // Where the device draws only one material, there is nothing to choose
                if (glassTiers.size > 1) {
                    prefsItem { GlassMaterialPref(glassTiers, glassTier) }
                    if (glassTier != GlassTier.Solid) prefsItem { GlassClarityPref() }
                }
                prefsItem {
                    SwitchPref(
                        key = PredictiveBackKey.name,
                        title = stringResource(id = R.string.title_settings_predictive_back),
                        summary = stringResource(id = R.string.summary_settings_predictive_back),
                        defaultChecked = true,
                    )
                }
            }
        }
    }
}

/** How the bars are laid out: floating glass, or the classic bands. */
@Composable
private fun ChromeStylePref() {
    var chosen by rememberPreferenceAsMutableState(ChromeStyleKey, ChromeStyle.Floating.name)
    ChoicePref(
        title = stringResource(id = R.string.title_chrome_style),
        options = listOf(
            ChromeStyle.Floating.name to stringResource(id = R.string.chrome_style_floating),
            ChromeStyle.Classic.name to stringResource(id = R.string.chrome_style_classic),
        ),
        current = chosen,
        onSelect = { chosen = it },
    )
}

/** The material chrome is drawn in, of those the device draws: glass, frosted, or opaque. */
@Composable
private fun GlassMaterialPref(tiers: List<GlassTier>, chosen: GlassTier) {
    var material by rememberPreferenceAsMutableState(GlassMaterialKey, "")
    ChoicePref(
        title = stringResource(id = R.string.title_glass_material),
        options = tiers.map {
            it.name to stringResource(
                id = when (it) {
                    GlassTier.Glass -> R.string.glass_material_glass
                    GlassTier.Blur -> R.string.glass_material_blur
                    GlassTier.Solid -> R.string.glass_material_solid
                }
            )
        },
        current = chosen.name,
        onSelect = { material = it },
    )
}

/** A setting with a few [options], keys to names, picked from a menu out of its value. */
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun ChoicePref(title: String, options: List<Pair<String, String>>, current: String, onSelect: (String) -> Unit) {
    val menu = rememberMenuTriggerState()
    Box(modifier = Modifier.clickMenu(state = menu, items = { choiceMenuItems(options, current, onSelect) })) {
        TextPref(
            title = title,
            enabled = true,
            trailingContent = {
                PrefsChoiceValue(text = options.firstOrNull { it.first == current }?.second.orEmpty(), modifier = Modifier.menuAnchor(menu))
            },
        )
    }
}

/** How clear the glass is, from frosted to clear; the chrome on the page shows it as it moves. */
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun GlassClarityPref() {
    var clarity by rememberPreferenceAsMutableState(GlassClarityKey, DefaultGlassClarity)
    Column {
        TextPref(title = stringResource(id = R.string.title_glass_clarity), minimalHeight = true, darkenOnDisable = false)
        Slider(
            value = clarity,
            onValueChange = { clarity = it },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
    }
}

/**
 * The language the app is shown in: the system's, or one chosen for the app alone, which the
 * system keeps as the app's language and shows in its own settings on Android 13 and later.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun LanguagePref() {
    val options = listOf(
        "" to stringResource(id = R.string.language_system),
        "zh-CN" to stringResource(id = R.string.language_zh_cn),
        "en" to stringResource(id = R.string.language_en),
    )
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags().let { tags ->
        options.firstOrNull { (tag, _) -> tag.isNotEmpty() && tags.startsWith(tag.substringBefore('-')) }?.first ?: ""
    }
    val menu = rememberMenuTriggerState()
    Box(
        modifier = Modifier.clickMenu(
            state = menu,
            items = {
                choiceMenuItems(options, current) { tag ->
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                }
            },
        ),
    ) {
        TextPref(
            title = stringResource(id = R.string.title_language),
            enabled = true,
            trailingContent = {
                PrefsChoiceValue(text = options.first { it.first == current }.second, modifier = Modifier.menuAnchor(menu))
            },
        )
    }
}
