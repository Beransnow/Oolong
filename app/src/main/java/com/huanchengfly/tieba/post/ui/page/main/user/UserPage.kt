package com.huanchengfly.tieba.post.ui.page.main.user

import android.graphics.Typeface
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.models.database.Account
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.pullRefreshIndicator
import com.huanchengfly.tieba.post.ui.navigation.AboutKey
import com.huanchengfly.tieba.post.ui.navigation.AppThemeKey
import com.huanchengfly.tieba.post.ui.navigation.HistoryKey
import com.huanchengfly.tieba.post.ui.navigation.SettingsKey
import com.huanchengfly.tieba.post.ui.navigation.ThreadStoreKey
import com.huanchengfly.tieba.post.ui.navigation.UserProfileKey
import com.huanchengfly.tieba.post.ui.navigation.WebViewKey
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.widgets.compose.Avatar
import com.huanchengfly.tieba.post.ui.widgets.compose.ConfirmDialog
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassStatusBar
import com.huanchengfly.tieba.post.ui.widgets.compose.ListMenuItem
import com.huanchengfly.tieba.post.ui.widgets.compose.Sizes
import com.huanchengfly.tieba.post.ui.widgets.compose.Switch
import com.huanchengfly.tieba.post.ui.widgets.compose.placeholder.placeholder
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberDialogState
import com.huanchengfly.tieba.post.utils.AccountUtil.LocalAccount
import com.huanchengfly.tieba.post.utils.CuidUtils
import com.huanchengfly.tieba.post.utils.StringUtil
import com.huanchengfly.tieba.post.utils.ThemeUtil
import com.huanchengfly.tieba.post.utils.appPreferences

@Composable
private fun StatCardPlaceholder(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.placeholder(visible = true, color = ExtendedTheme.colors.chip, shape = RectangleShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatCardItem(
            statNum = 0,
            statText = stringResource(id = R.string.text_stat_follow)
        )
        StatCardItem(
            statNum = 0,
            statText = stringResource(id = R.string.text_stat_fans)
        )
        StatCardItem(
            statNum = 0,
            statText = stringResource(id = R.string.title_stat_posts_num)
        )
    }
}

@Composable
private fun StatCard(
    account: Account,
    modifier: Modifier = Modifier
) {
    val postNum by animateIntAsState(targetValue = account.postNum?.toIntOrNull() ?: 0)
    val fansNum by animateIntAsState(targetValue = account.fansNum?.toIntOrNull() ?: 0)
    val concernNum by animateIntAsState(targetValue = account.concernNum?.toIntOrNull() ?: 0)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatCardItem(
            statNum = concernNum,
            statText = stringResource(id = R.string.text_stat_follow)
        )
        StatCardItem(
            statNum = fansNum,
            statText = stringResource(id = R.string.text_stat_fans)
        )
        StatCardItem(
            statNum = postNum,
            statText = stringResource(id = R.string.title_stat_posts_num)
        )
    }
}

@Composable
private fun InfoCard(
    modifier: Modifier = Modifier,
    userName: String = "",
    userIntro: String = "",
    avatar: String? = null,
    isPlaceholder: Boolean = false
) {
    Row(
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.Bottom)
        ) {
            Text(
                text = userName,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                color = ExtendedTheme.colors.text,
                modifier = Modifier
                    .fillMaxWidth()
                    .placeholder(visible = isPlaceholder, color = ExtendedTheme.colors.chip, shape = RectangleShape),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = userIntro,
                fontSize = 12.sp,
                color = ExtendedTheme.colors.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .placeholder(visible = isPlaceholder, color = ExtendedTheme.colors.chip, shape = RectangleShape),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        if (avatar != null) {
            Avatar(
                data = avatar,
                size = Sizes.Large,
                contentDescription = stringResource(id = R.string.desc_user_avatar),
                modifier = Modifier
                    .align(Alignment.Bottom)
                    .placeholder(visible = isPlaceholder, color = ExtendedTheme.colors.chip, shape = RectangleShape),
            )
        }
    }
}

/**
 * A figure over its label, both from the left, as in a forum's header: the first figure lines up
 * with the name above and the menu icons below.
 */
@Composable
private fun StatCardItem(
    statNum: Int,
    statText: String
) {
    Column(
        modifier = Modifier.padding(end = 32.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "$statNum",
            fontSize = 28.sp,
            lineHeight = 28.sp,
            fontFamily = FontFamily(Typeface.createFromAsset(LocalContext.current.assets, "bebas.ttf")),
            color = ExtendedTheme.colors.text,
            style = LocalTextStyle.current.copy(
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = statText,
            fontSize = 12.sp,
            color = ExtendedTheme.colors.textSecondary
        )
    }
}

@Composable
private fun LoginTipCard(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.Bottom)
        ) {
            Text(
                text = stringResource(id = R.string.tip_login),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = ExtendedTheme.colors.text,
                modifier = Modifier
                    .fillMaxWidth(),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Icon(
            imageVector = Icons.Rounded.AccountCircle,
            contentDescription = null,
            tint = ExtendedTheme.colors.onChip,
            modifier = Modifier
                .clip(CircleShape)
                .size(Sizes.Large)
                .background(color = ExtendedTheme.colors.chip)
                .padding(16.dp),
        )
    }
}

@Composable
fun UserPage(
    viewModel: UserViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val account = LocalAccount.current
    LaunchedEffect(account?.uid) { viewModel.refresh() }
    UserScreen(account = account, isLoading = state.isLoading, onRefresh = viewModel::refresh)
}

/** The "me" tab: who is logged in with their figures, then the menus, or a prompt to log in. */
private val StatsPadding = Modifier
    .fillMaxWidth()
    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun UserScreen(
    account: Account?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current

    val switchToNightDialogState = rememberDialogState()
    ConfirmDialog(
        dialogState = switchToNightDialogState,
        onConfirm = {},
        onCancel = {
            context.appPreferences.followSystemNight = false
            ThemeUtil.switchNightMode()
        },
        confirmText = stringResource(id = R.string.btn_keep_following),
        cancelText = stringResource(id = R.string.btn_close_following)
    ) {
        Text(text = stringResource(id = R.string.message_dialog_follow_system_night))
    }

    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = { GlassStatusBar() },
        modifier = Modifier.fillMaxSize()
    ) { contentPaddings ->
        val pullRefreshState = rememberPullRefreshState(
            refreshing = isLoading,
            onRefresh = onRefresh)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(pullRefreshState),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(state = rememberScrollState())
                    .fillMaxSize()
                    .padding(contentPaddings)
            ) {
                if (account != null) {
                    InfoCard(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable {
                                navigator.navigate(UserProfileKey(account.uid.toLong()))
                            }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        userName = account.nameShow ?: account.name,
                        userIntro = account.intro ?: stringResource(id = R.string.tip_no_intro),
                        avatar = StringUtil.getAvatarUrl(account.portrait),
                    )
                    StatCard(account = account, modifier = StatsPadding)
                } else if (isLoading) {
                    InfoCard(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                            .padding(top = 8.dp),
                        isPlaceholder = true,
                    )
                    StatCardPlaceholder(modifier = StatsPadding)
                } else {
                    LoginTipCard(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                            .padding(top = 8.dp),
                    )
                }
                if (account != null) {
                    ListMenuItem(
                        iconColor = ExtendedTheme.colors.text,
                        icon = ImageVector.vectorResource(id = R.drawable.ic_favorite),
                        text = stringResource(id = R.string.title_my_collect),
                        onClick = {
                            navigator.navigate(ThreadStoreKey)
                        }
                    )
                }
                ListMenuItem(
                    iconColor = ExtendedTheme.colors.text,
                    icon = ImageVector.vectorResource(id = R.drawable.ic_outline_watch_later_24),
                    text = stringResource(id = R.string.title_history),
                    onClick = {
                        navigator.navigate(HistoryKey)
                    }
                )
                ListMenuItem(
                    iconColor = ExtendedTheme.colors.text,
                    icon = ImageVector.vectorResource(id = R.drawable.ic_brush_24),
                    text = stringResource(id = R.string.title_theme),
                    onClick = {
                        navigator.navigate(AppThemeKey)
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.my_info_night),
                        color = ExtendedTheme.colors.textSecondary,
                        fontSize = 12.sp,
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = ThemeUtil.isNightMode(ThemeUtil.themeState.value),
                        onCheckedChange = {
                            if (context.appPreferences.followSystemNight) {
                                switchToNightDialogState.show()
                            } else {
                                ThemeUtil.switchNightMode()
                            }
                        }
                    )
                }
                if (account != null) {
                    ListMenuItem(
                        iconColor = ExtendedTheme.colors.text,
                        icon = ImageVector.vectorResource(id = R.drawable.ic_help_outline_black_24),
                        text = stringResource(id = R.string.my_info_service_center),
                        onClick = {
                            navigator.navigate(
                                WebViewKey(
                                    initialUrl = "https://tieba.baidu.com/mo/q/hybrid-main-service/uegServiceCenter?cuid=${CuidUtils.getNewCuid()}&cuid_galaxy2=${CuidUtils.getNewCuid()}&cuid_gid=&timestamp=${System.currentTimeMillis()}&_client_version=12.52.1.0&nohead=1"
                                )
                            )
                        },
                    )
                }
                // The two groups of menus are set apart by space alone
                Spacer(modifier = Modifier.height(16.dp))
                ListMenuItem(
                    iconColor = ExtendedTheme.colors.text,
                    icon = ImageVector.vectorResource(id = R.drawable.ic_settings_24),
                    text = stringResource(id = R.string.my_info_settings),
                    onClick = { navigator.navigate(SettingsKey) },
                )
                ListMenuItem(
                    iconColor = ExtendedTheme.colors.text,
                    icon = ImageVector.vectorResource(id = R.drawable.ic_info_black_24),
                    text = stringResource(id = R.string.my_info_about),
                    onClick = { navigator.navigate(AboutKey) },
                )
            }

            PullRefreshIndicator(
                refreshing = isLoading,
                state = pullRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = contentPaddings.calculateTopPadding()),
                backgroundColor = ExtendedTheme.colors.pullRefreshIndicator,
                contentColor = ExtendedTheme.colors.primary,
            )
        }
    }
}
