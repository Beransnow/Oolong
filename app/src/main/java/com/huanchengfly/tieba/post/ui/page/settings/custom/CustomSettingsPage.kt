package com.huanchengfly.tieba.post.ui.page.settings.custom

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.dataStore
import com.huanchengfly.tieba.post.rememberPreferenceAsState
import com.huanchengfly.tieba.post.ui.common.navigation.PredictiveBackKey
import com.huanchengfly.tieba.post.ui.common.prefs.PrefsChoiceValue
import com.huanchengfly.tieba.post.ui.common.prefs.PrefsLinkValue
import com.huanchengfly.tieba.post.ui.common.prefs.PrefsScreen
import com.huanchengfly.tieba.post.ui.common.prefs.widgets.ListPref
import com.huanchengfly.tieba.post.ui.common.prefs.widgets.SwitchPref
import com.huanchengfly.tieba.post.ui.common.prefs.widgets.TextPref
import com.huanchengfly.tieba.post.ui.navigation.AppThemeKey
import com.huanchengfly.tieba.post.ui.navigation.FontSizeKey
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.navigation.SearchPlacementKey
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.page.main.SearchAtTopKey
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBar
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBarTitle
import com.huanchengfly.tieba.post.ui.widgets.compose.ReduceTransparencyKey
import com.huanchengfly.tieba.post.ui.widgets.compose.Sizes
import com.huanchengfly.tieba.post.ui.widgets.compose.choiceMenuItems
import com.huanchengfly.tieba.post.ui.widgets.compose.clickMenu
import com.huanchengfly.tieba.post.ui.widgets.compose.menuAnchor
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberMenuTriggerState
import com.huanchengfly.tieba.post.utils.AppIconUtil
import com.huanchengfly.tieba.post.utils.LauncherIcons
import com.huanchengfly.tieba.post.utils.ThemeSettings
import com.huanchengfly.tieba.post.utils.ThemeUtil
import com.huanchengfly.tieba.post.utils.appPreferences
import kotlinx.collections.immutable.persistentListOf
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
    GlassScaffold(
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
                prefsItem {
                    SwitchPref(
                        key = ReduceTransparencyKey.name,
                        title = stringResource(id = R.string.title_settings_reduce_transparency),
                        defaultChecked = false,
                    )
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
