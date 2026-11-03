package oolong.client.ui.page.settings.account

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import oolong.client.R
import oolong.client.dataStore
import oolong.client.toastShort
import oolong.client.ui.common.LocalAccount
import oolong.client.ui.common.LocalAccountActions
import oolong.client.ui.common.LocalAllAccounts
import oolong.client.ui.common.prefs.PrefsExternal
import oolong.client.ui.common.prefs.PrefsScreen
import oolong.client.ui.common.prefs.widgets.EditTextPref
import oolong.client.ui.common.prefs.widgets.TextPref
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.navigation.LoginKey
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.widgets.compose.Avatar
import oolong.client.ui.widgets.compose.AvatarIcon
import oolong.client.ui.widgets.compose.BackNavigationIcon
import oolong.client.ui.widgets.compose.ConfirmDialog
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.GlassTopBarTitle
import oolong.client.ui.widgets.compose.Sizes
import oolong.client.ui.widgets.compose.rememberDialogState
import oolong.client.utils.StringUtil
import oolong.client.utils.TiebaUtil
import oolong.client.utils.appPreferences
import oolong.client.utils.launchUrl

@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeUiApi::class)
@Composable
fun AccountManagePage(
    navigator: Navigator = LocalNavigator.current,
) {
    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = R.string.title_account_manage)) },
                navigationIcon = {
                    BackNavigationIcon(onBackPressed = { navigator.navigateUp() })
                }
            )
        },
    ) { paddingValues ->
        val account = LocalAccount.current
        val accounts = LocalAllAccounts.current
        val actions = LocalAccountActions.current
        val resources = LocalResources.current
        val context = LocalContext.current
        val accountsGroup = stringResource(id = R.string.settings_group_accounts)
        // Copying the BDUSS and logging out both ask first: one gives the account away, the
        // other cannot be undone here
        val copyBdussDialog = rememberDialogState()
        ConfirmDialog(
            dialogState = copyBdussDialog,
            onConfirm = { TiebaUtil.copyText(context, account?.bduss, isSensitive = true) },
            confirmText = stringResource(id = R.string.title_copy_bduss),
        ) {
            Text(text = stringResource(id = R.string.summary_copy_bduss))
        }
        val exitDialog = rememberDialogState()
        ConfirmDialog(
            dialogState = exitDialog,
            onConfirm = {
                val next = actions.logOut()
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
            Text(text = stringResource(id = R.string.message_exit_account, account?.nameShow ?: account?.name.orEmpty()))
        }
        PrefsScreen(
            dataStore = LocalContext.current.dataStore,
            dividerThickness = 0.dp,
            modifier = Modifier.fillMaxSize(),
            contentPadding = paddingValues,
        ) {
            // Every account, the current one ticked: tapping another switches to it
            prefsGroup(accountsGroup) {
                accounts.forEach { item ->
                    prefsItem {
                        val current = item.id == account?.id
                        TextPref(
                            title = item.nameShow ?: item.name,
                            leadingIcon = {
                                Avatar(
                                    data = StringUtil.getAvatarUrl(item.portrait),
                                    size = Sizes.Small,
                                    contentDescription = null,
                                )
                            },
                            onClick = { if (!current) actions.switchTo(item.id) },
                            trailingContent = if (current) {
                                {
                                    Icon(
                                        imageVector = Symbols.Check,
                                        contentDescription = null,
                                        tint = ExtendedTheme.colors.text,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            } else null,
                        )
                    }
                }
                prefsItem {
                    TextPref(
                        title = stringResource(id = R.string.title_new_account),
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
            }
            prefsFooter { stringResource(id = R.string.tip_account_error) }
            prefsGroup(header = { Spacer(modifier = Modifier.height(8.dp)) }) {
                prefsItem {
                    TextPref(
                        title = stringResource(id = R.string.title_modify_username),
                        onClick = {
                            launchUrl(
                                context,
                                navigator,
                                "https://wappass.baidu.com/static/manage-chunk/change-username.html#/showUsername"
                            )
                        },
                        enabled = account != null,
                        trailingContent = { PrefsExternal() },
                    )
                }
                prefsItem {
                    TextPref(
                        title = stringResource(id = R.string.title_copy_bduss),
                        onClick = { copyBdussDialog.show() },
                        trailingContent = null,
                        enabled = account != null
                    )
                }
                prefsItem {
                    val littleTail = remember { context.appPreferences.littleTail }
                    EditTextPref(
                        key = "little_tail",
                        title = stringResource(id = R.string.title_my_tail),
                        summary = if (littleTail.isNullOrEmpty())
                            stringResource(id = R.string.tip_no_little_tail)
                        else
                            littleTail,
                        enabled = true,
                        dialogTitle = stringResource(id = R.string.title_dialog_modify_little_tail),
                    )
                }
            }
            prefsGroup(header = { Spacer(modifier = Modifier.height(8.dp)) }) {
                prefsItem {
                    TextPref(
                        title = stringResource(id = R.string.title_exit_account),
                        // Set apart in red: it is the one action here that cannot be undone from the page
                        textColor = MaterialTheme.colors.error,
                        trailingContent = null,
                        onClick = { exitDialog.show() },
                        enabled = account != null,
                    )
                }
            }
        }
    }
}
