package com.huanchengfly.tieba.post.ui.page.forum

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
import androidx.compose.foundation.lazy.LazyRow
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
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.api.models.protos.abstractText
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.arch.PagerState
import com.huanchengfly.tieba.post.model.ForumTab
import com.huanchengfly.tieba.post.model.GoodClass
import com.huanchengfly.tieba.post.repository.EmoticonRegistry
import com.huanchengfly.tieba.post.repository.toThreadSummary
import com.huanchengfly.tieba.post.ui.common.windowsizeclass.LocalWindowSizeClass
import com.huanchengfly.tieba.post.ui.common.windowsizeclass.WindowWidthSizeClass
import com.huanchengfly.tieba.post.ui.page.main.explore.rememberThreadPeeker
import com.huanchengfly.tieba.post.ui.widgets.compose.BlockTip
import com.huanchengfly.tieba.post.ui.widgets.compose.BlockableContent
import com.huanchengfly.tieba.post.ui.widgets.compose.Chip
import com.huanchengfly.tieba.post.ui.widgets.compose.FeedCard
import com.huanchengfly.tieba.post.ui.widgets.compose.LoadMoreLayout
import com.huanchengfly.tieba.post.ui.widgets.compose.MyLazyColumn
import com.huanchengfly.tieba.post.ui.widgets.compose.VerticalDivider
import kotlinx.collections.immutable.ImmutableList

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
    goodClasses: ImmutableList<GoodClass>? = null,
    goodClassId: Int = 0,
) {
    val itemFraction = when (LocalWindowSizeClass.current.widthSizeClass) {
        WindowWidthSizeClass.Expanded -> 0.5f
        else -> 1f
    }
    val items = list.items
    val peeker = rememberThreadPeeker()
    Box(modifier = modifier.fillMaxSize()) {
        LoadMoreLayout(
            isLoading = list.append == LoadStatus.Loading,
            onLoadMore = { onEvent(ForumEvent.LoadMore) },
            loadEnd = !list.hasMore,
            lazyListState = listState,
            isEmpty = items.isEmpty(),
        ) {
            MyLazyColumn(
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = contentPadding,
            ) {
                if (!goodClasses.isNullOrEmpty()) {
                    item(key = "GoodClasses") {
                        GoodClassChips(goodClasses, goodClassId) { onEvent(ForumEvent.SetGoodClass(it)) }
                    }
                }
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
                    key = { _, item -> item.thread.get { id } },
                    contentType = { _, item ->
                        val thread = item.thread.get()
                        when {
                            thread.isTop == 1 -> ItemType.Top
                            thread.media.size == 1 -> ItemType.SingleMedia
                            thread.media.isNotEmpty() -> ItemType.MultiMedia
                            thread.videoInfo != null -> ItemType.Video
                            else -> ItemType.PlainText
                        }
                    }
                ) { index, item ->
                    BlockableContent(
                        blocked = item.blocked,
                        blockedTip = { BlockTip(text = { Text(text = stringResource(id = R.string.tip_blocked_thread)) }) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 16.dp),
                    ) {
                        val thread = item.thread.get()
                        Column(modifier = Modifier.fillMaxWidth(itemFraction)) {
                            if (thread.isTop == 1) {
                                TopThreadItem(
                                    title = thread.title.takeUnless { it.isBlank() } ?: thread.abstractText,
                                    onClick = { onEvent(ForumEvent.OpenThread(thread)) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                if (index > 0) {
                                    if (items[index - 1].thread.get { isTop } == 1) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    VerticalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                }
                                FeedCard(
                                    item = item.thread,
                                    onClick = { onEvent(ForumEvent.OpenThread(it)) },
                                    onClickReply = { onEvent(ForumEvent.OpenReplies(it)) },
                                    onAgree = { onEvent(ForumEvent.Agree(tab, it.id)) },
                                    onClickOriginThread = { onEvent(ForumEvent.OpenOriginThread(it)) },
                                    onClickUser = { onEvent(ForumEvent.OpenUser(it.id)) },
                                    showForum = false,
                                    onLongPress = peeker?.let { { thread, from, at -> it.peek(thread.toThreadSummary(EmoticonRegistry { _, _ -> }), from, at) } },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoodClassChips(classes: ImmutableList<GoodClass>, selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = classes, key = { "${it.id}_${it.name}" }) {
            Chip(text = it.name, invertColor = selected == it.id, onClick = { onSelect(it.id) })
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
        val accent = forumAccentOrPrimary()
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
