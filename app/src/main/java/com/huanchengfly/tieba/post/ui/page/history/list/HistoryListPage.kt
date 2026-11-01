package com.huanchengfly.tieba.post.ui.page.history.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.model.HistoryEntry
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.page.history.HistoryList
import com.huanchengfly.tieba.post.ui.widgets.compose.Avatar
import com.huanchengfly.tieba.post.ui.widgets.compose.Chip
import com.huanchengfly.tieba.post.ui.widgets.compose.LoadMoreLayout
import com.huanchengfly.tieba.post.ui.widgets.compose.LongClickMenu
import com.huanchengfly.tieba.post.ui.widgets.compose.MyLazyColumn
import com.huanchengfly.tieba.post.ui.widgets.compose.Sizes
import com.huanchengfly.tieba.post.ui.widgets.compose.UserHeader
import com.huanchengfly.tieba.post.ui.widgets.compose.paddedStickyHeader
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberMenuState
import com.huanchengfly.tieba.post.utils.DateTimeUtils

/** One tab of the history: today's visits, then the earlier ones; long pressing one offers to delete it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T : HistoryEntry> HistoryListContent(
    list: HistoryList<T>,
    onLoadMore: () -> Unit,
    onOpen: (T) -> Unit,
    onDelete: (T) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val lazyListState = rememberLazyListState()
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        LoadMoreLayout(
            isLoading = false,
            onLoadMore = onLoadMore,
            loadEnd = !list.hasMore,
            lazyListState = lazyListState,
            isEmpty = list.isEmpty
        ) {
            MyLazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                state = lazyListState,
                contentPadding = contentPadding,
            ) {
                if (list.today.isNotEmpty()) {
                    paddedStickyHeader(lazyListState, key = "TodayHistoryHeader") {
                        HistoryHeader(text = stringResource(id = R.string.title_history_today), invert = true)
                    }
                    items(items = list.today, key = { it.id }) { entry ->
                        HistoryItem(entry, onDelete = { onDelete(entry) }, onClick = { onOpen(entry) })
                    }
                }
                if (list.before.isNotEmpty()) {
                    paddedStickyHeader(lazyListState, key = "BeforeHistoryHeader") {
                        HistoryHeader(text = stringResource(id = R.string.title_history_before), invert = false)
                    }
                    items(items = list.before, key = { it.id }) { entry ->
                        HistoryItem(entry, onDelete = { onDelete(entry) }, onClick = { onOpen(entry) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryHeader(text: String, invert: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ExtendedTheme.colors.background)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Chip(text = text, invertColor = invert)
    }
}

@Composable
private fun HistoryItem(
    info: HistoryEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    val menuState = rememberMenuState()
    LongClickMenu(
        menuContent = {
            DropdownMenuItem(onClick = {
                onDelete()
                menuState.expanded = false
            }) {
                Text(text = stringResource(id = R.string.title_delete))
            }
        },
        menuState = menuState,
        onClick = onClick
    ) {
        Column(
            modifier = modifier
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UserHeader(
                avatar = {
                    Avatar(
                        data = info.avatar,
                        size = Sizes.Small,
                        contentDescription = null
                    )
                },
                name = {
                    Text(
                        text = when (info) {
                            is HistoryEntry.Thread -> info.authorName
                            is HistoryEntry.Forum -> info.title
                        }
                    )
                },
            ) {
                Text(
                    text = DateTimeUtils.getRelativeTimeString(
                        LocalContext.current,
                        info.time
                    ),
                    fontSize = 15.sp,
                    color = ExtendedTheme.colors.text,
                )
            }
            if (info is HistoryEntry.Thread) {
                Text(
                    text = info.title,
                    fontSize = 15.sp,
                    color = ExtendedTheme.colors.text,
                )
            }
        }
    }
}
