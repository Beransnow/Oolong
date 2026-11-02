package com.huanchengfly.tieba.post.ui.page.settings.custom

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.dataStore
import com.huanchengfly.tieba.post.rememberPreferenceAsState
import com.huanchengfly.tieba.post.ui.common.prefs.PrefsLinkValue
import com.huanchengfly.tieba.post.ui.common.prefs.PrefsScreen
import com.huanchengfly.tieba.post.ui.common.prefs.widgets.ListPref
import com.huanchengfly.tieba.post.ui.common.prefs.widgets.SwitchPref
import com.huanchengfly.tieba.post.ui.common.prefs.widgets.TextPref
import com.huanchengfly.tieba.post.ui.navigation.AppThemeKey
import com.huanchengfly.tieba.post.ui.navigation.FontSizeKey
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBar
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBarTitle
import com.huanchengfly.tieba.post.ui.widgets.compose.ReduceTransparencyKey
import com.huanchengfly.tieba.post.ui.widgets.compose.Sizes
import com.huanchengfly.tieba.post.utils.AppIconUtil
import com.huanchengfly.tieba.post.utils.LauncherIcons
import com.huanchengfly.tieba.post.utils.ThemeUtil
import com.huanchengfly.tieba.post.utils.appPreferences
import kotlinx.collections.immutable.persistentListOf
import kotlin.math.roundToInt

/** The theme in use, by the name the theme page gives it. */
@Composable
private fun currentThemeName(): String {
    val theme by ThemeUtil.themeState
    val values = stringArrayResource(id = R.array.theme_values)
    val names = stringArrayResource(id = R.array.themeNames)
    return when {
        ThemeUtil.settingsState.value.isDynamic -> stringResource(id = R.string.title_dynamic_theme)
        theme == ThemeUtil.THEME_CUSTOM -> stringResource(id = R.string.title_custom_color)
        theme.startsWith(ThemeUtil.THEME_TRANSLUCENT) -> stringResource(id = R.string.title_theme_translucent)
        else -> names.getOrNull(values.indexOf(theme)).orEmpty()
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
    val nightGroup = stringResource(id = R.string.settings_group_night)
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
            prefsGroup(nightGroup) {
                prefsItem {
                    SwitchPref(
                        key = "follow_system_night",
                        title = stringResource(id = R.string.title_settings_follow_system_night),
                        defaultChecked = true,
                        onCheckedChange = { ThemeUtil.setFollowSystem(it) },
                    )
                }
                prefsItem {
                    val darkThemeValues = stringArrayResource(id = R.array.dark_theme_values)
                    val darkThemeNames = stringArrayResource(id = R.array.dark_theme_names)
                    ListPref(
                        key = "dark_theme",
                        title = stringResource(id = R.string.settings_night_mode),
                        defaultValue = ThemeUtil.THEME_AMOLED_DARK,
                        entries = darkThemeValues.associateWith { value ->
                            darkThemeNames[darkThemeValues.indexOf(
                                value
                            )]
                        },
                        useSelectedAsSummary = true,
                        onValueChange = { ThemeUtil.updateSettings { settings -> settings.copy(darkTheme = it) } },
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
                            LauncherIcons.NEW_ICON to "新图标",
                            LauncherIcons.NEW_ICON_INVERT to "新图标（反色）",
                            LauncherIcons.OLD_ICON to "旧图标",
                        ),
                        icons = mapOf(
                            LauncherIcons.NEW_ICON to {
                                Image(
                                    painter = rememberDrawablePainter(
                                        drawable = LocalContext.current.getDrawable(
                                            R.drawable.ic_launcher_new_round
                                        )
                                    ),
                                    contentDescription = "新图标",
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
                                    contentDescription = "新图标（反色）",
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
                                    contentDescription = "旧图标",
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
            }
        }
    }
}
