package oolong.client.ui.page.settings.post

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import oolong.client.R
import oolong.client.ui.common.settings.SettingsScreen
import oolong.client.ui.common.settings.settingsPage
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.navigation.PostSettingsKey
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.page.ProvideNavigator
import oolong.client.utils.isPhotoPickerAvailable

/** Posting and replying. */
val PostSettings = settingsPage(R.string.title_settings_post, PostSettingsKey) {
    rows {
        switch("postOrReplyWarning", R.string.title_post_or_reply_warning, default = true)
        switch("hideReply", R.string.title_hide_reply, default = false)
        choice(
            "pic_watermark_type", R.string.title_settings_image_watermark, default = "2",
            "0" to R.string.title_image_watermark_none,
            "1" to R.string.title_image_watermark_user_name,
            "2" to R.string.title_image_watermark_forum_name,
        )
        switch(
            "doNotUsePhotoPicker", R.string.title_do_not_use_photo_picker, default = false,
            // Only said when there is no system picker to choose instead
            summary = { stringResource(id = R.string.summary_photo_picker_not_supported).takeUnless { isPhotoPickerAvailable() } },
            enabled = { isPhotoPickerAvailable() },
        )
    }
}

@Composable
fun PostSettingsPage(navigator: Navigator = LocalNavigator.current) {
    ProvideNavigator(navigator) { SettingsScreen(PostSettings) }
}
