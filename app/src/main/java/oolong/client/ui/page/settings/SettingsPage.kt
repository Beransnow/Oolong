package oolong.client.ui.page.settings

import android.content.Intent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.ContentAlpha
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import oolong.client.BuildConfig
import oolong.client.R
import oolong.client.models.database.Account
import oolong.client.ui.common.LocalAccount
import oolong.client.ui.common.prefs.widgets.TextPref
import oolong.client.ui.common.settings.Availability
import oolong.client.ui.common.settings.SettingsContent
import oolong.client.ui.common.settings.SettingsHeader
import oolong.client.ui.common.settings.SettingsHighlight
import oolong.client.ui.common.settings.SettingsPage
import oolong.client.ui.common.settings.rememberSettingsEnvironment
import oolong.client.ui.common.settings.search
import oolong.client.ui.common.settings.settingsIndex
import oolong.client.ui.common.settings.settingsPage
import oolong.client.ui.common.theme.compose.Corners
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
import oolong.client.ui.page.settings.account.AccountSettings
import oolong.client.ui.page.settings.block.BlockSettings
import oolong.client.ui.page.settings.custom.CustomSettings
import oolong.client.ui.page.settings.habit.HabitSettings
import oolong.client.ui.page.settings.more.MoreSettings
import oolong.client.ui.page.settings.oksign.OKSignSettings
import oolong.client.ui.page.settings.post.PostSettings
import oolong.client.ui.page.settings.privacy.PrivacySettings
import oolong.client.ui.page.settings.theme.ThemeSettingsPage
import oolong.client.ui.widgets.compose.Avatar
import oolong.client.ui.widgets.compose.AvatarIcon
import oolong.client.ui.widgets.compose.BackNavigationIcon
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.GlassTopBarTitle
import oolong.client.ui.widgets.compose.MyBackHandler
import oolong.client.ui.widgets.compose.SearchBox
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
        // Named by the account it shows; search finds the page it opens by that page's name
        custom(R.string.title_account_manage, opens = AccountManageKey, available = Availability.SignedIn) { modifier ->
            NowAccountItem(account = LocalAccount.current, modifier = modifier)
        }
        custom(null, available = { !it.signedIn }) { modifier -> NowAccountItem(account = LocalAccount.current, modifier = modifier) }
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

/** Every settings page search goes through, from the hub down. */
val AllSettingsPages: List<SettingsPage<*>> by lazy {
    listOf(
        SettingsHub,
        CustomSettings,
        ThemeSettingsPage,
        HabitSettings,
        PostSettings,
        BlockSettings,
        PrivacySettings,
        OKSignSettings,
        MoreSettings,
        AccountSettings,
    )
}

/** The hub, with a field above its pages that searches every setting in them. */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SettingsPage(navigator: Navigator = LocalNavigator.current) {
    ProvideNavigator(navigator) {
        var query by rememberSaveable { mutableStateOf("") }
        val environment = rememberSettingsEnvironment()
        val resources = LocalResources.current
        val index = remember(environment.signedIn, resources) { settingsIndex(SettingsHub, AllSettingsPages, environment, resources) }
        val found = remember(index, query) { index.search(query) }
        val nothing = stringResource(id = R.string.settings_search_nothing)
        MyBackHandler(enabled = query.isNotEmpty()) { query = "" }
        GlassScaffold(
            largeTitle = true,
            backgroundColor = Color.Transparent,
            topBar = {
                GlassTopBar(
                    title = { GlassTopBarTitle(text = stringResource(id = R.string.title_settings)) },
                    navigationIcon = { BackNavigationIcon(onBackPressed = { navigator.navigateUp() }) },
                )
            },
        ) { padding ->
            SettingsContent(
                page = SettingsHub,
                contentPadding = padding,
                header = { SettingsSearchField(query, onQueryChange = { query = it }) },
                replacement = if (query.isBlank()) {
                    null
                } else {
                    {
                        prefsGroup(header = { Spacer(modifier = Modifier.height(8.dp)) }) {
                            found.forEach { entry ->
                                prefsItem {
                                    TextPref(
                                        title = entry.title,
                                        summary = entry.path.joinToString(" › ").ifEmpty { null },
                                        onClick = {
                                            SettingsHighlight.target = entry.row?.let { entry.page to it }
                                            navigator.navigate(entry.page)
                                        },
                                    )
                                }
                            }
                        }
                        if (found.isEmpty()) prefsFooter { nothing }
                    }
                },
            )
        }
    }
}

/** The field the hub searches settings with. */
@Composable
private fun SettingsSearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    SearchBox(
        keyword = query,
        onKeywordChange = onQueryChange,
        onKeywordSubmit = { focusManager.clearFocus() },
        placeholder = {
            Text(text = stringResource(id = R.string.settings_search_hint), color = ExtendedTheme.colors.onSearchField.copy(alpha = ContentAlpha.medium))
        },
        prependIcon = { Icon(imageVector = Symbols.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
        shape = Corners.full,
        submitButton = false,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(40.dp),
    )
}
