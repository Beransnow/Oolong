package com.huanchengfly.tieba.post.ui.page.forum.searchpost

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ContentAlpha
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.model.ForumSearchOrder
import com.huanchengfly.tieba.post.model.QuotedPost
import com.huanchengfly.tieba.post.model.SearchAuthor
import com.huanchengfly.tieba.post.model.SearchHistoryItem
import com.huanchengfly.tieba.post.model.SearchedPost
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.PressFeedback
import com.huanchengfly.tieba.post.ui.common.theme.compose.Symbols
import com.huanchengfly.tieba.post.ui.navigation.ForumSearchPostKey
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.navigation.SubPostsKey
import com.huanchengfly.tieba.post.ui.navigation.ThreadKey
import com.huanchengfly.tieba.post.ui.navigation.UserProfileKey
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.page.ProvideNavigator
import com.huanchengfly.tieba.post.ui.widgets.compose.Button
import com.huanchengfly.tieba.post.ui.widgets.compose.ErrorScreen
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBar
import com.huanchengfly.tieba.post.ui.widgets.compose.HorizontalDivider
import com.huanchengfly.tieba.post.ui.widgets.compose.LoadMoreLayout
import com.huanchengfly.tieba.post.ui.widgets.compose.SearchBox
import com.huanchengfly.tieba.post.ui.widgets.compose.SearchThreadList
import com.huanchengfly.tieba.post.ui.widgets.compose.choiceMenuItems
import com.huanchengfly.tieba.post.ui.widgets.compose.clickMenu
import com.huanchengfly.tieba.post.ui.widgets.compose.paddedStickyHeader
import com.huanchengfly.tieba.post.ui.widgets.compose.picker.ListSinglePicker
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberMenuTriggerState
import com.huanchengfly.tieba.post.ui.widgets.compose.states.StateScreen
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun SearchHistoryList(
    searchHistories: ImmutableList<SearchHistoryItem>,
    onSearchHistoryClick: (SearchHistoryItem) -> Unit,
    expanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    onDelete: (SearchHistoryItem) -> Unit = {},
    onClear: () -> Unit = {},
) {
    val hasItem = remember(searchHistories) {
        searchHistories.isNotEmpty()
    }
    val hasMore = remember(searchHistories) {
        searchHistories.size > 6
    }
    val showItem = remember(expanded, hasMore, searchHistories) {
        if (!expanded && hasMore) searchHistories.take(6) else searchHistories
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(id = R.string.title_search_history),
                modifier = Modifier
                    .weight(1f),
                style = MaterialTheme.typography.subtitle1
            )
            if (hasItem) {
                Text(
                    text = stringResource(id = R.string.button_clear_all),
                    modifier = Modifier.clickable(onClick = onClear),
                    style = MaterialTheme.typography.button
                )
            }
        }
        FlowRow(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .animateContentSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            showItem.fastForEach { searchHistory ->
                Box(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(100))
                        .combinedClickable(
                            onClick = { onSearchHistoryClick(searchHistory) },
                            onLongClick = { onDelete(searchHistory) }
                        )
                        .background(ExtendedTheme.colors.chip)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = searchHistory.keyword
                    )
                }
            }
        }
        if (hasMore) {
            Button(
                onClick = onToggleExpand,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    backgroundColor = Color.Transparent,
                    contentColor = ExtendedTheme.colors.text
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Symbols.ExpandLess else Symbols.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(
                            id = if (expanded) R.string.button_expand_less_history else R.string.button_expand_more_history
                        ),
                        style = MaterialTheme.typography.button,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        if (!hasItem) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.tip_empty),
                    color = ExtendedTheme.colors.textDisabled,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun ForumSearchPostPage(
    key: ForumSearchPostKey,
    navigator: Navigator = LocalNavigator.current,
    viewModel: ForumSearchPostViewModel = hiltViewModel<ForumSearchPostViewModel, ForumSearchPostViewModel.Factory> { it.create(key) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProvideNavigator(navigator = navigator) {
        ForumSearchPostScreen(
            state = state,
            onBack = { navigator.navigateUp() },
            onSearch = viewModel::search,
            onRefresh = viewModel::refresh,
            onLoadMore = viewModel::loadMore,
            onSetOrder = viewModel::setOrder,
            onSetOnlyThreads = viewModel::setOnlyThreads,
            onForget = viewModel::forget,
            onClearHistory = viewModel::clearHistory,
            onOpen = { post ->
                when {
                    post.quoted != null -> navigator.navigate(
                        SubPostsKey(threadId = post.threadId, subPostId = post.subPostId, loadFromSubPost = true)
                    )

                    post.thread != null -> navigator.navigate(
                        ThreadKey(threadId = post.threadId, postId = post.postId, scrollToReply = true)
                    )

                    else -> navigator.navigate(ThreadKey(threadId = post.threadId))
                }
            },
            onOpenUser = { author -> author.id.takeIf { it != 0L }?.let { navigator.navigate(UserProfileKey(it)) } },
            onOpenQuoted = { navigator.navigate(ThreadKey(threadId = it.threadId, postId = it.postId, scrollToReply = true)) },
            onOpenThread = { navigator.navigate(ThreadKey(threadId = it.threadId, scrollToReply = true)) },
        )
    }
}

/** Searching one forum: its history until the user searches, then the threads and replies found. */
@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun ForumSearchPostScreen(
    state: ForumSearchPostState,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onSetOrder: (ForumSearchOrder) -> Unit,
    onSetOnlyThreads: (Boolean) -> Unit,
    onForget: (SearchHistoryItem) -> Unit,
    onClearHistory: () -> Unit,
    onOpen: (SearchedPost) -> Unit,
    onOpenUser: (SearchAuthor) -> Unit,
    onOpenQuoted: (QuotedPost) -> Unit,
    onOpenThread: (QuotedPost) -> Unit,
) {
    val forumName = state.forumName
    val list = state.list
    val isRefreshing = list.refresh == LoadStatus.Loading
    val error = (list.refresh as? LoadStatus.Error)?.error
    var inputKeyword by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.keyword) {
        if (state.keyword.isNotEmpty()) inputKeyword = state.keyword
    }

    val pullRefreshState = rememberPullRefreshState(refreshing = isRefreshing, onRefresh = onRefresh)
    val lazyListState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val sortTypeMapping = mapOf(
        ForumSearchOrder.Newest to stringResource(R.string.title_search_post_sort_by_time),
        ForumSearchOrder.Relevant to stringResource(R.string.title_search_post_sort_by_relevant),
    )
    val filterTypeMapping = mapOf(
        false to stringResource(R.string.title_search_filter_all),
        true to stringResource(R.string.title_search_filter_only_thread),
    )

    GlassScaffold(
        topBar = {
            GlassTopBar(
                title = {
                    // The bar styles its title slot as a title; the field keeps body text.
                    ProvideTextStyle(MaterialTheme.typography.body1) {
                        SearchBox(
                            keyword = inputKeyword,
                            onKeywordChange = { inputKeyword = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .padding(end = 12.dp),
                            onKeywordSubmit = {
                                focusRequester.freeFocus()
                                keyboardController?.hide()
                                onSearch(it)
                            },
                            placeholder = {
                                Text(
                                    text = stringResource(
                                        id = R.string.hint_search_in_ba,
                                        forumName
                                    ),
                                    color = ExtendedTheme.colors.onSearchField.copy(alpha = ContentAlpha.medium)
                                )
                            },
                            prependIcon = {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(100))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = PressFeedback.Overlay,
                                            role = Role.Button,
                                            onClick = onBack
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Symbols.ArrowBack,
                                        contentDescription = stringResource(id = R.string.button_back)
                                    )
                                }
                            },
                            focusRequester = focusRequester,
                            shape = RoundedCornerShape(6.dp)
                        )
                    }
                },
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            if (state.keyword.isNotEmpty()) {
                StateScreen(
                    modifier = Modifier.fillMaxSize(),
                    isEmpty = list.items.isEmpty(),
                    isError = error != null,
                    isLoading = isRefreshing,
                    onReload = onRefresh,
                    errorScreen = { ErrorScreen(error = error) }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pullRefresh(pullRefreshState)
                    ) {
                        LoadMoreLayout(
                            isLoading = list.append == LoadStatus.Loading,
                            onLoadMore = onLoadMore,
                            loadEnd = !list.hasMore,
                            lazyListState = lazyListState,
                        ) {
                            SearchThreadList(
                                data = list.items,
                                lazyListState = lazyListState,
                                onItemClick = onOpen,
                                onItemUserClick = onOpenUser,
                                onItemForumClick = {},
                                onQuotePostClick = onOpenQuoted,
                                onMainPostClick = onOpenThread,
                                hideForum = true,
                                searchKeyword = state.keyword,
                                contentPadding = paddingValues,
                            ) {
                                paddedStickyHeader(lazyListState, key = "Sort&Filter") {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .background(ExtendedTheme.colors.background)
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = {}
                                            )
                                    ) {
                                        val sortMenu = rememberMenuTriggerState()
                                        val rotate by animateFloatAsState(
                                            targetValue = if (sortMenu.open) 180f else 0f,
                                            label = "ArrowIndicatorRotate"
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickMenu(
                                                items = { choiceMenuItems(sortTypeMapping.toList(), state.order, onSetOrder) },
                                                indication = null,
                                                state = sortMenu,
                                            ),
                                        ) {
                                            Text(
                                                text = sortTypeMapping[state.order]
                                                    ?: "",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Icon(
                                                imageVector = Symbols.ArrowDropDown,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .rotate(rotate)
                                            )
                                        }
                                        Spacer(modifier = Modifier.weight(1f))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.height(IntrinsicSize.Min)
                                        ) {
                                            filterTypeMapping.keys.map<Boolean, @Composable () -> Unit> { type ->
                                                {
                                                    Text(
                                                        text = filterTypeMapping[type] ?: "",
                                                        fontSize = 13.sp,
                                                        fontWeight = if (type == state.onlyThreads) {
                                                            FontWeight.Bold
                                                        } else {
                                                            FontWeight.Normal
                                                        },
                                                        modifier = Modifier.clickable(
                                                            interactionSource = remember { MutableInteractionSource() },
                                                            indication = null,
                                                            role = Role.RadioButton,
                                                            onClick = { onSetOnlyThreads(type) }
                                                        )
                                                    )
                                                }
                                            }.forEachIndexed { index, composable ->
                                                composable()
                                                if (index < filterTypeMapping.size - 1) {
                                                    HorizontalDivider(
                                                        modifier = Modifier.padding(
                                                            horizontal = 8.dp
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        PullRefreshIndicator(
                            refreshing = isRefreshing,
                            state = pullRefreshState,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = paddingValues.calculateTopPadding()),
                            backgroundColor = ExtendedTheme.colors.refreshIndicator,
                            contentColor = ExtendedTheme.colors.emphasis,
                        )
                    }
                }
            } else {
                SearchHistoryList(
                    searchHistories = state.history,
                    onSearchHistoryClick = { onSearch(it.keyword) },
                    onDelete = onForget,
                    onClear = onClearHistory
                )
            }
        }
    }
}
