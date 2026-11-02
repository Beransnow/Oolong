package com.huanchengfly.tieba.post.ui.page.main.notifications.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.arch.PagerState
import com.huanchengfly.tieba.post.model.Notification
import com.huanchengfly.tieba.post.model.NotificationType
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.navigation.SubPostsKey
import com.huanchengfly.tieba.post.ui.navigation.ThreadKey
import com.huanchengfly.tieba.post.ui.navigation.UserProfileKey
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.widgets.compose.AuthorName
import com.huanchengfly.tieba.post.ui.widgets.compose.Avatar
import com.huanchengfly.tieba.post.ui.widgets.compose.BlockTip
import com.huanchengfly.tieba.post.ui.widgets.compose.BlockableContent
import com.huanchengfly.tieba.post.ui.widgets.compose.Container
import com.huanchengfly.tieba.post.ui.widgets.compose.EmoticonText
import com.huanchengfly.tieba.post.ui.widgets.compose.LoadMoreLayout
import com.huanchengfly.tieba.post.ui.widgets.compose.MetaText
import com.huanchengfly.tieba.post.ui.widgets.compose.MyLazyColumn
import com.huanchengfly.tieba.post.ui.widgets.compose.Sizes
import com.huanchengfly.tieba.post.ui.widgets.compose.UserHeader
import com.huanchengfly.tieba.post.ui.widgets.compose.VerticalDivider
import com.huanchengfly.tieba.post.ui.widgets.compose.plus
import com.huanchengfly.tieba.post.utils.DateTimeUtils
import com.huanchengfly.tieba.post.utils.StringUtil

@Composable
fun NotificationsListPage(
    type: NotificationType,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val listType = type
    LaunchedEffect(listType) { viewModel.open(listType) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    NotificationList(
        type = listType,
        list = state.list(listType),
        contentPadding = contentPadding,
        onRefresh = { viewModel.refresh(listType) },
        onLoadMore = { viewModel.loadMore(listType) },
        onOpen = { notification ->
            if (notification.inPost) {
                navigator.navigate(
                    SubPostsKey(threadId = notification.threadId, subPostId = notification.postId, loadFromSubPost = true)
                )
            } else {
                navigator.navigate(ThreadKey(threadId = notification.threadId, postId = notification.postId))
            }
        },
        onOpenQuote = { notification ->
            val quotePostId = notification.quotePostId
            if (notification.inPost && quotePostId != null) {
                // The quoted id may be a post or a reply inside one, so let pb/floor resolve it as a reply
                navigator.navigate(
                    SubPostsKey(threadId = notification.threadId, subPostId = quotePostId, loadFromSubPost = true)
                )
            } else {
                navigator.navigate(ThreadKey(threadId = notification.threadId))
            }
        },
        onOpenUser = { navigator.navigate(UserProfileKey(it)) },
    )
}

/** One list of notifications, which pulls down to refresh and loads more at its end. */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun NotificationList(
    type: NotificationType,
    list: PagerState<NotificationItem>,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (Notification) -> Unit,
    onOpenQuote: (Notification) -> Unit,
    onOpenUser: (Long) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val refreshing = list.refresh == LoadStatus.Loading
    val pullRefreshState = rememberPullRefreshState(refreshing = refreshing, onRefresh = onRefresh)
    val lazyListState = rememberLazyListState()
    Box(modifier = modifier.pullRefresh(pullRefreshState)) {
        LoadMoreLayout(
            isLoading = list.append == LoadStatus.Loading,
            onLoadMore = onLoadMore,
            loadEnd = !list.hasMore,
            lazyListState = lazyListState,
        ) {
            MyLazyColumn(
                contentPadding = contentPadding.plus(PaddingValues(vertical = 4.dp)),
                state = lazyListState,
            ) {
                itemsIndexed(
                    items = list.items,
                    key = { _, (it) -> "${it.postId}_${it.author?.id}_${it.time}" },
                ) { index, (notification, blocked) ->
                    Container {
                        Column {
                            BlockableContent(
                                block = blocked,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                NotificationItemContent(type, notification, onOpen, onOpenQuote, onOpenUser)
                            }
                            // Divided like feed cards
                            if (index < list.items.lastIndex) {
                                VerticalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
        }

        PullRefreshIndicator(
            refreshing = refreshing,
            state = pullRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = contentPadding.calculateTopPadding()),
            backgroundColor = ExtendedTheme.colors.refreshIndicator,
            contentColor = ExtendedTheme.colors.emphasis,
        )
    }
}

@Composable
private fun NotificationItemContent(
    type: NotificationType,
    notification: Notification,
    onOpen: (Notification) -> Unit,
    onOpenQuote: (Notification) -> Unit,
    onOpenUser: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable { onOpen(notification) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val author = notification.author
        if (author != null) {
            UserHeader(
                avatar = {
                    Avatar(data = StringUtil.getAvatarUrl(author.portrait), size = Sizes.Small, contentDescription = null)
                },
                name = { AuthorName(text = AnnotatedString(author.displayName)) },
                onClick = { onOpenUser(author.id) },
                desc = { MetaText(text = DateTimeUtils.getRelativeTimeString(LocalContext.current, notification.time)) },
            ) {}
        }
        EmoticonText(
            text = notification.content,
            fontSize = 15.sp,
            lineHeight = 24.sp,
            color = ExtendedTheme.colors.text,
        )
        val quoteText = when {
            type == NotificationType.AtMe -> notification.threadTitle
            notification.inPost -> notification.quoteContent
            else -> stringResource(id = R.string.text_message_list_item_reply_my_thread, notification.threadTitle)
        }
        if (quoteText != null) {
            // What it answers, in the grey block replies quote in
            EmoticonText(
                text = quoteText,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenQuote(notification) }
                    .background(ExtendedTheme.colors.chip, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                color = ExtendedTheme.colors.textSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
