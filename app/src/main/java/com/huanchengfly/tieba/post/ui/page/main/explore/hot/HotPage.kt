package com.huanchengfly.tieba.post.ui.page.main.explore.hot

import android.graphics.Typeface
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.GlobalEvent
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.arch.onGlobalEvent
import com.huanchengfly.tieba.post.model.HotCategory
import com.huanchengfly.tieba.post.model.HotTopic
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.OrangeA700
import com.huanchengfly.tieba.post.ui.common.theme.compose.RedA700
import com.huanchengfly.tieba.post.ui.common.theme.compose.White
import com.huanchengfly.tieba.post.ui.common.theme.compose.Yellow
import com.huanchengfly.tieba.post.ui.common.theme.compose.pullRefreshIndicator
import com.huanchengfly.tieba.post.ui.navigation.HotTopicListKey
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.page.main.explore.FeedThreadEvents
import com.huanchengfly.tieba.post.ui.page.main.explore.rememberFeedThreadEvents
import com.huanchengfly.tieba.post.ui.widgets.compose.BlockTip
import com.huanchengfly.tieba.post.ui.widgets.compose.BlockableContent
import com.huanchengfly.tieba.post.ui.widgets.compose.Container
import com.huanchengfly.tieba.post.ui.widgets.compose.FeedCard
import com.huanchengfly.tieba.post.ui.widgets.compose.MyLazyColumn
import com.huanchengfly.tieba.post.ui.widgets.compose.ProvideContentColor
import com.huanchengfly.tieba.post.ui.widgets.compose.VerticalDivider
import com.huanchengfly.tieba.post.ui.widgets.compose.VerticalGrid
import com.huanchengfly.tieba.post.ui.widgets.compose.items
import com.huanchengfly.tieba.post.ui.widgets.compose.itemsIndexed
import com.huanchengfly.tieba.post.ui.widgets.compose.placeholder.placeholder
import com.huanchengfly.tieba.post.utils.StringUtil.getShortNumString

@Composable
fun HotPage(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: HotViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.open() }
    onGlobalEvent<GlobalEvent.Refresh>(filter = { it.key == "hot" }) { viewModel.refresh() }
    val navigator = LocalNavigator.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    HotScreen(
        state = state,
        events = rememberFeedThreadEvents(onAgree = viewModel::agree),
        onRefresh = viewModel::refresh,
        onSelectCategory = viewModel::selectCategory,
        onOpenTopics = { navigator.navigate(HotTopicListKey) },
        contentPadding = contentPadding,
    )
}

/** The hot topics and the hot threads ranking, which pull down to refresh. */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HotScreen(
    state: HotState,
    events: FeedThreadEvents,
    onRefresh: () -> Unit,
    onSelectCategory: (code: String) -> Unit,
    onOpenTopics: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val isLoading = state.load == LoadStatus.Loading
    val topicList = state.topics
    val threadList = state.threads
    val tabList = state.categories
    val currentTabCode = state.category
    val isLoadingThreadList = state.switching
    val pullRefreshState = rememberPullRefreshState(refreshing = isLoading, onRefresh = onRefresh)
    Box(modifier = Modifier.pullRefresh(pullRefreshState)) {
        MyLazyColumn(
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            if (topicList.isNotEmpty()) {
                item(key = "TopicHeader") {
                    Container {
                        Box(
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .padding(horizontal = 16.dp)
                        ) { ChipHeader(text = stringResource(id = R.string.hot_topic_rank)) }
                    }
                }
                item(key = "TopicList") {
                    Container {
                        VerticalGrid(
                            column = 2,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(
                                items = topicList,
                            ) { index, item ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        fontWeight = FontWeight.Bold,
                                        color = when (index) {
                                            0 -> RedA700
                                            1 -> OrangeA700
                                            2 -> Yellow
                                            else -> MaterialTheme.colors.onBackground.copy(
                                                ContentAlpha.medium
                                            )
                                        },
                                        fontFamily = FontFamily(
                                            Typeface.createFromAsset(
                                                LocalContext.current.assets,
                                                "bebas.ttf"
                                            )
                                        ),
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                    Text(
                                        text = item.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    when (item.tag) {
                                        HotTopic.Tag.Hot -> Text(
                                            text = stringResource(id = R.string.topic_tag_hot),
                                            fontSize = 10.sp,
                                            color = White,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(RedA700)
                                                .padding(vertical = 2.dp, horizontal = 4.dp)
                                        )

                                        HotTopic.Tag.New -> Text(
                                            text = stringResource(id = R.string.topic_tag_new),
                                            fontSize = 10.sp,
                                            color = White,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(OrangeA700)
                                                .padding(vertical = 2.dp, horizontal = 4.dp)
                                        )
                                        HotTopic.Tag.None -> {}
                                    }
                                }
                            }
                            item {
                                ProvideContentColor(color = ExtendedTheme.colors.primary) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(onClick = onOpenTopics)
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = stringResource(id = R.string.tip_more_topic),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowRight,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item(key = "TopicDivider") {
                    Container {
                        VerticalDivider(
                            modifier = Modifier
                                .padding(top = 16.dp, bottom = 8.dp)
                                .padding(horizontal = 16.dp),
                            thickness = 2.dp
                        )
                    }
                }
            }
            if (threadList.isNotEmpty()) {
                if (tabList.isNotEmpty()) {
                    item(key = "ThreadTabs") {
                        Container {
                            VerticalGrid(
                                column = 5,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ExtendedTheme.colors.background)
                                    .padding(vertical = 8.dp)
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                item {
                                    ThreadListTab(
                                        text = stringResource(id = R.string.tab_all_hot_thread),
                                        selected = currentTabCode == HotCategory.ALL,
                                        onSelected = { onSelectCategory(HotCategory.ALL) }
                                    )
                                }
                                items(tabList) {
                                    ThreadListTab(
                                        text = it.name,
                                        selected = currentTabCode == it.code,
                                        onSelected = { onSelectCategory(it.code) }
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "ThreadListTip") {
                    Container(
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.hot_thread_rank_rule),
                            color = ExtendedTheme.colors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                }
                if (isLoadingThreadList) {
                    items(10) { ThreadListItemPlaceholder() }
                } else {
                    itemsIndexed(
                        items = threadList,
                        key = { _, item -> "Thread_${item.thread.get { threadId }}" }
                    ) { index, (item, blocked) ->
                        Container {
                            BlockableContent(
                                blocked = blocked,
                                blockedTip = { BlockTip(text = { Text(text = stringResource(id = R.string.tip_blocked_thread)) }) },
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                            ) {
                                FeedCard(
                                    item = item,
                                    onClick = events.onOpen,
                                    onClickReply = events.onOpenReplies,
                                    onAgree = events.onAgree,
                                    onClickForum = events.onOpenForum,
                                    onClickUser = events.onOpenUser,
                                    onClickOriginThread = events.onOpenOrigin,
                                ) {
                                    HotRank(index = index, hotNum = item.get { hotNum })
                                }
                            }
                        }
                    }
                }
            }
        }

        PullRefreshIndicator(
            refreshing = isLoading,
            state = pullRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = contentPadding.calculateTopPadding()),
            backgroundColor = ExtendedTheme.colors.pullRefreshIndicator,
            contentColor = ExtendedTheme.colors.primary,
        )
    }
}

@Composable
private fun HotRank(index: Int, hotNum: Int) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val color = when (index) {
            0 -> RedA700
            1 -> OrangeA700
            2 -> Yellow
            else -> MaterialTheme.colors.onBackground.copy(ContentAlpha.medium)
        }
        Text(
            text = "${index + 1}",
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Text(
            text = stringResource(id = R.string.hot_num, hotNum.getShortNumString()),
            style = MaterialTheme.typography.caption,
            color = color
        )
    }
}

@Composable
private fun ThreadListItemPlaceholder() {
    Row(modifier = Modifier.padding(all = 16.dp)) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "1",
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = ExtendedTheme.colors.background,
                modifier = Modifier
                    .padding(top = 3.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .wrapContentSize()
                    .placeholder(visible = true)
                    .padding(vertical = 1.dp, horizontal = 4.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "",
                    modifier = Modifier
                        .fillMaxWidth()
                        .placeholder(visible = true)
                )
                Text(
                    text = stringResource(id = R.string.hot_num, "666"),
                    style = MaterialTheme.typography.caption,
                    color = ExtendedTheme.colors.textSecondary,
                    modifier = Modifier.placeholder(visible = true)
                )
            }
        }
    }
}

@Composable
private fun ThreadListTab(
    text: String,
    selected: Boolean,
    onSelected: () -> Unit
) {
    val textColor by animateColorAsState(targetValue = if (selected) ExtendedTheme.colors.onAccent else ExtendedTheme.colors.onChip)
    val backgroundColor by animateColorAsState(targetValue = if (selected) ExtendedTheme.colors.primary else ExtendedTheme.colors.chip)
    Text(
        text = text,
        textAlign = TextAlign.Center,
        color = textColor,
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(100))
            .background(backgroundColor)
            .clickable(onClick = onSelected)
            .padding(vertical = 4.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold
    )
}


@Composable
private fun ChipHeader(
    text: String,
    invert: Boolean = false,
    modifier: Modifier = Modifier
) {
    Text(
        color = if (invert) MaterialTheme.colors.onSecondary else ExtendedTheme.colors.onChip,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(100))
            .then(modifier)
            .background(color = if (invert) MaterialTheme.colors.secondary else ExtendedTheme.colors.chip)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}