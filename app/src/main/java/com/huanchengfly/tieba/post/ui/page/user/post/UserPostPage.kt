package com.huanchengfly.tieba.post.ui.page.user.post

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.arch.PagerState
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.navigation.ForumKey
import com.huanchengfly.tieba.post.ui.navigation.SubPostsKey
import com.huanchengfly.tieba.post.ui.navigation.ThreadKey
import com.huanchengfly.tieba.post.ui.navigation.UserProfileKey
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.widgets.compose.Button
import com.huanchengfly.tieba.post.ui.widgets.compose.Card
import com.huanchengfly.tieba.post.ui.widgets.compose.Container
import com.huanchengfly.tieba.post.ui.widgets.compose.ErrorScreen
import com.huanchengfly.tieba.post.ui.widgets.compose.FeedCard
import com.huanchengfly.tieba.post.ui.widgets.compose.FeedCardPlaceholder
import com.huanchengfly.tieba.post.ui.widgets.compose.LoadMoreLayout
import com.huanchengfly.tieba.post.ui.widgets.compose.LocalChromePadding
import com.huanchengfly.tieba.post.ui.widgets.compose.MyLazyColumn
import com.huanchengfly.tieba.post.ui.widgets.compose.TipScreen
import com.huanchengfly.tieba.post.ui.widgets.compose.UserHeader
import com.huanchengfly.tieba.post.ui.widgets.compose.states.StateScreen
import com.huanchengfly.tieba.post.utils.DateTimeUtils
import kotlinx.collections.immutable.ImmutableList
import com.huanchengfly.tieba.post.model.ThreadSummary
import com.huanchengfly.tieba.post.model.UserPost
import com.huanchengfly.tieba.post.model.UserThread
import com.huanchengfly.tieba.post.model.UserReplies

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun UserPostPage(
    list: PagerState<UserPost>,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onAgree: (ThreadSummary) -> Unit,
    hidden: Boolean = false,
    fluid: Boolean = false,
    enablePullRefresh: Boolean = false,
) {
    val navigator = LocalNavigator.current
    val posts = list.items
    val isRefreshing = list.refresh == LoadStatus.Loading || list.refresh == LoadStatus.Idle
    val error = (list.refresh as? LoadStatus.Error)?.error
    val hidePost = hidden

    StateScreen(
        modifier = Modifier.fillMaxSize(),
        isEmpty = posts.isEmpty(),
        isError = error != null,
        isLoading = isRefreshing,
        onReload = onRefresh,
        loadingScreen = {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column {
                    repeat(4) {
                        FeedCardPlaceholder()
                    }
                }
            }
        },
        errorScreen = { ErrorScreen(error = error) },
        emptyScreen = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                if (hidePost) {
                    TipScreen(
                        title = { Text(text = stringResource(id = R.string.title_user_hide_post)) },
                        image = {
                            val composition by rememberLottieComposition(
                                LottieCompositionSpec.RawRes(
                                    R.raw.lottie_hide
                                )
                            )
                            LottieAnimation(
                                composition = composition,
                                iterations = LottieConstants.IterateForever,
                                modifier = Modifier
                                    .padding(vertical = 16.dp)
                                    .fillMaxWidth()
                                    .aspectRatio(2.5f)
                            )
                        },
                        scrollable = false,
                    )
                } else {
                    TipScreen(
                        title = { Text(text = stringResource(id = R.string.title_empty)) },
                        image = {
                            val composition by rememberLottieComposition(
                                LottieCompositionSpec.RawRes(
                                    R.raw.lottie_empty_box
                                )
                            )
                            LottieAnimation(
                                composition = composition,
                                iterations = LottieConstants.IterateForever,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f)
                            )
                        },
                        actions = {
                            if (canReload) {
                                Button(onClick = { reload() }) {
                                    Text(text = stringResource(id = R.string.btn_refresh))
                                }
                            }
                        },
                        scrollable = false,
                    )
                }
            }
        },
    ) {
        val pullRefreshState = rememberPullRefreshState(
            refreshing = list.refresh == LoadStatus.Loading,
            onRefresh = onRefresh
        )

        val lazyListState = rememberLazyListState()

        val pullRefreshModifier =
            if (enablePullRefresh) Modifier.pullRefresh(pullRefreshState) else Modifier

        Box(modifier = pullRefreshModifier) {
            LoadMoreLayout(
                isLoading = list.append == LoadStatus.Loading,
                onLoadMore = onLoadMore,
                loadEnd = !list.hasMore,
                lazyListState = lazyListState
            ) {
                UserPostList(
                    data = posts,
                    fluid = fluid,
                    lazyListState = lazyListState,
                    onClickItem = { threadId, postId, isSubPost ->
                        if (postId == null) {
                            navigator.navigate(ThreadKey(threadId))
                        } else {
                            if (isSubPost) {
                                navigator.navigate(
                                    SubPostsKey(
                                        threadId = threadId,
                                        subPostId = postId,
                                        loadFromSubPost = true
                                    )
                                )
                            } else {
                                navigator.navigate(
                                    ThreadKey(
                                        threadId,
                                        postId = postId,
                                        scrollToReply = true
                                    )
                                )
                            }
                        }
                    },
                    onAgreeItem = onAgree,
                    onClickReply = {
                        navigator.navigate(
                            ThreadKey(
                                it.id,
                                it.forum.id,
                                scrollToReply = true
                            )
                        )
                    },
                    onClickUser = {
                        navigator.navigate(UserProfileKey(it))
                    },
                    onClickForum = {
                        navigator.navigate(ForumKey(it))
                    },
                    onClickOriginThread = {
                        navigator.navigate(ThreadKey(it))
                    },
                )
            }

            PullRefreshIndicator(
                refreshing = list.refresh == LoadStatus.Loading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = ExtendedTheme.colors.refreshIndicator,
                contentColor = ExtendedTheme.colors.emphasis,
            )
        }
    }
}

@Composable
private fun UserPostList(
    data: ImmutableList<UserPost>,
    fluid: Boolean = false,
    lazyListState: LazyListState = rememberLazyListState(),
    onClickItem: (threadId: Long, postId: Long?, isSubPost: Boolean) -> Unit = { _, _, _ -> },
    onAgreeItem: (ThreadSummary) -> Unit = {},
    onClickReply: (ThreadSummary) -> Unit = {},
    onClickUser: (id: Long) -> Unit = {},
    onClickForum: (name: String) -> Unit = {},
    onClickOriginThread: (threadId: Long) -> Unit = {},
) {
    MyLazyColumn(state = lazyListState, contentPadding = LocalChromePadding.current) {
        items(
            items = data,
            key = { it.key }
        ) { itemData ->
            Container(fluid = fluid) {
                UserPostItem(
                    post = itemData,
                    onClick = onClickItem,
                    onAgree = onAgreeItem,
                    onClickReply = onClickReply,
                    onClickUser = onClickUser,
                    onClickForum = onClickForum,
                    onClickOriginThread = onClickOriginThread,
                )
            }
        }
    }
}

@Composable
fun UserPostItem(
    post: UserPost,
    onAgree: (ThreadSummary) -> Unit,
    modifier: Modifier = Modifier,
    onClick: (threadId: Long, postId: Long?, isSubPost: Boolean) -> Unit = { _, _, _ -> },
    onClickReply: (ThreadSummary) -> Unit = {},
    onClickUser: (id: Long) -> Unit = {},
    onClickForum: (name: String) -> Unit = {},
    onClickOriginThread: (threadId: Long) -> Unit = {},
) {
    when (post) {
        is UserThread -> FeedCard(
            thread = post.thread,
            onClick = { onClick(it.id, null, false) },
            onAgree = onAgree,
            modifier = modifier,
            onClickReply = onClickReply,
            onClickUser = { onClickUser(it.id) },
            onClickForum = { onClickForum(it.name) },
            onClickShared = { onClickOriginThread(it.id) },
        )

        is UserReplies -> UserRepliesItem(post, onClick, onClickUser, onClickOriginThread, modifier)
    }
}

/** The user's replies in a thread, each opening where it is, over the thread's title. */
@Composable
private fun UserRepliesItem(
    post: UserReplies,
    onClick: (threadId: Long, postId: Long?, isSubPost: Boolean) -> Unit,
    onClickUser: (id: Long) -> Unit,
    onClickOriginThread: (threadId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    run {
        Card(
            header = {
                UserHeader(
                    nameProvider = { post.author.name },
                    nameShowProvider = { post.author.nameShow },
                    portraitProvider = { post.author.portrait },
                    onClick = {
                        onClickUser(post.author.id)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            },
            content = {
                Column {
                    post.replies.fastForEach {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onClick(
                                        post.threadId,
                                        it.postId,
                                        it.isSubPost
                                    )
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = it.text,
                                style = MaterialTheme.typography.body1,
                                color = ExtendedTheme.colors.text,
                            )

                            Text(
                                text = DateTimeUtils.getRelativeTimeString(
                                    LocalContext.current,
                                    it.time
                                ),
                                style = MaterialTheme.typography.caption,
                                color = ExtendedTheme.colors.textSecondary,
                            )
                        }
                    }
                }

                Text(
                    text = post.threadTitle,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(ExtendedTheme.colors.inset)
                        .clickable {
                            onClickOriginThread(post.threadId)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.body2,
                )
            },
            modifier = modifier,
            contentPadding = PaddingValues(0.dp),
        )
    }
}
