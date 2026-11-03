package oolong.client.ui.page.history.list

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
import oolong.client.R
import oolong.client.model.HistoryEntry
import oolong.client.ui.common.relativeTime
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.page.history.HistoryList
import oolong.client.ui.widgets.compose.Avatar
import oolong.client.ui.widgets.compose.Chip
import oolong.client.ui.widgets.compose.ContextMenuItem
import oolong.client.ui.widgets.compose.LoadMoreEffect
import oolong.client.ui.widgets.compose.MyLazyColumn
import oolong.client.ui.widgets.compose.Sizes
import oolong.client.ui.widgets.compose.UserHeader
import oolong.client.ui.widgets.compose.longPressMenu
import oolong.client.ui.widgets.compose.paddedStickyHeader

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
        LoadMoreEffect(lazyListState, enabled = list.hasMore, onLoadMore = onLoadMore)
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
    val delete = stringResource(id = R.string.title_delete)
    Box(
        modifier = Modifier.longPressMenu(
            items = { listOf(ContextMenuItem(Symbols.Delete, delete, onClick = onDelete)) },
            onClick = onClick,
        ),
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
                    text = relativeTime(info.time),
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
