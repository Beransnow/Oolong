package oolong.client.ui.page.topic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import oolong.client.R
import oolong.client.arch.Actions
import oolong.client.arch.LoadStatus
import oolong.client.arch.MessageController
import oolong.client.arch.Page
import oolong.client.arch.PagerState
import oolong.client.arch.query.QueryClient
import oolong.client.arch.query.pager
import oolong.client.model.ForumSummary
import oolong.client.model.ThreadSummary
import oolong.client.model.Topic
import oolong.client.model.TopicForum
import oolong.client.repository.BlockRuleSource
import oolong.client.repository.ExploreRepository
import oolong.client.repository.ThreadActions
import oolong.client.repository.Threads
import oolong.client.ui.common.theme.compose.Corners
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Spacing
import oolong.client.ui.common.theme.compose.TextStyles
import oolong.client.ui.navigation.ForumKey
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.navigation.TopicKey
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.page.main.explore.FeedThread
import oolong.client.ui.page.main.explore.FeedThreadEvents
import oolong.client.ui.page.main.explore.feedThread
import oolong.client.ui.page.main.explore.mapItems
import oolong.client.ui.page.main.explore.rememberFeedThreadEvents
import oolong.client.ui.widgets.compose.Avatar
import oolong.client.ui.widgets.compose.BackNavigationIcon
import oolong.client.ui.widgets.compose.BlockableContent
import oolong.client.ui.widgets.compose.ContentImage
import oolong.client.ui.widgets.compose.EndPull
import oolong.client.ui.widgets.compose.ErrorScreen
import oolong.client.ui.widgets.compose.FeedCard
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.GlassTopBarTitle
import oolong.client.ui.widgets.compose.LoadMoreEffect
import oolong.client.ui.widgets.compose.MyLazyColumn
import oolong.client.ui.widgets.compose.PullRefresh
import oolong.client.ui.widgets.compose.VerticalDivider
import oolong.client.ui.widgets.compose.loadMoreFooter
import oolong.client.ui.widgets.compose.states.StateScreen
import oolong.client.utils.StringUtil.getShortNumString

/** Where a topic's next page starts: its number, and the last thread of the one before. */
private data class TopicCursor(val page: Int, val lastThreadId: Long)

/** What the first page of a topic tells of it besides its threads. */
private data class TopicHead(val topic: Topic?, val forums: List<TopicForum>, val leadThreadId: Long?)

@Immutable
data class TopicState(
    val topic: Topic? = null,
    val forums: ImmutableList<TopicForum> = persistentListOf(),
    /** The thread picked to lead the topic, which shows as pinned. */
    val leadThreadId: Long? = null,
    val threads: PagerState<FeedThread> = PagerState(refresh = LoadStatus.Loading),
)

/** A hot topic and the threads talking about it, a page at a time. */
@HiltViewModel(assistedFactory = TopicViewModel.Factory::class)
class TopicViewModel @AssistedInject constructor(
    @Assisted private val key: TopicKey,
    private val repository: ExploreRepository,
    client: QueryClient,
    private val threadActions: ThreadActions,
    private val messages: MessageController,
    blockRuleSource: BlockRuleSource,
) : ViewModel() {
    private val actions = Actions(viewModelScope)
    private val head = MutableStateFlow(TopicHead(null, emptyList(), null))

    private val pager = client.pager(
        scope = viewModelScope,
        initialKey = TopicCursor(1, 0),
        itemKey = { thread: ThreadSummary -> thread.id },
        normalize = { Threads(it) },
    ) { cursor ->
        val page = repository.topic(key.id, key.name, cursor.page, cursor.lastThreadId)
        if (cursor.page == 1) head.value = TopicHead(page.topic, page.forums, page.threads.firstOrNull { it.second }?.first?.id)
        val threads = page.threads.map { it.first }
        Page(threads, next = threads.lastOrNull()?.takeIf { page.hasMore }?.let { TopicCursor(cursor.page + 1, it.id) })
    }

    val state: StateFlow<TopicState> = combine(head, pager.state, blockRuleSource.rules) { head, threads, rules ->
        TopicState(
            topic = head.topic,
            forums = head.forums.toImmutableList(),
            leadThreadId = head.leadThreadId,
            threads = threads.mapItems(rules::feedThread),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TopicState())

    init {
        pager.refresh()
    }

    fun refresh() = pager.refresh()

    fun loadMore() = pager.loadMore()

    fun agree(thread: ThreadSummary) = actions.launch("agree-${thread.id}") {
        runCatching { threadActions.agree(thread, !thread.agree.agreed) }
            .onFailure { messages.showError(R.string.toast_agree_failed, it) }
    }

    @AssistedFactory
    interface Factory {
        fun create(key: TopicKey): TopicViewModel
    }
}

@Composable
fun TopicPage(
    key: TopicKey,
    navigator: Navigator = LocalNavigator.current,
    viewModel: TopicViewModel = hiltViewModel<TopicViewModel, TopicViewModel.Factory> { it.create(key) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()
    // The header names the topic; the bar does once it has scrolled away
    val headerGone by remember { derivedStateOf { state.topic == null || lazyListState.firstVisibleItemIndex > 0 } }
    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = {
                    if (headerGone) GlassTopBarTitle(text = state.topic?.name ?: key.name.ifEmpty { stringResource(R.string.title_topic) })
                },
                navigationIcon = { BackNavigationIcon(onBackPressed = { navigator.navigateUp() }) },
            )
        },
    ) { padding ->
        TopicScreen(
            state = state,
            events = rememberFeedThreadEvents(onAgree = viewModel::agree),
            onRefresh = viewModel::refresh,
            onLoadMore = viewModel::loadMore,
            onOpenForum = { navigator.navigate(ForumKey(it.name)) },
            contentPadding = padding,
            lazyListState = lazyListState,
        )
    }
}

/**
 * A topic: its picture, name, place and heat, what it is about, the forums it is talked about in,
 * then its threads, the one picked to lead first and marked pinned. The topic's own colour washes
 * faintly down from the top, as a forum's does on its page.
 */
@Composable
fun TopicScreen(
    state: TopicState,
    events: FeedThreadEvents,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenForum: (ForumSummary) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    lazyListState: LazyListState = rememberLazyListState(),
) {
    val list = state.threads
    val error = (list.refresh as? LoadStatus.Error)?.error
    StateScreen(
        modifier = Modifier.fillMaxSize(),
        isEmpty = list.items.isEmpty() && state.topic == null,
        isError = error != null,
        isLoading = list.refresh == LoadStatus.Loading,
        onReload = onRefresh,
        errorScreen = { ErrorScreen(error = error, modifier = Modifier.padding(contentPadding)) },
    ) {
        LoadMoreEffect(lazyListState, enabled = list.hasMore && list.append == LoadStatus.Idle, onLoadMore = onLoadMore)
        PullRefresh(
            refreshing = false,
            onRefresh = onRefresh,
            indicatorTop = contentPadding.calculateTopPadding(),
            endPull = if (list.hasMore) EndPull.None else EndPull.Tease,
        ) {
            Box {
                state.topic?.color?.let { TopicWash(Color(it)) }
                MyLazyColumn(state = lazyListState, contentPadding = contentPadding, modifier = Modifier.fillMaxWidth()) {
                    state.topic?.let { topic -> item(key = "Head") { TopicHeader(topic) } }
                    if (state.forums.isNotEmpty()) {
                        item(key = "Forums") { TopicForums(state.forums, onOpenForum) }
                    }
                    itemsIndexed(list.items, key = { _, item -> item.thread.id }) { index, item ->
                        BlockableContent(block = item.block, modifier = Modifier.padding(vertical = Spacing.xs, horizontal = Spacing.md)) {
                            Column {
                                FeedCard(
                                    thread = item.thread,
                                    onClick = events.onOpen,
                                    onClickReply = events.onOpenReplies,
                                    onAgree = events.onAgree,
                                    onClickForum = events.onOpenForum,
                                    onClickUser = events.onOpenUser,
                                    onClickShared = events.onOpenOrigin,
                                    onLongPress = events.onPeek,
                                    dislikeAction = { if (item.thread.id == state.leadThreadId) PinnedLabel() },
                                )
                                if (index < list.items.size - 1) VerticalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
                            }
                        }
                    }
                    loadMoreFooter(list.append, list.hasMore, onLoadMore)
                }
            }
        }
    }
}

/** The topic's colour, faint at the top and gone a little way down, behind the header. */
@Composable
private fun TopicWash(color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0.06f), Color.Transparent))),
    )
}

@Composable
private fun TopicHeader(topic: Topic) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.xs, bottom = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        if (topic.picture.isNotEmpty()) {
            ContentImage(
                imageUri = topic.picture,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(bottom = Spacing.sm)
                    .fillMaxWidth()
                    .aspectRatio(2.2f)
                    .clip(Corners.lg),
            )
        }
        Text(text = topic.name, style = TextStyles.display, color = ExtendedTheme.colors.text)
        val stats = listOfNotNull(
            topic.rank.takeIf { it > 0 }?.let { stringResource(R.string.topic_rank, it) },
            stringResource(R.string.hot_num, topic.discussCount.getShortNumString()),
        ).joinToString(" · ")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            TopicTag(topic.tag)
            Text(text = stats, style = TextStyles.caption, color = ExtendedTheme.colors.textSecondary)
        }
        if (topic.description.isNotEmpty()) {
            Text(
                text = topic.description,
                style = TextStyles.reply,
                color = ExtendedTheme.colors.text,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
        }
    }
}

/** The forums a topic is talked about in, side by side, each opening its forum. */
@Composable
private fun TopicForums(forums: ImmutableList<TopicForum>, onOpen: (ForumSummary) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.padding(bottom = Spacing.xs),
    ) {
        items(forums, key = { it.forum.id }) { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier
                    .clip(Corners.full)
                    .background(ExtendedTheme.colors.chip)
                    .clickable { onOpen(item.forum) }
                    .padding(start = Spacing.xxs, end = Spacing.sm, top = Spacing.xxs, bottom = Spacing.xxs),
            ) {
                Avatar(data = item.forum.avatar, size = 28.dp, contentDescription = null)
                Text(
                    text = stringResource(R.string.title_forum, item.forum.name),
                    style = TextStyles.nameCompact,
                    color = ExtendedTheme.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = item.members.getShortNumString(), style = TextStyles.footnote, color = ExtendedTheme.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun PinnedLabel() {
    Text(
        text = stringResource(R.string.content_top),
        style = TextStyles.footnote,
        color = ExtendedTheme.colors.textSecondary,
        modifier = Modifier
            .clip(Corners.xs)
            .background(ExtendedTheme.colors.chip)
            .padding(horizontal = Spacing.xs, vertical = 2.dp),
    )
}
