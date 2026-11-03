package oolong.client.ui.page.user.follows

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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import oolong.client.R
import oolong.client.arch.LoadStatus
import oolong.client.arch.PagerState
import oolong.client.arch.pager
import oolong.client.model.ListedUser
import oolong.client.repository.UserRepository
import oolong.client.ui.common.navigation.pageHero
import oolong.client.ui.common.navigation.rememberPageHero
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Spacing
import oolong.client.ui.common.theme.compose.TextStyles
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.navigation.UserFollowsKey
import oolong.client.ui.navigation.UserProfileKey
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.widgets.compose.Avatar
import oolong.client.ui.widgets.compose.BackNavigationIcon
import oolong.client.ui.widgets.compose.ErrorScreen
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.GlassTopBarTitle
import oolong.client.ui.widgets.compose.LoadMoreLayout
import oolong.client.ui.widgets.compose.MyLazyColumn
import oolong.client.ui.widgets.compose.SegmentedControl
import oolong.client.ui.widgets.compose.Sizes
import oolong.client.ui.widgets.compose.loadMoreFooter
import oolong.client.ui.widgets.compose.states.StateScreen
import oolong.client.utils.StringUtil

@Immutable
data class UserFollowsState(
    val fans: Boolean,
    val follows: PagerState<ListedUser> = PagerState(),
    val fanList: PagerState<ListedUser> = PagerState(),
) {
    val shown: PagerState<ListedUser> get() = if (fans) fanList else follows
}

/** Whom a user follows and who follows them, a page of each at a time. */
@HiltViewModel(assistedFactory = UserFollowsViewModel.Factory::class)
class UserFollowsViewModel @AssistedInject constructor(
    @Assisted key: UserFollowsKey,
    repository: UserRepository,
) : ViewModel() {
    private val uid = key.uid
    private val showingFans = MutableStateFlow(key.fans)

    private val followPager = pager(initialKey = 1, itemKey = { it: ListedUser -> it.user.id }) { page -> repository.follows(uid, page) }
    private val fanPager = pager(initialKey = 1, itemKey = { it: ListedUser -> it.user.id }) { page -> repository.fans(uid, page) }

    val state: StateFlow<UserFollowsState> = combine(showingFans, followPager.state, fanPager.state) { fans, follows, fanList ->
        UserFollowsState(fans = fans, follows = follows, fanList = fanList)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UserFollowsState(fans = key.fans))

    init {
        show(key.fans)
    }

    /** Shows the followers, or with [fans] false those followed, loading them the first time. */
    fun show(fans: Boolean) {
        showingFans.value = fans
        val pager = if (fans) fanPager else followPager
        if (pager.state.value.refresh == LoadStatus.Idle) pager.refresh()
    }

    fun refresh() = (if (showingFans.value) fanPager else followPager).refresh()

    fun loadMore() = (if (showingFans.value) fanPager else followPager).loadMore()

    @AssistedFactory
    interface Factory {
        fun create(key: UserFollowsKey): UserFollowsViewModel
    }
}

/** A user's follows and followers, switched between at the top; each opens to their profile. */
@Composable
fun UserFollowsPage(
    key: UserFollowsKey,
    navigator: Navigator = LocalNavigator.current,
    viewModel: UserFollowsViewModel = hiltViewModel<UserFollowsViewModel, UserFollowsViewModel.Factory> { it.create(key) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = key.name) },
                navigationIcon = { BackNavigationIcon(onBackPressed = { navigator.navigateUp() }) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            SegmentedControl(
                options = persistentListOf(stringResource(id = R.string.text_stat_follow), stringResource(id = R.string.text_stat_fans)),
                selected = if (state.fans) 1 else 0,
                onSelect = { viewModel.show(fans = it == 1) },
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs).fillMaxWidth(),
            )
            UserList(
                list = state.shown,
                empty = stringResource(id = if (state.fans) R.string.follows_empty_fans else R.string.follows_empty_follows),
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
                onRefresh = viewModel::refresh,
                onLoadMore = viewModel::loadMore,
                onOpen = { navigator.navigate(UserProfileKey(it.user.id)) },
            )
        }
    }
}

@Composable
private fun UserList(
    list: PagerState<ListedUser>,
    empty: String,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (ListedUser) -> Unit,
) {
    val error = (list.refresh as? LoadStatus.Error)?.error
    StateScreen(
        isEmpty = list.items.isEmpty(),
        isError = error != null,
        isLoading = list.refresh == LoadStatus.Loading || list.refresh == LoadStatus.Idle,
        onReload = onRefresh,
        errorScreen = { ErrorScreen(error = error) },
        emptyScreen = {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = empty, style = TextStyles.label, color = ExtendedTheme.colors.textSecondary)
            }
        },
    ) {
        val lazyListState = rememberLazyListState()
        LoadMoreLayout(
            isLoading = list.append == LoadStatus.Loading,
            onLoadMore = onLoadMore,
            loadEnd = !list.hasMore,
            lazyListState = lazyListState,
        ) {
            MyLazyColumn(state = lazyListState, contentPadding = contentPadding) {
                items(list.items, key = { it.user.id }) { item ->
                    UserRow(item = item, onClick = { onOpen(item) })
                }
                loadMoreFooter(list.append, onLoadMore)
            }
        }
    }
}

/** One user: their avatar, which flies into their profile, their name and what they say about themselves. */
@Composable
private fun UserRow(item: ListedUser, onClick: () -> Unit) {
    val hero = rememberPageHero()
    val avatar = StringUtil.getAvatarUrl(item.user.portrait)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                hero.leave(avatar)
                onClick()
            }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Avatar(data = avatar, size = Sizes.Medium, contentDescription = null, modifier = Modifier.pageHero(hero))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.user.displayName,
                style = TextStyles.nameCompact,
                color = ExtendedTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.intro.isNotBlank()) {
                Text(
                    text = item.intro,
                    style = TextStyles.caption,
                    color = ExtendedTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
