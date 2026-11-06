package oolong.client.ui.page.settings.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringPreferencesKey
import oolong.client.R
import oolong.client.models.database.Account
import oolong.client.repository.AccountActions
import oolong.client.toastShort
import oolong.client.ui.common.LocalAccount
import oolong.client.ui.common.LocalAccountActions
import oolong.client.ui.common.LocalAllAccounts
import oolong.client.ui.common.prefs.widgets.TextPref
import oolong.client.ui.common.settings.SettingsHeader
import oolong.client.ui.common.settings.SettingsPage
import oolong.client.ui.common.settings.SettingsScope
import oolong.client.ui.common.settings.SettingsScreen
import oolong.client.ui.common.settings.stored
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.navigation.AccountManageKey
import oolong.client.ui.navigation.LoginKey
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.page.ProvideNavigator
import oolong.client.ui.widgets.compose.Avatar
import oolong.client.ui.widgets.compose.AvatarIcon
import oolong.client.ui.widgets.compose.ConfirmDialog
import oolong.client.ui.widgets.compose.DialogState
import oolong.client.ui.widgets.compose.Sizes
import oolong.client.ui.widgets.compose.rememberDialogState
import oolong.client.utils.StringUtil
import oolong.client.utils.TiebaUtil
import oolong.client.utils.launchUrl

/** The accounts page's accounts, and the dialogs its actions ask in first. */
class AccountState(
    val account: Account?,
    val accounts: List<Account>,
    val actions: AccountActions,
    /** Copying the BDUSS asks first: it gives the account away. */
    val copyBdussDialog: DialogState,
    /** Logging out asks first: it can't be undone here. */
    val exitDialog: DialogState,
)

/** The accounts on this device, switching between them, and the current one's own settings. */
@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeUiApi::class)
object AccountSettings : SettingsPage<AccountState>(R.string.title_account_manage, AccountManageKey) {
    @Composable
    override fun rememberState() = AccountState(
        account = LocalAccount.current,
        accounts = LocalAllAccounts.current,
        actions = LocalAccountActions.current,
        copyBdussDialog = rememberDialogState(),
        exitDialog = rememberDialogState(),
    )

    @Composable
    override fun Effects(state: AccountState) {
        val context = LocalContext.current
        val resources = LocalResources.current
        ConfirmDialog(
            dialogState = state.copyBdussDialog,
            onConfirm = { TiebaUtil.copyText(context, state.account?.bduss, isSensitive = true) },
            confirmText = stringResource(id = R.string.title_copy_bduss),
        ) {
            Text(text = stringResource(id = R.string.summary_copy_bduss))
        }
        ConfirmDialog(
            dialogState = state.exitDialog,
            onConfirm = {
                val next = state.actions.logOut()
                context.toastShort(
                    if (next != null) {
                        resources.getString(R.string.toast_exit_account_switched, next.nameShow ?: next.name)
                    } else {
                        resources.getString(R.string.toast_exit_account_success)
                    }
                )
            },
            confirmText = stringResource(id = R.string.title_exit_account),
        ) {
            Text(text = stringResource(id = R.string.message_exit_account, state.account?.nameShow ?: state.account?.name.orEmpty()))
        }
    }

    override fun SettingsScope<AccountState>.content() {
        group(R.string.settings_group_accounts) {
            // Every account, the current one ticked: tapping another switches to it
            custom(null, shown = { accounts.isNotEmpty() }) { AccountRows(account, accounts, actions) }
            custom(R.string.title_new_account) { modifier ->
                val navigator = LocalNavigator.current
                TextPref(
                    title = stringResource(id = R.string.title_new_account),
                    modifier = modifier,
                    leadingIcon = {
                        AvatarIcon(
                            icon = Symbols.Add,
                            size = Sizes.Small,
                            contentDescription = null,
                            color = ExtendedTheme.colors.text,
                            backgroundColor = ExtendedTheme.colors.background,
                        )
                    },
                    onClick = { navigator.navigate(LoginKey) },
                    trailingContent = null,
                )
            }
            footer { stringResource(id = R.string.tip_account_error) }
        }
        group(SettingsHeader.Gap(8.dp)) {
            action(
                R.string.title_modify_username,
                external = true,
                enabled = { account != null },
                onClick = { launchUrl(context, navigator, "https://wappass.baidu.com/static/manage-chunk/change-username.html#/showUsername") },
            )
            action(R.string.title_copy_bduss, enabled = { account != null }, onClick = { state.copyBdussDialog.show() })
            text(
                "little_tail", R.string.title_my_tail,
                summary = { stored(stringPreferencesKey("little_tail"), "").ifEmpty { stringResource(id = R.string.tip_no_little_tail) } },
                dialogTitle = R.string.title_dialog_modify_little_tail,
            )
        }
        group(SettingsHeader.Gap(8.dp)) {
            // Set apart in red: it is the one action here that cannot be undone from the page
            action(R.string.title_exit_account, destructive = true, enabled = { account != null }, onClick = { state.exitDialog.show() })
        }
    }
}

@Composable
fun AccountManagePage(navigator: Navigator = LocalNavigator.current) {
    ProvideNavigator(navigator) { SettingsScreen(AccountSettings) }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun AccountRows(account: Account?, accounts: List<Account>, actions: AccountActions) {
    Column {
        accounts.forEach { item ->
            val current = item.id == account?.id
            TextPref(
                title = item.nameShow ?: item.name,
                leadingIcon = {
                    Avatar(data = StringUtil.getAvatarUrl(item.portrait), size = Sizes.Small, contentDescription = null)
                },
                onClick = { if (!current) actions.switchTo(item.id) },
                trailingContent = if (current) {
                    { Icon(imageVector = Symbols.Check, contentDescription = null, tint = ExtendedTheme.colors.text, modifier = Modifier.size(20.dp)) }
                } else {
                    null
                },
            )
        }
    }
}
