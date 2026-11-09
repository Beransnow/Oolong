package oolong.client.ui.page.settings.habit

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import oolong.client.R
import oolong.client.repository.SubPostSort
import oolong.client.ui.common.settings.SettingsScreen
import oolong.client.ui.common.settings.settingsPage
import oolong.client.ui.navigation.HabitSettingsKey
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.page.ProvideNavigator

/** Browsing: the home page, forums, threads and pictures. */
val HabitSettings = settingsPage(R.string.title_settings_read_habit, HabitSettingsKey) {
    group(R.string.settings_group_home) {
        switch("listSingle", R.string.settings_forum_single, default = false)
        switch("homePageShowHistoryForum", R.string.settings_home_page_show_history_forum, default = true)
        switch("hideExplore", R.string.title_hide_explore, default = false)
        switch("feedAutoRefresh", R.string.title_feed_auto_refresh, default = true, summary = { stringResource(id = R.string.summary_feed_auto_refresh) })
    }
    group(R.string.settings_group_forum) {
        choice(
            "default_sort_type", R.string.title_settings_default_sort_type, default = "0",
            "0" to R.string.title_sort_by_reply,
            "1" to R.string.title_sort_by_send,
        )
        choice(
            "forumFabFunction", R.string.settings_forum_fab_function, default = "hide",
            "refresh" to R.string.btn_refresh,
            "back_to_top" to R.string.btn_back_to_top,
            "hide" to R.string.btn_hide,
        )
    }
    group(R.string.settings_group_thread) {
        switch("show_both_username_and_nickname", R.string.title_show_both_username_and_nickname, default = false)
        choice(
            "subPostSort", R.string.title_sub_post_sort, default = SubPostSort.Hot.value,
            SubPostSort.Hot.value to R.string.sub_post_sort_hot,
            SubPostSort.Earliest.value to R.string.sub_post_sort_earliest,
        )
        switch("hideIpLocation", R.string.title_hide_ip_location, default = false)
    }
    group(R.string.settings_group_collected) {
        switch("collect_thread_see_lz", R.string.settings_collect_thread_see_lz, default = true)
        switch("collect_thread_desc_sort", R.string.settings_collect_thread_desc_sort, default = false)
    }
    group(R.string.settings_group_image) {
        choice(
            "image_load_type", R.string.title_settings_image_load_type, default = "0",
            "0" to R.string.title_image_load_type_smart_origin,
            "1" to R.string.title_image_load_type_smart_load,
            "2" to R.string.title_image_load_type_all_origin,
            "3" to R.string.title_image_load_type_all_no,
        )
        switch("imageDarkenWhenNightMode", R.string.settings_image_darken_when_night_mode, default = true)
        switch("videoAutoPip", R.string.settings_video_auto_pip, default = true)
        switch("hideMedia", R.string.title_hide_media, default = false)
    }
}

@Composable
fun HabitSettingsPage(navigator: Navigator = LocalNavigator.current) {
    ProvideNavigator(navigator) { SettingsScreen(HabitSettings) }
}
