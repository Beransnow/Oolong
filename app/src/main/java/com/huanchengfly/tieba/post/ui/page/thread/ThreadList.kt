package com.huanchengfly.tieba.post.ui.page.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlignVerticalTop
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.model.Post
import com.huanchengfly.tieba.post.model.PostContent
import com.huanchengfly.tieba.post.model.knownFirstFloor
import com.huanchengfly.tieba.post.models.LoadPicPageData
import com.huanchengfly.tieba.post.models.PhotoViewData
import com.huanchengfly.tieba.post.models.PicItem
import com.huanchengfly.tieba.post.repository.ThreadSort
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.widgets.compose.Container
import com.huanchengfly.tieba.post.ui.widgets.compose.ErrorScreen
import com.huanchengfly.tieba.post.ui.widgets.compose.LoadMoreLayout
import com.huanchengfly.tieba.post.ui.widgets.compose.MyLazyColumn
import com.huanchengfly.tieba.post.ui.widgets.compose.SharedThreadCard
import com.huanchengfly.tieba.post.ui.widgets.compose.VerticalDivider
import com.huanchengfly.tieba.post.ui.widgets.compose.plus
import com.huanchengfly.tieba.post.ui.widgets.compose.states.StateScreenScope
import com.huanchengfly.tieba.post.utils.ImageUtil
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.distinctUntilChanged

private const val FIRST_POST_KEY = "FirstPost"
private const val POST_KEY_PREFIX = "Post_"

/** The index of the first reply in the list, after the first floor and the reply count header. */
internal const val FIRST_REPLY_INDEX = 1

private const val THREAD_HEADER_KEY = "ThreadHeader"

/**
 * Whether the reply count header has scrolled up to where the top bar shows it pinned, so the
 * bar should show it instead. [pinnedAbove] is how far above the end of the list's top padding
 * the pinned header sits, in pixels.
 */
internal fun LazyListState.isThreadHeaderPinned(pinnedAbove: Int): Boolean {
    val header = layoutInfo.visibleItemsInfo.firstOrNull { it.key == THREAD_HEADER_KEY }
    // Item offsets start at the end of the top padding
    return if (header != null) header.offset <= -pinnedAbove else firstVisibleItemIndex > FIRST_REPLY_INDEX
}

/** The posts of the thread, or the first load or its error in their place. */
@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun ThreadContent(
    state: ThreadState,
    preferences: ThreadPreferences,
    listState: LazyListState,
    padding: PaddingValues,
    onEvent: (ThreadEvent) -> Unit,
    chromeEnd: () -> Int = { 0 },
) {
    val posts = state.posts
    val firstPost = state.firstPost ?: state.preview?.knownFirstFloor()
    val loadingFirstTime = posts.refresh == LoadStatus.Loading && posts.items.isEmpty()
    val error = (posts.refresh as? LoadStatus.Error)?.error

    if (firstPost == null && posts.items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                error != null -> remember { StateScreenScope { onEvent(ThreadEvent.Retry) } }
                    .ErrorScreen(error = error, modifier = Modifier.fillMaxSize())

                loadingFirstTime -> CircularProgressIndicator(color = ExtendedTheme.colors.emphasis)
            }
        }
        return
    }

    TrackReadPosition(listState, firstPost?.id, onEvent)

    val newestFirst = state.query.sort == ThreadSort.NewestFirst
    // The first load shows its own indicator in the list
    val pullRefreshing = !loadingFirstTime &&
        (if (newestFirst) posts.prepend == LoadStatus.Loading else posts.refresh == LoadStatus.Loading)
    val pullRefreshState = rememberPullRefreshState(
        refreshing = pullRefreshing,
        onRefresh = { onEvent(if (newestFirst) ThreadEvent.LoadPrevious else ThreadEvent.Refresh) },
    )
    val options = PostCardOptions(
        threadAuthorId = state.thread?.author?.id ?: firstPost?.author?.id ?: 0L,
        immersive = state.immersive,
        hideBlocked = preferences.hideBlocked,
        hideReply = preferences.hideReply,
    )
    val viewerId = state.viewer.user?.id
    val photoViewData = rememberPhotoViewData(state)

    Box(modifier = Modifier.pullRefresh(pullRefreshState, enabled = posts.hasPrevious || newestFirst)) {
        LoadMoreLayout(
            isLoading = posts.append == LoadStatus.Loading,
            onLoadMore = { onEvent(if (posts.hasMore) ThreadEvent.LoadMore else ThreadEvent.LoadLatest) },
            loadEnd = !posts.hasMore && newestFirst,
            indicator = { isLoading, loadEnd, willLoad ->
                LoadMorePill(isLoading, loadEnd, willLoad, posts.hasMore)
            },
            lazyListState = listState,
            isEmpty = posts.items.isEmpty(),
            preloadCount = if (posts.hasMore) 1 else 0,
        ) {
            MyLazyColumn(
                state = listState,
                contentPadding = padding.plus(PaddingValues(bottom = 8.dp)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                item(key = FIRST_POST_KEY) {
                    if (firstPost != null) {
                        FirstPost(state, firstPost, options, viewerId, photoViewData, onEvent)
                    }
                }
                if (state.thread != null) {
                    item(key = THREAD_HEADER_KEY) { ThreadHeader(state, onEvent) }
                }
                if (posts.hasPrevious && !newestFirst) {
                    item(key = "LoadPrevious") { LoadPreviousButton(onEvent) }
                }
                itemsIndexed(
                    items = posts.items,
                    key = { _, it -> "$POST_KEY_PREFIX${it.post.id}" },
                ) { index, item ->
                    Container {
                        if (options.immersive) {
                            ThreadPostCard(
                                item = item,
                                options = options,
                                onEvent = onEvent,
                                isStoreMark = item.post.id == state.thread?.storeMarkPostId,
                                canDelete = viewerId != null && item.post.author.id == viewerId,
                                photoViewData = { photoViewData(item.post, it) },
                            )
                        } else {
                            ThreadFloor(
                                item = item,
                                options = options,
                                onEvent = onEvent,
                                listState = listState,
                                key = "$POST_KEY_PREFIX${item.post.id}",
                                stickBelow = chromeEnd,
                                startsRule = index == 0,
                                isStoreMark = item.post.id == state.thread?.storeMarkPostId,
                                canDelete = viewerId != null && item.post.author.id == viewerId,
                                photoViewData = { photoViewData(item.post, it) },
                            )
                        }
                    }
                }
                if (loadingFirstTime) {
                    item(key = "Loading") {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ExtendedTheme.colors.emphasis)
                        }
                    }
                }
            }
        }

        PullRefreshIndicator(
            refreshing = pullRefreshing,
            state = pullRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = padding.calculateTopPadding()),
            backgroundColor = ExtendedTheme.colors.refreshIndicator,
            contentColor = ExtendedTheme.colors.emphasis,
        )
    }
}

@Composable
private fun FirstPost(
    state: ThreadState,
    post: Post,
    options: PostCardOptions,
    viewerId: Long?,
    photoViewData: (Post, PostContent.Image) -> PhotoViewData?,
    onEvent: (ThreadEvent) -> Unit,
) {
    Container {
        Column {
            if (!options.immersive) {
                ThreadFirstPost(
                    post = post,
                    state = state,
                    options = options,
                    isStoreMark = post.id == state.thread?.storeMarkPostId,
                    canDelete = viewerId != null && post.author.id == viewerId,
                    photoViewData = { photoViewData(post, it) },
                    onEvent = onEvent,
                )
            } else ThreadPostCard(
                item = PostItem(post, blocked = false, blockedSubPosts = persistentSetOf()),
                options = options,
                // Replying to the first floor replies to the thread; it shows no replies inside it to reply to
                onEvent = { event -> if (event is ThreadEvent.Reply) onEvent(ThreadEvent.Reply(null)) else onEvent(event) },
                isStoreMark = post.id == state.thread?.storeMarkPostId,
                canDelete = viewerId != null && post.author.id == viewerId,
                showSubPosts = false,
                photoViewData = { photoViewData(post, it) },
            )
            state.thread?.shared?.let { shared ->
                SharedThreadCard(
                    shared = shared,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(ExtendedTheme.colors.inset)
                        .clickable {
                            onEvent(ThreadEvent.OpenOriginThread(shared.id, shared.forumId))
                        }
                        .padding(16.dp)
                )
            }
            if (options.immersive) {
                VerticalDivider(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                    thickness = 2.dp
                )
            }
        }
    }
}

/**
 * The reply count and the switch between all posts and the thread author's. Shown in the list
 * above the replies, and pinned in the top bar, on its glass, once the list scrolls past it.
 */
@Composable
internal fun ThreadHeader(
    state: ThreadState,
    onEvent: (ThreadEvent) -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = ExtendedTheme.colors.background,
) {
    Container(modifier = modifier) {
        Row(
            modifier = Modifier
                .background(backgroundColor)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.title_thread_header, "${(state.thread?.replyCount ?: 1) - 1}"),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = ExtendedTheme.colors.text,
            )
            Spacer(modifier = Modifier.weight(1f))
            // A segmented switch: the selected side is a pill on the track
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(ExtendedTheme.colors.chip)
                    .padding(3.dp),
            ) {
                HeaderFilter(stringResource(R.string.text_all), selected = !state.query.seeLz) {
                    onEvent(ThreadEvent.SetSeeLz(false))
                }
                HeaderFilter(stringResource(R.string.title_see_lz), selected = state.query.seeLz) {
                    onEvent(ThreadEvent.SetSeeLz(true))
                }
            }
        }
    }
}

@Composable
private fun HeaderFilter(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) ExtendedTheme.colors.background else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !selected,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 5.dp),
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) ExtendedTheme.colors.text else ExtendedTheme.colors.textSecondary,
    )
}

@Composable
private fun LoadPreviousButton(onEvent: (ThreadEvent) -> Unit) {
    Container {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEvent(ThreadEvent.LoadPrevious) }
                .padding(8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.AlignVerticalTop,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(id = R.string.btn_load_previous),
                color = ExtendedTheme.colors.text,
                fontSize = 14.sp
            )
        }
    }
}

/** Where [postId] is in the list, or null when it isn't loaded. */
internal fun ThreadState.indexOfPost(postId: Long): Int? {
    if (postId == firstPost?.id) return 0
    val index = posts.items.indexOfFirst { it.post.id == postId }
    if (index == -1) return null
    val loadPrevious = if (posts.hasPrevious && query.sort != ThreadSort.NewestFirst) 1 else 0
    return FIRST_REPLY_INDEX + 1 + loadPrevious + index
}

/** Reports the last post on screen as the list scrolls, or the first floor when no reply is. */
@Composable
private fun TrackReadPosition(listState: LazyListState, firstPostId: Long?, onEvent: (ThreadEvent) -> Unit) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    val currentFirstPostId by rememberUpdatedState(firstPostId)
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .lastOrNull { (it.key as? String)?.startsWith(POST_KEY_PREFIX) == true }
                ?.let { (it.key as String).removePrefix(POST_KEY_PREFIX).toLongOrNull() }
                ?: currentFirstPostId?.takeIf { it != 0L }
        }
            .distinctUntilChanged()
            .collect { postId -> if (postId != null) currentOnEvent(ThreadEvent.ReadUpTo(postId)) }
    }
}

/** What the image viewer needs to open an image of a post and page through the thread's images. */
@Composable
private fun rememberPhotoViewData(state: ThreadState): (Post, PostContent.Image) -> PhotoViewData? {
    val context = LocalContext.current
    val thread = state.thread
    val seeLz = state.query.seeLz
    return remember(thread?.id, thread?.forum, seeLz) {
        { post, image ->
            thread?.let {
                PhotoViewData(
                    data = LoadPicPageData(
                        forumId = it.forum.id,
                        forumName = it.forum.name,
                        threadId = it.id,
                        postId = post.id,
                        objType = "pb",
                        picId = image.id,
                        picIndex = 1,
                        seeLz = seeLz,
                        originUrl = image.originUrl,
                    ),
                    picItems = persistentListOf(
                        PicItem(
                            picId = image.id,
                            picIndex = 1,
                            url = ImageUtil.getUrl(context, true, image.originUrl, *image.urls.toTypedArray()),
                            originUrl = image.originUrl,
                            showOriginBtn = image.showOriginButton,
                            originSize = image.originSize,
                            postId = post.id,
                        )
                    ),
                )
            }
        }
    }
}
