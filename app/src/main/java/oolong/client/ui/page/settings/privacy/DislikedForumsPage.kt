package oolong.client.ui.page.settings.block.disliked

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import oolong.client.R
import oolong.client.arch.Actions
import oolong.client.arch.LoadStatus
import oolong.client.arch.MessageController
import oolong.client.arch.PagerState
import oolong.client.arch.pager
import oolong.client.model.DislikedForum
import oolong.client.repository.DislikedForumRepository
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Spacing
import oolong.client.ui.common.theme.compose.TextStyles
import oolong.client.ui.navigation.ForumKey
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.widgets.compose.Avatar
import oolong.client.ui.widgets.compose.BackNavigationIcon
import oolong.client.ui.widgets.compose.EndPull
import oolong.client.ui.widgets.compose.ErrorScreen
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.GlassTopBarTitle
import oolong.client.ui.widgets.compose.LoadMoreEffect
import oolong.client.ui.widgets.compose.MyLazyColumn
import oolong.client.ui.widgets.compose.PullRefresh
import oolong.client.ui.widgets.compose.SecondaryButton
import oolong.client.ui.widgets.compose.Sizes
import oolong.client.ui.widgets.compose.loadMoreFooter
import oolong.client.ui.widgets.compose.states.StateScreen
import javax.inject.Inject

/** The forums the user keeps out of their recommendations, a page at a time. */
@HiltViewModel
class DislikedForumsViewModel @Inject constructor(
    private val repository: DislikedForumRepository,
    private val messages: MessageController,
) : ViewModel() {
    private val forums = pager(initialKey = 1, itemKey = { it: DislikedForum -> it.id }) { page -> repository.forums(page) }
    private val actions = Actions(viewModelScope)

    val state: StateFlow<PagerState<DislikedForum>> = forums.state

    init {
        forums.refresh()
    }

    fun refresh() = forums.refresh()

    fun loadMore() = forums.loadMore()

    /** Lets [forum] back into the recommendations and takes it off the list. */
    fun cancel(forum: DislikedForum) = actions.launch("cancel-${forum.id}") {
        runCatching { repository.cancel(forum) }
            .onSuccess { forums.update { list -> list.filterNot { it.id == forum.id } } }
            .onFailure { messages.showError(R.string.toast_cancel_dislike_failed, it) }
    }
}

@Composable
fun DislikedForumsPage(
    navigator: Navigator = LocalNavigator.current,
    viewModel: DislikedForumsViewModel = hiltViewModel(),
) {
    val list by viewModel.state.collectAsStateWithLifecycle()
    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = R.string.title_disliked_forums)) },
                navigationIcon = { BackNavigationIcon(onBackPressed = { navigator.navigateUp() }) },
            )
        },
    ) { padding ->
        val error = (list.refresh as? LoadStatus.Error)?.error
        StateScreen(
            isEmpty = list.items.isEmpty(),
            isError = error != null,
            isLoading = list.refresh == LoadStatus.Loading || list.refresh == LoadStatus.Idle,
            onReload = viewModel::refresh,
            errorScreen = { ErrorScreen(error = error) },
            emptyScreen = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(id = R.string.disliked_forums_empty),
                        style = TextStyles.label,
                        color = ExtendedTheme.colors.textSecondary,
                    )
                }
            },
        ) {
            val lazyListState = rememberLazyListState()
            LoadMoreEffect(lazyListState, enabled = list.hasMore && list.append == LoadStatus.Idle, onLoadMore = viewModel::loadMore)
            PullRefresh(refreshing = false, onRefresh = {}, enabled = false, endPull = if (list.hasMore) EndPull.None else EndPull.Tease) {
                MyLazyColumn(state = lazyListState, contentPadding = padding) {
                    items(list.items, key = { it.id }) { forum ->
                        ForumRow(
                            forum = forum,
                            onOpen = { navigator.navigate(ForumKey(forum.name)) },
                            onCancel = { viewModel.cancel(forum) },
                        )
                    }
                    loadMoreFooter(list.append, list.hasMore, viewModel::loadMore)
                }
            }
        }
    }
}

@Composable
private fun ForumRow(forum: DislikedForum, onOpen: () -> Unit, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = Spacing.md, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Avatar(data = forum.avatar, size = Sizes.Medium, contentDescription = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(id = R.string.title_forum, forum.name),
                style = TextStyles.nameCompact,
                color = ExtendedTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (forum.slogan.isNotBlank()) {
                Text(
                    text = forum.slogan,
                    style = TextStyles.caption,
                    color = ExtendedTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        SecondaryButton(onClick = onCancel, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
            Text(text = stringResource(id = R.string.btn_cancel_dislike), style = TextStyles.label)
        }
    }
}
