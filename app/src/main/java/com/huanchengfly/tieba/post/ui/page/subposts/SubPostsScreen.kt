package com.huanchengfly.tieba.post.ui.page.subposts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AlignVerticalTop
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.model.SubPost
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.page.thread.PostCardOptions
import com.huanchengfly.tieba.post.ui.page.thread.PostItem
import com.huanchengfly.tieba.post.ui.page.thread.ThreadEvent
import com.huanchengfly.tieba.post.ui.page.thread.ThreadPostCard
import com.huanchengfly.tieba.post.ui.page.thread.ThreadPreferences
import com.huanchengfly.tieba.post.ui.widgets.compose.Avatar
import com.huanchengfly.tieba.post.ui.widgets.compose.ConfirmDialog
import com.huanchengfly.tieba.post.ui.widgets.compose.ErrorScreen
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBar
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBarTitle
import com.huanchengfly.tieba.post.ui.widgets.compose.LoadMoreLayout
import com.huanchengfly.tieba.post.ui.widgets.compose.MyLazyColumn
import com.huanchengfly.tieba.post.ui.widgets.compose.MyScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.Sizes
import com.huanchengfly.tieba.post.ui.widgets.compose.TitleCentredToolbar
import com.huanchengfly.tieba.post.ui.widgets.compose.VerticalDivider
import com.huanchengfly.tieba.post.ui.widgets.compose.floatingGlass
import com.huanchengfly.tieba.post.ui.widgets.compose.paddedStickyHeader
import com.huanchengfly.tieba.post.ui.widgets.compose.plus
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberDialogState
import com.huanchengfly.tieba.post.ui.widgets.compose.states.StateScreenScope
import com.huanchengfly.tieba.post.utils.StringUtil

/**
 * The replies inside a post, as a page of its own or, with [isSheet], in a bottom sheet over the
 * thread.
 *
 * @param replyAvatar the logged-in user's portrait for the reply box, or null to show none
 */
@Composable
fun SubPostsScreen(
    state: SubPostsState,
    onEvent: (SubPostsEvent) -> Unit,
    modifier: Modifier = Modifier,
    isSheet: Boolean = false,
    replyAvatar: String? = null,
    preferences: ThreadPreferences = ThreadPreferences(),
    listState: LazyListState = rememberLazyListState(),
) {
    val deleteDialog = rememberDialogState()
    var deleting by remember { mutableStateOf<SubPost?>(null) }
    val handleEvent: (SubPostsEvent) -> Unit = { event ->
        if (event is SubPostsEvent.Delete) {
            deleting = event.subPost
            deleteDialog.show()
        } else {
            onEvent(event)
        }
    }
    val title = state.post?.let { stringResource(id = R.string.title_sub_posts, it.floor) }
        ?: stringResource(id = R.string.title_sub_posts_default)
    val replyBar: @Composable () -> Unit = {
        if (replyAvatar != null && !preferences.hideReply && state.post != null) {
            ReplyCapsule(replyAvatar) { handleEvent(SubPostsEvent.ReplyToPost) }
        }
    }
    val content: @Composable (PaddingValues) -> Unit = { padding ->
        SubPostsContent(state, preferences, listState, padding, handleEvent)
    }

    if (isSheet) {
        MyScaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TitleCentredToolbar(
                    title = { Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.h6) },
                    navigationIcon = {
                        IconButton(onClick = { onEvent(SubPostsEvent.Back) }) {
                            Icon(imageVector = Icons.Rounded.Close, contentDescription = stringResource(id = R.string.btn_close))
                        }
                    },
                )
            },
            bottomBar = replyBar,
            content = content,
        )
    } else {
        GlassScaffold(
            modifier = modifier,
            topBar = {
                GlassTopBar(
                    title = { GlassTopBarTitle(title) },
                    navigationIcon = {
                        IconButton(onClick = { onEvent(SubPostsEvent.Back) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(id = R.string.btn_close)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { onEvent(SubPostsEvent.OpenThread) }) {
                            Icon(
                                imageVector = Icons.Rounded.OpenInBrowser,
                                contentDescription = stringResource(id = R.string.btn_open_origin_thread)
                            )
                        }
                    },
                )
            },
            bottomBar = replyBar,
            content = content,
        )
    }

    ConfirmDialog(
        dialogState = deleteDialog,
        onConfirm = { onEvent(SubPostsEvent.Delete(deleting)) },
    ) {
        Text(
            text = stringResource(
                id = R.string.message_confirm_delete,
                if (deleting == null && state.post != null) stringResource(id = R.string.tip_post_floor, state.post.floor)
                else stringResource(id = R.string.this_reply)
            )
        )
    }
}

@Composable
private fun SubPostsContent(
    state: SubPostsState,
    preferences: ThreadPreferences,
    listState: LazyListState,
    padding: PaddingValues,
    onEvent: (SubPostsEvent) -> Unit,
) {
    val post = state.post
    val subPosts = state.subPosts
    if (post == null) {
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            val error = (subPosts.refresh as? LoadStatus.Error)?.error
            if (error != null) {
                remember { StateScreenScope { onEvent(SubPostsEvent.Retry) } }
                    .ErrorScreen(error = error, modifier = Modifier.fillMaxSize())
            } else {
                CircularProgressIndicator(color = ExtendedTheme.colors.primary)
            }
        }
        return
    }

    val postEvents: (ThreadEvent) -> Unit = { event ->
        when (event) {
            is ThreadEvent.Reply -> onEvent(SubPostsEvent.Reply(post))
            is ThreadEvent.AgreePost -> onEvent(SubPostsEvent.AgreePost)
            is ThreadEvent.OpenUser -> onEvent(SubPostsEvent.OpenUser(event.userId))
            is ThreadEvent.Copy -> onEvent(SubPostsEvent.Copy(event.text))
            is ThreadEvent.Report -> onEvent(SubPostsEvent.Report(event.postId))
            is ThreadEvent.DeletePost -> onEvent(SubPostsEvent.Delete(null))
            else -> Unit
        }
    }
    LoadMoreLayout(
        isLoading = subPosts.append == LoadStatus.Loading,
        onLoadMore = { onEvent(SubPostsEvent.LoadMore) },
        loadEnd = !subPosts.hasMore,
        lazyListState = listState,
        isEmpty = subPosts.items.isEmpty(),
    ) {
        MyLazyColumn(
            state = listState,
            contentPadding = padding.plus(PaddingValues(bottom = 8.dp)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item(key = "Post") {
                Column {
                    ThreadPostCard(
                        item = PostItem(post, blocked = state.postBlocked),
                        options = PostCardOptions(threadAuthorId = 0, hideBlocked = preferences.hideBlocked, hideReply = preferences.hideReply),
                        onEvent = postEvents,
                        canDelete = state.viewerId != null && post.author.id == state.viewerId,
                        canStore = false,
                        showSubPosts = false,
                    )
                    VerticalDivider(thickness = 2.dp)
                }
            }
            paddedStickyHeader(listState, key = "SubPostsHeader") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ExtendedTheme.colors.background)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.title_sub_posts_header, state.totalCount),
                        style = MaterialTheme.typography.subtitle1
                    )
                }
            }
            if (subPosts.hasPrevious) {
                item(key = "LoadPrevious") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEvent(SubPostsEvent.LoadPrevious) }
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Rounded.AlignVerticalTop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = stringResource(id = R.string.btn_load_previous), color = ExtendedTheme.colors.text, fontSize = 14.sp)
                    }
                }
            }
            items(items = subPosts.items, key = { it.subPost.id }) { item ->
                SubPostCard(
                    post = post,
                    item = item,
                    threadAuthorId = state.threadAuthorId,
                    canDelete = state.viewerId != null && item.subPost.author.id == state.viewerId,
                    hideReply = preferences.hideReply,
                    onEvent = onEvent,
                )
            }
        }
    }
}

private val ReplyCapsuleShape = RoundedCornerShape(percent = 50)

/** A floating reply box with the user's avatar. */
@Composable
private fun ReplyCapsule(avatar: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.navigationBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
            )
            .height(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .floatingGlass(ReplyCapsuleShape)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Avatar(data = StringUtil.getAvatarUrl(avatar), size = Sizes.Tiny, contentDescription = null)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(ReplyCapsuleShape)
                    .background(ExtendedTheme.colors.bottomBarSurface)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.tip_reply_thread),
                    style = MaterialTheme.typography.caption,
                    color = ExtendedTheme.colors.onBottomBarSurface,
                )
            }
        }
    }
}
