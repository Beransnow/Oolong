package oolong.client.ui.page.forum

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import oolong.client.R
import oolong.client.arch.LoadStatus
import oolong.client.arch.PagerState
import oolong.client.model.ForumTab
import oolong.client.ui.common.windowsizeclass.LocalWindowSizeClass
import oolong.client.ui.common.windowsizeclass.WindowWidthSizeClass
import oolong.client.ui.page.main.explore.rememberThreadPeeker
import oolong.client.ui.widgets.compose.BlockTip
import oolong.client.ui.widgets.compose.BlockableContent
import oolong.client.ui.widgets.compose.FeedCard
import oolong.client.ui.widgets.compose.LoadMoreEffect
import oolong.client.ui.widgets.compose.MyLazyColumn
import oolong.client.ui.widgets.compose.VerticalDivider
import oolong.client.ui.widgets.compose.loadMoreFooter
import oolong.client.ui.widgets.compose.pageAccentOrPrimary

private enum class ItemType { Top, PlainText, SingleMedia, MultiMedia, Video }

/**
 * One tab's threads: the rules and the threads pinned to the top as single lines, then the
 * threads. The good threads tab starts with the classes to filter them by.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun ForumThreadList(
    tab: ForumTab,
    list: PagerState<ForumThreadItem>,
    onEvent: (ForumEvent) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    ruleTitle: String? = null,
) {
    val itemFraction = when (LocalWindowSizeClass.current.widthSizeClass) {
        WindowWidthSizeClass.Expanded -> 0.5f
        else -> 1f
    }
    val items = list.items
    val peeker = rememberThreadPeeker()
    Box(modifier = modifier.fillMaxSize()) {
        LoadMoreEffect(listState, enabled = list.hasMore && list.append == LoadStatus.Idle, onLoadMore = { onEvent(ForumEvent.LoadMore) })
        MyLazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = contentPadding,
        ) {
            if (!ruleTitle.isNullOrEmpty()) {
                item(key = "ForumRule") {
                    TopThreadItem(
                        title = ruleTitle,
                        onClick = { onEvent(ForumEvent.OpenRule) },
                        modifier = Modifier.fillMaxWidth(),
                        type = stringResource(id = R.string.desc_forum_rule)
                    )
                }
            }
            itemsIndexed(
                items = items,
                key = { _, item -> item.thread.id },
                contentType = { _, item ->
                    val thread = item.thread
                    when {
                        thread.isTop == true -> ItemType.Top
                        thread.images.size == 1 -> ItemType.SingleMedia
                        thread.images.isNotEmpty() -> ItemType.MultiMedia
                        thread.video != null -> ItemType.Video
                        else -> ItemType.PlainText
                    }
                }
            ) { index, item ->
                BlockableContent(
                    block = item.block,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                ) {
                    val thread = item.thread
                    Column(modifier = Modifier.fillMaxWidth(itemFraction)) {
                        if (thread.isTop == true) {
                            TopThreadItem(
                                title = thread.title.takeUnless { it.isBlank() } ?: thread.abstract,
                                onClick = { onEvent(ForumEvent.OpenThread(thread)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            if (index > 0) {
                                if (items[index - 1].thread.isTop == true) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                VerticalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                            FeedCard(
                                thread = item.thread,
                                onClick = { onEvent(ForumEvent.OpenThread(it)) },
                                onClickReply = { onEvent(ForumEvent.OpenReplies(it)) },
                                onAgree = { onEvent(ForumEvent.Agree(tab, it.id)) },
                                onClickShared = { onEvent(ForumEvent.OpenOriginThread(it)) },
                                onClickUser = { onEvent(ForumEvent.OpenUser(it.id)) },
                                showForum = false,
                                onLongPress = peeker?.let { { thread, from, at, origin -> it.peek(thread, from, at, origin) } },
                            )
                        }
                    }
                }
            }
            loadMoreFooter(list.append, list.hasMore, onRetry = { onEvent(ForumEvent.LoadMore) })
        }
    }
}

@Composable
private fun TopThreadItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    type: String = stringResource(id = R.string.content_top),
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val accent = pageAccentOrPrimary()
        Text(
            text = type,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            modifier = Modifier
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.subtitle2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            fontSize = 15.sp
        )
    }
}
