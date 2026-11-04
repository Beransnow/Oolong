package oolong.client.ui.page.settings.privacy

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
import oolong.client.repository.BlackSettings
import oolong.client.repository.BlackedUser
import oolong.client.repository.UserRepository
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Spacing
import oolong.client.ui.common.theme.compose.TextStyles
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.navigation.UserProfileKey
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
import oolong.client.utils.StringUtil
import javax.inject.Inject

/** The signed-in user's blacklist, a page at a time. */
@HiltViewModel
class BlackListViewModel @Inject constructor(
    private val repository: UserRepository,
    private val messages: MessageController,
) : ViewModel() {
    private val users = pager(initialKey = 1, itemKey = { it: BlackedUser -> it.user.id }) { page -> repository.blackList(page) }
    private val actions = Actions(viewModelScope)

    val state: StateFlow<PagerState<BlackedUser>> = users.state

    init {
        users.refresh()
    }

    fun refresh() = users.refresh()

    fun loadMore() = users.loadMore()

    /** Lifts every bar on [blacked] and takes them off the list. */
    fun remove(blacked: BlackedUser) = actions.launch("remove-${blacked.user.id}") {
        runCatching { repository.setBlack(blacked.user.id, BlackSettings(follow = false, interact = false, chat = false)) }
            .onSuccess { users.update { list -> list.filterNot { it.user.id == blacked.user.id } } }
            .onFailure { messages.showError(R.string.toast_remove_black_failed, it) }
    }
}

@Composable
fun BlackListPage(
    navigator: Navigator = LocalNavigator.current,
    viewModel: BlackListViewModel = hiltViewModel(),
) {
    val list by viewModel.state.collectAsStateWithLifecycle()
    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = R.string.title_black_list)) },
                navigationIcon = { BackNavigationIcon(onBackPressed = { navigator.navigateUp() }) },
            )
        },
    ) { padding ->
        val error = (list.refresh as? LoadStatus.Error)?.error
        StateScreen(
            isEmpty = list.items.isEmpty(),
            isError = error != null,
            isLoading = list.refresh == LoadStatus.Loading,
            onReload = viewModel::refresh,
            errorScreen = { ErrorScreen(error = error) },
            emptyScreen = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(id = R.string.black_list_empty),
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
                    items(list.items, key = { it.user.id }) { blacked ->
                        BlackedRow(
                            blacked = blacked,
                            onOpen = { navigator.navigate(UserProfileKey(blacked.user.id)) },
                            onRemove = { viewModel.remove(blacked) },
                        )
                    }
                    loadMoreFooter(list.append, list.hasMore, viewModel::loadMore)
                }
            }
        }
    }
}

/** One user: avatar, name, what they are barred from, and lifting it. */
@Composable
private fun BlackedRow(blacked: BlackedUser, onOpen: () -> Unit, onRemove: () -> Unit) {
    val settings = blacked.settings
    val bars = listOfNotNull(
        stringResource(id = R.string.black_list_follow).takeIf { settings.follow },
        stringResource(id = R.string.black_list_interact).takeIf { settings.interact },
        stringResource(id = R.string.black_list_chat).takeIf { settings.chat },
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = Spacing.md, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Avatar(data = StringUtil.getAvatarUrl(blacked.user.portrait), size = Sizes.Medium, contentDescription = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = blacked.user.displayName,
                style = TextStyles.nameCompact,
                color = ExtendedTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (bars.isNotEmpty()) {
                Text(
                    text = bars.joinToString(" · "),
                    style = TextStyles.caption,
                    color = ExtendedTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        SecondaryButton(onClick = onRemove, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
            Text(text = stringResource(id = R.string.btn_remove_black), style = TextStyles.label)
        }
    }
}
