package com.huanchengfly.tieba.post.ui.page.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.ScaffoldState
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlignVerticalTop
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.model.Agree
import com.huanchengfly.tieba.post.model.Post
import com.huanchengfly.tieba.post.model.PostContent
import com.huanchengfly.tieba.post.model.SubPost
import com.huanchengfly.tieba.post.models.LoadPicPageData
import com.huanchengfly.tieba.post.models.PhotoViewData
import com.huanchengfly.tieba.post.models.PicItem
import com.huanchengfly.tieba.post.repository.ThreadSort
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.pullRefreshIndicator
import com.huanchengfly.tieba.post.ui.widgets.compose.ConfirmDialog
import com.huanchengfly.tieba.post.ui.widgets.compose.Container
import com.huanchengfly.tieba.post.ui.widgets.compose.ErrorScreen
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.HorizontalDivider
import com.huanchengfly.tieba.post.ui.widgets.compose.LoadMoreLayout
import com.huanchengfly.tieba.post.ui.widgets.compose.MyLazyColumn
import com.huanchengfly.tieba.post.ui.widgets.compose.OriginThreadCard
import com.huanchengfly.tieba.post.ui.widgets.compose.PromptDialog
import com.huanchengfly.tieba.post.ui.widgets.compose.VerticalDivider
import com.huanchengfly.tieba.post.ui.widgets.compose.animateHiddenFraction
import com.huanchengfly.tieba.post.ui.widgets.compose.plus
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberDialogState
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberHideOnScrollState
import com.huanchengfly.tieba.post.ui.widgets.compose.states.StateScreenScope
import com.huanchengfly.tieba.post.utils.ImageUtil
import kotlin.math.roundToInt
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Everything the user can do on the thread page. */
sealed interface ThreadEvent {
    data object Back : ThreadEvent
    data object Refresh : ThreadEvent
    data object Retry : ThreadEvent
    data object LoadMore : ThreadEvent
    data object LoadLatest : ThreadEvent
    data object LoadPrevious : ThreadEvent
    data class SetSeeLz(val seeLz: Boolean) : ThreadEvent
    data object ToggleSort : ThreadEvent
    data object ToggleImmersive : ThreadEvent
    data class JumpToPage(val page: Int) : ThreadEvent
    data object OpenMenu : ThreadEvent
    data object AgreeThread : ThreadEvent
    data class AgreePost(val postId: Long) : ThreadEvent
    data object OpenForum : ThreadEvent
    data class OpenUser(val userId: Long) : ThreadEvent

    /** Reply to the thread when [post] is null, to a post, or to a reply inside it. */
    data class Reply(val post: Post?, val subPost: SubPost? = null) : ThreadEvent

    /** Open the replies inside [post], at [subPostId] or from the start when 0. */
    data class OpenSubPosts(val post: Post, val subPostId: Long) : ThreadEvent
    data class OpenOriginThread(val threadId: Long, val forumId: Long) : ThreadEvent
    data class Copy(val text: String) : ThreadEvent
    data class Report(val postId: Long) : ThreadEvent
    data object ReportThread : ThreadEvent
    data object Share : ThreadEvent
    data object CopyLink : ThreadEvent
    data class StoreAt(val post: Post) : ThreadEvent
    data object StoreAtLastRead : ThreadEvent
    data object RemoveStore : ThreadEvent
    data class DeletePost(val post: Post) : ThreadEvent
    data object DeleteThread : ThreadEvent
    data class ReadUpTo(val postId: Long) : ThreadEvent
}

/** User settings the thread page follows. */
data class ThreadPreferences(
    val hideReply: Boolean = false,
    val hideBlocked: Boolean = false,
)

private const val FIRST_POST_KEY = "FirstPost"
private const val POST_KEY_PREFIX = "Post_"

/** The index of the first reply in the list, after the first floor and the sticky header. */
internal const val FIRST_REPLY_INDEX = 1

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ThreadScreen(
    state: ThreadState,
    onEvent: (ThreadEvent) -> Unit,
    modifier: Modifier = Modifier,
    preferences: ThreadPreferences = ThreadPreferences(),
    listState: LazyListState = rememberLazyListState(),
    scaffoldState: ScaffoldState = rememberScaffoldState(),
    sheetState: ModalBottomSheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden, skipHalfExpanded = true),
) {
    val coroutineScope = rememberCoroutineScope()
    val jumpDialog = rememberDialogState()
    val deleteDialog = rememberDialogState()
    var deleting by remember { mutableStateOf<Post?>(null) }
    val newestFirst = state.query.sort == ThreadSort.NewestFirst
    val viewer = state.viewer.user

    val handleEvent: (ThreadEvent) -> Unit = { event ->
        when (event) {
            ThreadEvent.OpenMenu -> coroutineScope.launch {
                if (sheetState.isVisible) sheetState.hide() else sheetState.show()
            }

            is ThreadEvent.DeletePost -> {
                deleting = event.post
                deleteDialog.show()
            }

            else -> onEvent(event)
        }
    }

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        sheetBackgroundColor = ExtendedTheme.colors.windowBackground,
        scrimColor = Color.Transparent,
        sheetContent = {
            ThreadMenu(
                seeLz = state.query.seeLz,
                stored = state.thread?.isStored == true,
                immersive = state.immersive,
                newestFirst = newestFirst,
                canDelete = viewer != null && state.thread?.author?.id == viewer.id,
                onEvent = { event ->
                    coroutineScope.launch { sheetState.hide() }
                    onEvent(event)
                },
                onJumpToPage = {
                    coroutineScope.launch { sheetState.hide() }
                    jumpDialog.show()
                },
                onDelete = {
                    coroutineScope.launch { sheetState.hide() }
                    deleting = null
                    deleteDialog.show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            )
        },
        modifier = modifier,
    ) {
        val hide = rememberHideOnScrollState()
        val hiddenFraction by hide.animateHiddenFraction()
        GlassScaffold(
            modifier = Modifier.nestedScroll(hide.nestedScrollConnection),
            scaffoldState = scaffoldState,
            topBar = { ThreadTopBar(state.thread?.forum, handleEvent) { hiddenFraction } },
            bottomBar = {
                val thread = state.thread
                if (thread != null) {
                    ThreadReplyBar(
                        viewer = viewer,
                        agree = thread.agree,
                        hideReply = preferences.hideReply,
                        onEvent = handleEvent,
                    ) { hiddenFraction }
                }
            },
        ) { padding ->
            ThreadContent(state, preferences, listState, padding, { hiddenFraction }, handleEvent)
        }
    }

    ConfirmDialog(
        dialogState = deleteDialog,
        onConfirm = {
            val post = deleting
            onEvent(if (post == null) ThreadEvent.DeleteThread else ThreadEvent.DeletePost(post))
        },
    ) {
        Text(
            text = stringResource(
                id = R.string.message_confirm_delete,
                deleting?.let { stringResource(id = R.string.tip_post_floor, it.floor) }
                    ?: stringResource(id = R.string.this_thread)
            )
        )
    }

    PromptDialog(
        onConfirm = { it.toIntOrNull()?.let { page -> onEvent(ThreadEvent.JumpToPage(page)) } },
        dialogState = jumpDialog,
        onValueChange = { newVal, _ -> "^[0-9]*$".toRegex().matches(newVal) },
        title = { Text(text = stringResource(id = R.string.title_jump_page)) },
    ) {
        Text(text = stringResource(id = R.string.tip_jump_page, state.currentPage, state.totalPage))
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun ThreadContent(
    state: ThreadState,
    preferences: ThreadPreferences,
    listState: LazyListState,
    padding: PaddingValues,
    barsHiddenFraction: () -> Float,
    onEvent: (ThreadEvent) -> Unit,
) {
    val posts = state.posts
    val firstPost = state.firstPost ?: state.preview?.let { preview ->
        Post(
            id = 0,
            floor = 1,
            title = preview.title,
            time = preview.time,
            author = preview.author ?: return@let null,
            content = preview.content,
            agree = Agree(0, false),
            subPosts = persistentListOf(),
            subPostCount = 0,
        )
    }
    val loadingFirstTime = posts.refresh == LoadStatus.Loading && posts.items.isEmpty()
    val error = (posts.refresh as? LoadStatus.Error)?.error

    if (firstPost == null && posts.items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                error != null -> remember { StateScreenScope { onEvent(ThreadEvent.Retry) } }
                    .ErrorScreen(error = error, modifier = Modifier.fillMaxSize())

                loadingFirstTime -> CircularProgressIndicator(color = ExtendedTheme.colors.primary)
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
                    item(key = "ThreadHeader") { ThreadHeader(state, onEvent) }
                }
                if (posts.hasPrevious && !newestFirst) {
                    item(key = "LoadPrevious") { LoadPreviousButton(onEvent) }
                }
                items(
                    items = posts.items,
                    key = { "$POST_KEY_PREFIX${it.post.id}" },
                ) { item ->
                    Container {
                        ThreadPostCard(
                            item = item,
                            options = options,
                            onEvent = onEvent,
                            isStoreMark = item.post.id == state.thread?.storeMarkPostId,
                            canDelete = viewerId != null && item.post.author.id == viewerId,
                            photoViewData = { photoViewData(item.post, it) },
                        )
                    }
                }
                if (loadingFirstTime) {
                    item(key = "Loading") {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ExtendedTheme.colors.primary)
                        }
                    }
                }
            }
        }

        if (state.thread != null) PinnedThreadHeader(state, listState, padding, barsHiddenFraction, onEvent)

        PullRefreshIndicator(
            refreshing = pullRefreshing,
            state = pullRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = padding.calculateTopPadding()),
            backgroundColor = ExtendedTheme.colors.pullRefreshIndicator,
            contentColor = ExtendedTheme.colors.primary,
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
            ThreadPostCard(
                item = PostItem(post, blocked = false, blockedSubPosts = persistentSetOf()),
                options = options,
                onEvent = { event -> if (event is ThreadEvent.Reply) onEvent(ThreadEvent.Reply(null)) else onEvent(event) },
                isStoreMark = post.id == state.thread?.storeMarkPostId,
                canDelete = viewerId != null && post.author.id == viewerId,
                showSubPosts = false,
                photoViewData = { photoViewData(post, it) },
            )
            state.thread?.origin?.let { origin ->
                OriginThreadCard(
                    originThreadInfo = origin,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(ExtendedTheme.colors.floorCard)
                        .clickable {
                            onEvent(ThreadEvent.OpenOriginThread(origin.get { tid.toLong() }, origin.get { fid }))
                        }
                        .padding(16.dp)
                )
            }
            VerticalDivider(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                thickness = 2.dp
            )
        }
    }
}

/** The reply count and the switch between all posts and the thread author's. */
@Composable
private fun ThreadHeader(state: ThreadState, onEvent: (ThreadEvent) -> Unit, modifier: Modifier = Modifier) {
    Container(modifier = modifier) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colors.background)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.title_thread_header, "${(state.thread?.replyCount ?: 1) - 1}"),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = ExtendedTheme.colors.text,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(IntrinsicSize.Min)
            ) {
                HeaderFilter(stringResource(R.string.text_all), selected = !state.query.seeLz) {
                    onEvent(ThreadEvent.SetSeeLz(false))
                }
                HorizontalDivider()
                HeaderFilter(stringResource(R.string.title_see_lz), selected = state.query.seeLz) {
                    onEvent(ThreadEvent.SetSeeLz(true))
                }
            }
        }
    }
}

/**
 * The header again, held under the top bar once the list has scrolled past it. A sticky list
 * header would stick to the top of the screen, behind the floating bar.
 */
@Composable
private fun PinnedThreadHeader(
    state: ThreadState,
    listState: LazyListState,
    padding: PaddingValues,
    barsHiddenFraction: () -> Float,
    onEvent: (ThreadEvent) -> Unit,
) {
    val pinned by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex >= FIRST_REPLY_INDEX } }
    if (!pinned) return
    val density = LocalDensity.current
    val statusBar = WindowInsets.statusBars.getTop(density)
    ThreadHeader(
        state = state,
        onEvent = onEvent,
        modifier = Modifier.offset {
            val belowBar = padding.calculateTopPadding().roundToPx()
            IntOffset(0, maxOf(statusBar, (belowBar * (1 - barsHiddenFraction())).roundToInt()))
        },
    )
}

@Composable
private fun HeaderFilter(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !selected,
                onClick = onClick,
            ),
        fontSize = 13.sp,
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
