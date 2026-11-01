package com.huanchengfly.tieba.post.ui.page.search.thread

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.arch.PagerState
import com.huanchengfly.tieba.post.model.SearchAuthor
import com.huanchengfly.tieba.post.model.SearchedPost
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.widgets.compose.ErrorScreen
import com.huanchengfly.tieba.post.ui.widgets.compose.LoadMoreLayout
import com.huanchengfly.tieba.post.ui.widgets.compose.SearchThreadList
import com.huanchengfly.tieba.post.ui.widgets.compose.states.StateScreen

/** Threads and replies a search found, which pull down to search again and load more at their end. */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SearchThreadResults(
    list: PagerState<SearchedPost>,
    keyword: String,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (SearchedPost) -> Unit,
    onOpenUser: (SearchAuthor) -> Unit,
    onOpenForum: (forumName: String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val refreshing = list.refresh == LoadStatus.Loading
    val error = (list.refresh as? LoadStatus.Error)?.error
    StateScreen(
        modifier = Modifier.fillMaxSize(),
        isEmpty = list.items.isEmpty(),
        isError = error != null,
        isLoading = refreshing,
        onReload = onRefresh,
        errorScreen = { ErrorScreen(error = error) },
    ) {
        val pullRefreshState = rememberPullRefreshState(refreshing = refreshing, onRefresh = onRefresh)
        val lazyListState = rememberLazyListState()
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
                    onItemForumClick = onOpenForum,
                    searchKeyword = keyword,
                    contentPadding = contentPadding,
                )
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
}
