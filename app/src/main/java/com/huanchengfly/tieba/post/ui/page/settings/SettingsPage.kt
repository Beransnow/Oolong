package com.huanchengfly.tieba.post.ui.page.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.dataStore
import com.huanchengfly.tieba.post.models.database.Account
import com.huanchengfly.tieba.post.ui.common.LocalAccount
import com.huanchengfly.tieba.post.ui.common.prefs.PrefsScreen
import com.huanchengfly.tieba.post.ui.common.prefs.widgets.TextPref
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.navigation.AccountManageKey
import com.huanchengfly.tieba.post.ui.navigation.BlockSettingsKey
import com.huanchengfly.tieba.post.ui.navigation.CustomSettingsKey
import com.huanchengfly.tieba.post.ui.navigation.HabitSettingsKey
import com.huanchengfly.tieba.post.ui.navigation.LoginKey
import com.huanchengfly.tieba.post.ui.navigation.MoreSettingsKey
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.navigation.OKSignSettingsKey
import com.huanchengfly.tieba.post.ui.navigation.PostSettingsKey
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.page.ProvideNavigator
import com.huanchengfly.tieba.post.ui.widgets.compose.Avatar
import com.huanchengfly.tieba.post.ui.widgets.compose.AvatarIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBar
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBarTitle
import com.huanchengfly.tieba.post.ui.widgets.compose.Sizes
import com.huanchengfly.tieba.post.utils.StringUtil

/** An entry's icon on the settings hub: a line icon in the text's colour, as on the "me" tab. */
@Composable
internal fun LeadingIcon(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalContentColor provides ExtendedTheme.colors.text) {
        content()
    }
}

/** The account at the top of the hub: who is logged in, leading to their accounts, or to logging in. */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun NowAccountItem(
    account: Account?,
    modifier: Modifier = Modifier
) {
    val navigator = LocalNavigator.current
    if (account != null) {
        TextPref(
            title = account.nameShow ?: account.name,
            onClick = { navigator.navigate(AccountManageKey) },
            leadingIcon = {
                Avatar(
                    data = StringUtil.getAvatarUrl(account.portrait),
                    size = Sizes.Small,
                    contentDescription = null
                )
            },
            modifier = modifier,
        )
    } else {
        TextPref(
            title = stringResource(id = R.string.title_login),
            onClick = { navigator.navigate(LoginKey) },
            leadingIcon = {
                AvatarIcon(
                    icon = Icons.Rounded.AccountCircle,
                    size = Sizes.Small,
                    contentDescription = stringResource(id = R.string.title_new_account),
                    color = ExtendedTheme.colors.onChip,
                    backgroundColor = ExtendedTheme.colors.background,
                )
            },
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SettingsPage(
    navigator: Navigator = LocalNavigator.current,
) {
    ProvideNavigator(navigator = navigator) {
        GlassScaffold(
            backgroundColor = Color.Transparent,
            topBar = {
                GlassTopBar(
                    title = { GlassTopBarTitle(text = stringResource(id = R.string.title_settings)) },
                    navigationIcon = {
                        BackNavigationIcon(onBackPressed = { navigator.navigateUp() })
                    }
                )
            },
        ) {
            PrefsScreen(
                dataStore = LocalContext.current.dataStore,
                dividerThickness = 0.dp,
                modifier = Modifier.fillMaxSize(),
                contentPadding = it,
            ) {
                // Untitled groups: the account, how the app looks and reads, what it hides and
                // does by itself, and the rest
                prefsGroup(header = { Spacer(modifier = Modifier.height(8.dp)) }) {
                    prefsItem {
                        NowAccountItem(account = LocalAccount.current)
                    }
                }
                prefsGroup(header = { Spacer(modifier = Modifier.height(8.dp)) }) {
                    prefsItem {
                        TextPref(
                            title = stringResource(id = R.string.title_settings_custom),
                            leadingIcon = { LeadingIcon { Icon(imageVector = Icons.Outlined.Palette, contentDescription = null) } },
                            onClick = { navigator.navigate(CustomSettingsKey) }
                        )
                    }
                    prefsItem {
                        TextPref(
                            title = stringResource(id = R.string.title_settings_read_habit),
                            leadingIcon = { LeadingIcon { Icon(imageVector = Icons.Outlined.AutoStories, contentDescription = null) } },
                            onClick = { navigator.navigate(HabitSettingsKey) }
                        )
                    }
                    prefsItem {
                        TextPref(
                            title = stringResource(id = R.string.title_settings_post),
                            leadingIcon = { LeadingIcon { Icon(imageVector = Icons.Outlined.EditNote, contentDescription = null) } },
                            onClick = { navigator.navigate(PostSettingsKey) }
                        )
                    }
                }
                prefsGroup(header = { Spacer(modifier = Modifier.height(8.dp)) }) {
                    prefsItem {
                        TextPref(
                            title = stringResource(id = R.string.title_block_settings),
                            leadingIcon = { LeadingIcon { Icon(imageVector = Icons.Outlined.Block, contentDescription = null) } },
                            onClick = { navigator.navigate(BlockSettingsKey) }
                        )
                    }
                    prefsItem {
                        TextPref(
                            title = stringResource(id = R.string.title_oksign),
                            leadingIcon = { LeadingIcon { Icon(imageVector = Icons.Outlined.TaskAlt, contentDescription = null) } },
                            onClick = { navigator.navigate(OKSignSettingsKey) }
                        )
                    }
                }
                prefsGroup(header = { Spacer(modifier = Modifier.height(8.dp)) }) {
                    prefsItem {
                        TextPref(
                            title = stringResource(id = R.string.title_settings_more),
                            leadingIcon = { LeadingIcon { Icon(imageVector = Icons.Outlined.MoreHoriz, contentDescription = null) } },
                            onClick = { navigator.navigate(MoreSettingsKey) }
                        )
                    }
                }
            }
        }
    }
}