package oolong.client.ui.page.settings

import android.content.Intent
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import oolong.client.BuildConfig
import oolong.client.R
import oolong.client.models.database.Account
import oolong.client.ui.common.LocalAccount
import oolong.client.ui.common.prefs.widgets.TextPref
import oolong.client.ui.common.settings.Availability
import oolong.client.ui.common.settings.SettingsHeader
import oolong.client.ui.common.settings.SettingsScreen
import oolong.client.ui.common.settings.settingsPage
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
import oolong.client.ui.navigation.SettingsKey
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

/** The settings hub: the account, then each settings page. */
@OptIn(ExperimentalMaterialApi::class)
val SettingsHub = settingsPage(R.string.title_settings, SettingsKey) {
    group(SettingsHeader.Gap(8.dp)) {
        custom(null) { modifier -> NowAccountItem(account = LocalAccount.current, modifier = modifier) }
    }
    group(SettingsHeader.Gap(8.dp)) {
        link(R.string.title_settings_custom, to = CustomSettingsKey, icon = Symbols.Palette)
        link(R.string.title_settings_read_habit, to = HabitSettingsKey, icon = Symbols.AutoStories)
        link(R.string.title_settings_post, to = PostSettingsKey, icon = Symbols.EditNote)
    }
    group(SettingsHeader.Gap(8.dp)) {
        link(R.string.title_block_settings, to = BlockSettingsKey, icon = Symbols.Block)
        // What the account keeps on the server, so only while signed in
        link(R.string.title_privacy_settings, to = PrivacySettingsKey, icon = Symbols.Shield, available = Availability.SignedIn)
        link(R.string.title_oksign, to = OKSignSettingsKey, icon = Symbols.TaskAlt)
    }
    group(SettingsHeader.Gap(8.dp)) {
        link(R.string.title_settings_more, to = MoreSettingsKey, icon = Symbols.MoreHoriz)
    }
    group(SettingsHeader.Gap(8.dp), available = { BuildConfig.DEBUG }) {
        custom(null) { modifier ->
            val navigator = LocalNavigator.current
            TextPref(
                title = "控件目录",
                modifier = modifier,
                leadingIcon = { LeadingIcon { Icon(imageVector = Symbols.Category, contentDescription = null) } },
                onClick = { navigator.navigate(ControlCatalogKey) },
            )
        }
        custom(null) { modifier ->
            val context = LocalContext.current
            TextPref(
                title = "点选打码实验",
                modifier = modifier,
                leadingIcon = { LeadingIcon { Icon(imageVector = Symbols.Category, contentDescription = null) } },
                // Built only into the debug build, so found by name
                onClick = { context.startActivity(Intent().setClassName(context, "oolong.client.debug.automask.AutoMaskLabActivity")) },
            )
        }
    }
}

@Composable
fun SettingsPage(navigator: Navigator = LocalNavigator.current) {
    ProvideNavigator(navigator) { SettingsScreen(SettingsHub) }
}
