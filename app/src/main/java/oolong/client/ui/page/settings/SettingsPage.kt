package oolong.client.ui.page.settings

import android.content.Intent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import oolong.client.BuildConfig
import oolong.client.R
import oolong.client.dataStore
import oolong.client.models.database.Account
import oolong.client.ui.common.LocalAccount
import oolong.client.ui.common.prefs.PrefsScreen
import oolong.client.ui.common.prefs.widgets.TextPref
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.navigation.AccountManageKey
import oolong.client.ui.navigation.BlockSettingsKey
import oolong.client.ui.navigation.ControlCatalogKey
import oolong.client.ui.navigation.CustomSettingsKey
import oolong.client.ui.navigation.HabitSettingsKey
import oolong.client.ui.navigation.LoginKey
import oolong.client.ui.navigation.MoreSettingsKey
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.navigation.OKSignSettingsKey
import oolong.client.ui.navigation.PostSettingsKey
import oolong.client.ui.navigation.PrivacySettingsKey
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.page.ProvideNavigator
import oolong.client.ui.widgets.compose.Avatar
import oolong.client.ui.widgets.compose.AvatarIcon
import oolong.client.ui.widgets.compose.BackNavigationIcon
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.GlassTopBarTitle
import oolong.client.ui.widgets.compose.Sizes
import oolong.client.utils.StringUtil

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
                    icon = Symbols.AccountCircle,
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
    val context = LocalContext.current
    val signedIn = LocalAccount.current != null
    ProvideNavigator(navigator = navigator) {
        GlassScaffold(
            largeTitle = true,
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
                            leadingIcon = { LeadingIcon { Icon(imageVector = Symbols.Palette, contentDescription = null) } },
                            onClick = { navigator.navigate(CustomSettingsKey) }
                        )
                    }
                    prefsItem {
                        TextPref(
                            title = stringResource(id = R.string.title_settings_read_habit),
                            leadingIcon = { LeadingIcon { Icon(imageVector = Symbols.AutoStories, contentDescription = null) } },
                            onClick = { navigator.navigate(HabitSettingsKey) }
                        )
                    }
                    prefsItem {
                        TextPref(
                            title = stringResource(id = R.string.title_settings_post),
                            leadingIcon = { LeadingIcon { Icon(imageVector = Symbols.EditNote, contentDescription = null) } },
                            onClick = { navigator.navigate(PostSettingsKey) }
                        )
                    }
                }
                prefsGroup(header = { Spacer(modifier = Modifier.height(8.dp)) }) {
                    prefsItem {
                        TextPref(
                            title = stringResource(id = R.string.title_block_settings),
                            leadingIcon = { LeadingIcon { Icon(imageVector = Symbols.Block, contentDescription = null) } },
                            onClick = { navigator.navigate(BlockSettingsKey) }
                        )
                    }
                    // What the account keeps on the server, so only while signed in
                    if (signedIn) {
                        prefsItem {
                            TextPref(
                                title = stringResource(id = R.string.title_privacy_settings),
                                leadingIcon = { LeadingIcon { Icon(imageVector = Symbols.Shield, contentDescription = null) } },
                                onClick = { navigator.navigate(PrivacySettingsKey) }
                            )
                        }
                    }
                    prefsItem {
                        TextPref(
                            title = stringResource(id = R.string.title_oksign),
                            leadingIcon = { LeadingIcon { Icon(imageVector = Symbols.TaskAlt, contentDescription = null) } },
                            onClick = { navigator.navigate(OKSignSettingsKey) }
                        )
                    }
                }
                prefsGroup(header = { Spacer(modifier = Modifier.height(8.dp)) }) {
                    prefsItem {
                        TextPref(
                            title = stringResource(id = R.string.title_settings_more),
                            leadingIcon = { LeadingIcon { Icon(imageVector = Symbols.MoreHoriz, contentDescription = null) } },
                            onClick = { navigator.navigate(MoreSettingsKey) }
                        )
                    }
                }
                if (BuildConfig.DEBUG) {
                    prefsGroup(header = { Spacer(modifier = Modifier.height(8.dp)) }) {
                        prefsItem {
                            TextPref(
                                title = "控件目录",
                                leadingIcon = { LeadingIcon { Icon(imageVector = Symbols.Category, contentDescription = null) } },
                                onClick = { navigator.navigate(ControlCatalogKey) }
                            )
                        }
                        prefsItem {
                            TextPref(
                                title = "点选打码实验",
                                leadingIcon = { LeadingIcon { Icon(imageVector = Symbols.Category, contentDescription = null) } },
                                // Built only into the debug build, so found by name
                                onClick = { context.startActivity(Intent().setClassName(context, "oolong.client.debug.automask.AutoMaskLabActivity")) }
                            )
                        }
                    }
                }
            }
        }
    }
}
