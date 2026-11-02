package com.huanchengfly.tieba.post.ui.page.hottopic.list

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.ContentAlpha
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.model.HotTopic
import com.huanchengfly.tieba.post.model.RankedTopic
import com.huanchengfly.tieba.post.ui.common.theme.compose.Corners
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.OrangeA700
import com.huanchengfly.tieba.post.ui.common.theme.compose.RedA700
import com.huanchengfly.tieba.post.ui.common.theme.compose.Shapes
import com.huanchengfly.tieba.post.ui.common.theme.compose.White
import com.huanchengfly.tieba.post.ui.common.theme.compose.Yellow
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBar
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBarTitle
import com.huanchengfly.tieba.post.ui.widgets.compose.MyLazyColumn
import com.huanchengfly.tieba.post.ui.widgets.compose.NetworkImage
import com.huanchengfly.tieba.post.ui.widgets.compose.Sizes
import com.huanchengfly.tieba.post.ui.widgets.compose.plus
import com.huanchengfly.tieba.post.utils.StringUtil.getShortNumString

@Composable
private fun TopicImage(
    index: Int,
    imageUri: String
) {
    val boxModifier = if (index < 3) {
        Modifier
            .fillMaxWidth()
            .aspectRatio(2.39f)
            .clip(Shapes.medium)
    } else {
        Modifier
            .size(Sizes.Medium)
            .aspectRatio(1f)
            .clip(Shapes.small)
    }
    Box(
        modifier = boxModifier
    ) {
        NetworkImage(
            imageUri = imageUri,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Text(
            text = "${index + 1}",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = ExtendedTheme.colors.background,
            fontFamily = FontFamily(
                Typeface.createFromAsset(
                    LocalContext.current.assets,
                    "bebas.ttf"
                )
            ),
            modifier = Modifier
                .background(
                    when (index) {
                        0 -> RedA700
                        1 -> OrangeA700
                        2 -> Yellow
                        else -> ExtendedTheme.colors.text.copy(ContentAlpha.medium)
                    }
                )
                .padding(4.dp)
        )
    }
}

@Composable
private fun TopicBody(
    index: Int,
    item: RankedTopic
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = item.name, style = MaterialTheme.typography.subtitle1)
            when (item.tag) {
                HotTopic.Tag.Hot -> Text(
                    text = stringResource(id = R.string.topic_tag_hot),
                    fontSize = 10.sp,
                    color = White,
                    modifier = Modifier
                        .clip(Corners.xs)
                        .background(RedA700)
                        .padding(vertical = 2.dp, horizontal = 4.dp)
                )

                HotTopic.Tag.New -> Text(
                    text = stringResource(id = R.string.topic_tag_new),
                    fontSize = 10.sp,
                    color = White,
                    modifier = Modifier
                        .clip(Corners.xs)
                        .background(OrangeA700)
                        .padding(vertical = 2.dp, horizontal = 4.dp)
                )

                HotTopic.Tag.None -> {}
            }
        }
        Text(
            text = item.description,
            maxLines = if (index < 3) 3 else 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.body2
        )
        Text(
            text = stringResource(id = R.string.hot_num, item.discussCount.getShortNumString()),
            style = MaterialTheme.typography.caption,
            color = ExtendedTheme.colors.textSecondary
        )
    }
}

@Composable
fun HotTopicListPage(
    navigator: Navigator = LocalNavigator.current,
    viewModel: HotTopicListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HotTopicListScreen(state = state, onBack = { navigator.navigateUp() }, onRefresh = viewModel::refresh)
}

/** The full hot topics ranking, the top three with large pictures; pulls down to refresh. */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HotTopicListScreen(
    state: HotTopicListState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val isRefreshing = state.load == LoadStatus.Loading
    val topicList = state.topics
    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = R.string.title_hot_message_list)) },
                navigationIcon = {
                    BackNavigationIcon(onBackPressed = onBack)
                }
            )
        },
    ) { contentPaddings ->
        val pullRefreshState = rememberPullRefreshState(
            refreshing = isRefreshing,
            onRefresh = onRefresh
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(pullRefreshState)
        ) {
            MyLazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = contentPaddings.plus(PaddingValues(16.dp))
            ) {
                itemsIndexed(
                    items = topicList,
                    key = { _, item -> item.id },
                ) { index, item ->
                    if (index < 3) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TopicImage(index = index, imageUri = item.image)
                            TopicBody(index = index, item = item)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TopicImage(index = index, imageUri = item.image)
                            TopicBody(index = index, item = item)
                        }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = contentPaddings.calculateTopPadding()),
                backgroundColor = ExtendedTheme.colors.refreshIndicator,
                contentColor = ExtendedTheme.colors.emphasis,
            )
        }
    }
}
