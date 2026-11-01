package com.huanchengfly.tieba.post.ui.page.settings.account

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.dataStore
import com.huanchengfly.tieba.post.ui.common.prefs.PrefsExternal
import com.huanchengfly.tieba.post.ui.common.prefs.PrefsScreen
import com.huanchengfly.tieba.post.ui.common.prefs.widgets.EditTextPref
import com.huanchengfly.tieba.post.ui.common.prefs.widgets.TextPref
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.navigation.LoginKey
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.widgets.compose.Avatar
import com.huanchengfly.tieba.post.ui.widgets.compose.AvatarIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.ConfirmDialog
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBar
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBarTitle
import com.huanchengfly.tieba.post.ui.widgets.compose.Sizes
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberDialogState
import com.huanchengfly.tieba.post.utils.AccountUtil
import com.huanchengfly.tieba.post.utils.AccountUtil.AllAccounts
import com.huanchengfly.tieba.post.utils.AccountUtil.LocalAccount
import com.huanchengfly.tieba.post.utils.StringUtil
import com.huanchengfly.tieba.post.utils.TiebaUtil
import com.huanchengfly.tieba.post.utils.appPreferences
import com.huanchengfly.tieba.post.utils.launchUrl
import com.ramcosta.composedestinations.annotation.Destination

@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeUiApi::class)
@Destination
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
        val accounts = AllAccounts.current
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
            onConfirm = { AccountUtil.exit(context) },
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
                            onClick = { if (!current) AccountUtil.switchAccount(context, item.id) },
                            trailingContent = if (current) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
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
                                icon = Icons.Rounded.Add,
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