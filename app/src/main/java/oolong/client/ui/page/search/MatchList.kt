package oolong.client.ui.page.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oolong.client.arch.LoadStatus
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.widgets.compose.Chip
import oolong.client.ui.widgets.compose.ErrorScreen
import oolong.client.ui.widgets.compose.MyLazyColumn
import oolong.client.ui.widgets.compose.paddedStickyHeader
import oolong.client.ui.widgets.compose.states.StateScreen

/**
 * Forums or users found by a search: the exact match under its own header, then the rest. Pulls
 * down to search again.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun <T> MatchList(
    state: MatchesState<T>,
    exactTitle: String,
    fuzzyTitle: String,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemContent: @Composable (T) -> Unit,
) {
    val (exact, fuzzy) = state.matches
    val refreshing = state.load == LoadStatus.Loading
    val error = (state.load as? LoadStatus.Error)?.error
    StateScreen(
        modifier = Modifier.fillMaxSize(),
        isEmpty = exact == null && fuzzy.isEmpty(),
        isError = error != null,
        isLoading = refreshing,
        onReload = onRefresh,
        errorScreen = { ErrorScreen(error = error) },
    ) {
        val pullRefreshState = rememberPullRefreshState(refreshing = refreshing, onRefresh = onRefresh)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(pullRefreshState)
        ) {
            val listState = rememberLazyListState()
            MyLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = contentPadding,
            ) {
                if (exact != null) {
                    paddedStickyHeader(listState, key = "ExactMatchHeader") { MatchHeader(exactTitle, invert = true) }
                    item(key = "ExactMatch") { itemContent(exact) }
                }
                if (fuzzy.isNotEmpty()) {
                    paddedStickyHeader(listState, key = "FuzzyMatchHeader") { MatchHeader(fuzzyTitle, invert = false) }
                    // The server may list a match twice, so items go without keys
                    items(fuzzy) { itemContent(it) }
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
}

@Composable
private fun MatchHeader(text: String, invert: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ExtendedTheme.colors.background)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Chip(text = text, invertColor = invert)
    }
}
