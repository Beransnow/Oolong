package oolong.client.ui.page.user.likeforum

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import oolong.client.arch.LoadStatus
import oolong.client.arch.PagerState
import oolong.client.model.LikedForum
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.navigation.ForumKey
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.widgets.compose.Avatar
import oolong.client.ui.widgets.compose.Container
import oolong.client.ui.widgets.compose.ErrorScreen
import oolong.client.ui.widgets.compose.LoadMoreLayout
import oolong.client.ui.widgets.compose.LocalChromePadding
import oolong.client.ui.widgets.compose.MyLazyColumn
import oolong.client.ui.widgets.compose.Sizes
import oolong.client.ui.widgets.compose.loadMoreFooter
import oolong.client.ui.widgets.compose.states.StateScreen

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun UserLikeForumPage(
    list: PagerState<LikedForum>,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    fluid: Boolean = false,
    enablePullRefresh: Boolean = false,
) {
    val navigator = LocalNavigator.current
    val forums = list.items
    val error = (list.refresh as? LoadStatus.Error)?.error
    StateScreen(
        isEmpty = forums.isEmpty(),
        isError = error != null,
        isLoading = list.refresh == LoadStatus.Loading || list.refresh == LoadStatus.Idle,
        onReload = onRefresh,
        errorScreen = { ErrorScreen(error = error) },
    ) {
        val pullRefreshState = rememberPullRefreshState(
            refreshing = list.refresh == LoadStatus.Loading,
            onRefresh = onRefresh
        )

        val lazyListState = rememberLazyListState()

        val pullRefreshModifier =
            if (enablePullRefresh) Modifier.pullRefresh(pullRefreshState) else Modifier

        Box(modifier = pullRefreshModifier) {
            LoadMoreLayout(
                isLoading = list.append == LoadStatus.Loading,
                onLoadMore = onLoadMore,
                loadEnd = !list.hasMore,
                lazyListState = lazyListState
            ) {
                UserLikeForumList(
                    data = forums,
                    fluid = fluid,
                    onClickForum = { navigator.navigate(ForumKey(it.name)) },
                    lazyListState = lazyListState,
                    footer = { loadMoreFooter(list.append, onLoadMore) },
                )
            }

            PullRefreshIndicator(
                refreshing = list.refresh == LoadStatus.Loading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = ExtendedTheme.colors.refreshIndicator,
                contentColor = ExtendedTheme.colors.emphasis,
            )
        }
    }
}

@Composable
private fun UserLikeForumList(
    data: ImmutableList<LikedForum>,
    onClickForum: (LikedForum) -> Unit,
    fluid: Boolean = false,
    lazyListState: LazyListState = rememberLazyListState(),
    footer: LazyListScope.() -> Unit = {},
) {
    MyLazyColumn(state = lazyListState, contentPadding = LocalChromePadding.current) {
        items(
            items = data,
            key = { it.id }
        ) {
            Container(fluid = fluid) {
                UserLikeForumItem(
                    item = it,
                    onClick = {
                        onClickForum(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        footer()
    }
}

@Composable
private fun UserLikeForumItem(
    item: LikedForum,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Avatar(
            data = item.avatar,
            size = Sizes.Medium,
            contentDescription = null
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = item.name, style = MaterialTheme.typography.subtitle1)
            item.slogan.takeUnless { it.isEmpty() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.body2,
                    color = ExtendedTheme.colors.textSecondary
                )
            }
        }
    }
}
