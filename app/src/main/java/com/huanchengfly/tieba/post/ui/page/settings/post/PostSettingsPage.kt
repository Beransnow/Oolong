package com.huanchengfly.tieba.post.ui.page.settings.post

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.dataStore
import com.huanchengfly.tieba.post.ui.common.prefs.PrefsScreen
import com.huanchengfly.tieba.post.ui.common.prefs.widgets.ListPref
import com.huanchengfly.tieba.post.ui.common.prefs.widgets.SwitchPref
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBar
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBarTitle
import com.huanchengfly.tieba.post.utils.isPhotoPickerAvailable

/** Posting and replying: the risk warning, the reply entry, watermarks and the picture picker. */
@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeUiApi::class)
@Composable
fun PostSettingsPage(
    navigator: Navigator = LocalNavigator.current
) {
    val context = LocalContext.current
    val pickerNotSupported = stringResource(id = R.string.summary_photo_picker_not_supported)

    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = R.string.title_settings_post)) },
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
            prefsItem {
                SwitchPref(
                    key = "postOrReplyWarning",
                    title = stringResource(id = R.string.title_post_or_reply_warning),
                    defaultChecked = true,
                )
            }
            prefsItem {
                SwitchPref(
                    key = "hideReply",
                    title = stringResource(id = R.string.title_hide_reply),
                    defaultChecked = false,
                )
            }
            prefsItem {
                ListPref(
                    key = "pic_watermark_type",
                    title = stringResource(id = R.string.title_settings_image_watermark),
                    entries = mapOf(
                        "0" to stringResource(id = R.string.title_image_watermark_none),
                        "1" to stringResource(id = R.string.title_image_watermark_user_name),
                        "2" to stringResource(id = R.string.title_image_watermark_forum_name)
                    ),
                    useSelectedAsSummary = true,
                    defaultValue = "2",
                )
            }
            prefsItem {
                SwitchPref(
                    key = "doNotUsePhotoPicker",
                    title = stringResource(id = R.string.title_do_not_use_photo_picker),
                    // Only said when there is no system picker to choose instead
                    summary = { pickerNotSupported.takeUnless { isPhotoPickerAvailable() } },
                    defaultChecked = false,
                    enabled = isPhotoPickerAvailable()
                )
            }
        }
    }
}
