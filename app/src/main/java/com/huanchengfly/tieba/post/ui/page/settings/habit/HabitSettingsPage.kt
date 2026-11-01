package com.huanchengfly.tieba.post.ui.page.settings.habit

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.outlined.CalendarViewDay
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.PhotoSizeSelectActual
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.dataStore
import com.huanchengfly.tieba.post.ui.common.prefs.PrefsScreen
import com.huanchengfly.tieba.post.ui.common.prefs.widgets.ListPref
import com.huanchengfly.tieba.post.ui.common.prefs.widgets.SwitchPref
import com.huanchengfly.tieba.post.ui.page.settings.LeadingIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.AvatarIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBar
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBarTitle
import com.huanchengfly.tieba.post.ui.widgets.compose.Sizes
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

/** Browsing: the home page, forums, threads and pictures. */
@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeUiApi::class)
@Destination
@Composable
fun HabitSettingsPage(
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    val homeGroup = stringResource(id = R.string.settings_group_home)
    val forumGroup = stringResource(id = R.string.settings_group_forum)
    val threadGroup = stringResource(id = R.string.settings_group_thread)
    val imageGroup = stringResource(id = R.string.settings_group_image)
    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = R.string.title_settings_read_habit)) },
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
            prefsGroup(homeGroup) {
                prefsItem {
                    SwitchPref(
                        key = "listSingle",
                        title = stringResource(id = R.string.settings_forum_single),
                        defaultChecked = false,
                    ) {
                        LeadingIcon {
                            AvatarIcon(
                                icon = Icons.Outlined.ViewAgenda,
                                size = Sizes.Small,
                                contentDescription = null,
                            )
                        }
                    }
                }
                prefsItem {
                    SwitchPref(
                        key = "homePageShowHistoryForum",
                        title = stringResource(id = R.string.settings_home_page_show_history_forum),
                        defaultChecked = true,
                        leadingIcon = {
                            LeadingIcon {
                                AvatarIcon(
                                    icon = Icons.Outlined.WatchLater,
                                    size = Sizes.Small,
                                    contentDescription = null,
                                )
                            }
                        },
                    )
                }
                prefsItem {
                    SwitchPref(
                        key = "hideExplore",
                        title = stringResource(id = R.string.title_hide_explore),
                        defaultChecked = false,
                    ) {
                        LeadingIcon {
                            AvatarIcon(
                                icon = Icons.Outlined.Explore,
                                size = Sizes.Small,
                                contentDescription = null,
                            )
                        }
                    }
                }
            }
            prefsGroup(forumGroup) {
                prefsItem {
                    ListPref(
                        key = "default_sort_type",
                        title = stringResource(id = R.string.title_settings_default_sort_type),
                        entries = mapOf(
                            "0" to stringResource(id = R.string.title_sort_by_reply),
                            "1" to stringResource(id = R.string.title_sort_by_send),
                        ),
                        useSelectedAsSummary = true,
                        defaultValue = "0",
                        leadingIcon = {
                            LeadingIcon {
                                AvatarIcon(
                                    icon = Icons.Outlined.CalendarViewDay,
                                    size = Sizes.Small,
                                    contentDescription = null,
                                )
                            }
                        },
                    )
                }
                prefsItem {
                    ListPref(
                        key = "forumFabFunction",
                        title = stringResource(id = R.string.settings_forum_fab_function),
                        defaultValue = "hide",
                        leadingIcon = {
                            LeadingIcon {
                                AvatarIcon(
                                    icon = Icons.AutoMirrored.Outlined.ExitToApp,
                                    size = Sizes.Small,
                                    contentDescription = null,
                                )
                            }
                        },
                        useSelectedAsSummary = true,
                        entries = mapOf(
                            "refresh" to stringResource(id = R.string.btn_refresh),
                            "back_to_top" to stringResource(id = R.string.btn_back_to_top),
                            "hide" to stringResource(id = R.string.btn_hide)
                        )
                    )
                }
            }
            prefsGroup(threadGroup) {
                prefsItem {
                    SwitchPref(
                        key = "collect_thread_see_lz",
                        title = stringResource(id = R.string.settings_collect_thread_see_lz),
                        defaultChecked = true,
                        leadingIcon = {
                            LeadingIcon {
                                AvatarIcon(
                                    icon = Icons.Outlined.StarOutline,
                                    size = Sizes.Small,
                                    contentDescription = null,
                                )
                            }
                        },
                        summaryOn = stringResource(id = R.string.tip_collect_thread_see_lz_on),
                        summaryOff = stringResource(id = R.string.tip_collect_thread_see_lz)
                    )
                }
                prefsItem {
                    SwitchPref(
                        key = "collect_thread_desc_sort",
                        title = stringResource(id = R.string.settings_collect_thread_desc_sort),
                        defaultChecked = false,
                        leadingIcon = {
                            LeadingIcon {
                                AvatarIcon(
                                    icon = Icons.AutoMirrored.Rounded.Sort,
                                    size = Sizes.Small,
                                    contentDescription = null,
                                )
                            }
                        },
                        summaryOn = stringResource(id = R.string.tip_collect_thread_desc_sort_on),
                        summaryOff = stringResource(id = R.string.tip_collect_thread_desc_sort)
                    )
                }
                prefsItem {
                    SwitchPref(
                        key = "show_both_username_and_nickname",
                        title = stringResource(id = R.string.title_show_both_username_and_nickname),
                        defaultChecked = false,
                    ) {
                        LeadingIcon {
                            AvatarIcon(
                                icon = Icons.Outlined.Verified,
                                size = Sizes.Small,
                                contentDescription = null,
                            )
                        }
                    }
                }
            }
            prefsGroup(imageGroup) {
                prefsItem {
                    ListPref(
                        key = "image_load_type",
                        title = stringResource(id = R.string.title_settings_image_load_type),
                        entries = mapOf(
                            "0" to stringResource(id = R.string.title_image_load_type_smart_origin),
                            "1" to stringResource(id = R.string.title_image_load_type_smart_load),
                            "2" to stringResource(id = R.string.title_image_load_type_all_origin),
                            "3" to stringResource(id = R.string.title_image_load_type_all_no)
                        ),
                        useSelectedAsSummary = true,
                        defaultValue = "0",
                        leadingIcon = {
                            LeadingIcon {
                                AvatarIcon(
                                    icon = Icons.Outlined.PhotoSizeSelectActual,
                                    size = Sizes.Small,
                                    contentDescription = null,
                                )
                            }
                        },
                    )
                }
                prefsItem {
                    SwitchPref(
                        key = "imageDarkenWhenNightMode",
                        title = stringResource(id = R.string.settings_image_darken_when_night_mode),
                        defaultChecked = true,
                    ) {
                        LeadingIcon {
                            AvatarIcon(
                                icon = Icons.Outlined.NightsStay,
                                size = Sizes.Small,
                                contentDescription = null,
                            )
                        }
                    }
                }
                prefsItem {
                    SwitchPref(
                        key = "hideMedia",
                        title = stringResource(id = R.string.title_hide_media),
                        defaultChecked = false
                    ) {
                        LeadingIcon {
                            AvatarIcon(
                                icon = ImageVector.vectorResource(R.drawable.ic_outline_collapse_all),
                                size = Sizes.Small,
                                contentDescription = null,
                            )
                        }
                    }
                }
            }
        }
    }
}
