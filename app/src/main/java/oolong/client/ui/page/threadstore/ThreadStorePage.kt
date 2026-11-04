package oolong.client.ui.page.threadstore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import oolong.client.R
import oolong.client.arch.LoadStatus
import oolong.client.arch.PagerState
import oolong.client.model.StoredThread
import oolong.client.model.User
import oolong.client.ui.common.theme.compose.Corners
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.navigation.ThreadKey
import oolong.client.ui.navigation.UserProfileKey
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.page.thread.ThreadPageFrom
import oolong.client.ui.page.thread.ThreadPageFromStoreExtra
import oolong.client.ui.page.thread.ThreadSortType
import oolong.client.ui.widgets.compose.Avatar
import oolong.client.ui.widgets.compose.BackNavigationIcon
import oolong.client.ui.widgets.compose.ContextMenuItem
import oolong.client.ui.widgets.compose.EndPull
import oolong.client.ui.widgets.compose.ErrorScreen
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.GlassTopBarTitle
import oolong.client.ui.widgets.compose.LoadMoreEffect
import oolong.client.ui.widgets.compose.MyLazyColumn
import oolong.client.ui.widgets.compose.PullRefresh
import oolong.client.ui.widgets.compose.Sizes
import oolong.client.ui.widgets.compose.UserHeader
import oolong.client.ui.widgets.compose.loadMoreFooter
import oolong.client.ui.widgets.compose.longPressMenu
import oolong.client.ui.widgets.compose.states.StateScreen
import oolong.client.utils.StringUtil
import oolong.client.utils.StringUtil.getUsernameAnnotatedString
import oolong.client.utils.appPreferences

private val UpdateTipTextStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 10.sp)

@Composable
fun ThreadStorePage(
    navigator: Navigator = LocalNavigator.current,
    viewModel: ThreadStoreViewModel = hiltViewModel(),
) {
    val list by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ThreadStoreScreen(
        list = list,
        onBack = { navigator.navigateUp() },
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onRemove = viewModel::remove,
        onOpenUser = { navigator.navigate(UserProfileKey(it.id)) },
        onOpen = { thread ->
            navigator.navigate(
                ThreadKey(
                    threadId = thread.threadId,
                    postId = thread.markPostId,
                    seeLz = context.appPreferences.collectThreadSeeLz,
                    sortType = if (context.appPreferences.collectThreadDescSort) ThreadSortType.SORT_TYPE_DESC else ThreadSortType.SORT_TYPE_DEFAULT,
                    from = ThreadPageFrom.FROM_STORE,
                    extra = ThreadPageFromStoreExtra(
                        maxPid = thread.maxPostId,
                        maxFloor = thread.latestFloor
                    )
                )
            )
        },
    )
}

/** The threads the user stored; pulls down to refresh, and long pressing one offers to remove it. */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ThreadStoreScreen(
    list: PagerState<StoredThread>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRemove: (StoredThread) -> Unit,
    onOpenUser: (User) -> Unit,
    onOpen: (StoredThread) -> Unit,
) {
    val isRefreshing = list.refresh == LoadStatus.Loading
    val error = (list.refresh as? LoadStatus.Error)?.error
    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = R.string.title_my_collect)) },
                navigationIcon = {
                    BackNavigationIcon(onBackPressed = onBack)
                }
            )
        }
    ) { contentPaddings ->
        StateScreen(
            isEmpty = list.items.isEmpty(),
            isError = error != null,
            isLoading = isRefreshing,
            modifier = Modifier.fillMaxSize(),
            onReload = onRefresh,
            errorScreen = { ErrorScreen(error = error) }
        ) {
            val lazyListState = rememberLazyListState()

            LoadMoreEffect(lazyListState, enabled = list.hasMore && list.append == LoadStatus.Idle, onLoadMore = onLoadMore)
            PullRefresh(
                refreshing = isRefreshing,
                onRefresh = onRefresh,
                indicatorTop = contentPaddings.calculateTopPadding(),
                endPull = if (list.hasMore) EndPull.None else EndPull.Tease,
            ) {
                MyLazyColumn(state = lazyListState, contentPadding = contentPaddings) {
                    items(
                        items = list.items,
                        key = { it.threadId }
                    ) { thread ->
                        StoreItem(
                            info = thread,
                            onUserClick = { thread.author?.let(onOpenUser) },
                            onClick = { onOpen(thread) },
                            onDelete = { onRemove(thread) }
                        )
                    }
                    loadMoreFooter(list.append, list.hasMore, onLoadMore)
                }
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun StoreItem(
    info: StoredThread,
    onUserClick: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasUpdate = info.hasUpdate
    val isDeleted = info.deleted
    val textMeasurer = rememberTextMeasurer()
    val unstore = stringResource(id = R.string.title_collect_on)
    Box(
        modifier = Modifier.longPressMenu(
            items = { listOf(ContextMenuItem(Symbols.BookmarkRemove, unstore, onClick = onDelete)) },
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
                        data = StringUtil.getAvatarUrl(info.author?.portrait),
                        size = Sizes.Small,
                        contentDescription = null
                    )
                },
                name = {
                    Text(
                        text = getUsernameAnnotatedString(
                            LocalContext.current,
                            info.author?.name.orEmpty(),
                            info.author?.nameShow,
                            LocalContentColor.current
                        )
                    )
                },
                onClick = onUserClick,
            )
            val title = remember(info, hasUpdate) {
                buildAnnotatedString {
                    append(info.title)
                    if (hasUpdate) {
                        append(" ")
                        appendInlineContent("Update", info.latestFloor.toString())
                    }
                }
            }
            val updateTip = stringResource(
                id = R.string.tip_thread_store_update,
                info.latestFloor.toString()
            )
            val result = remember {
                textMeasurer.measure(
                    AnnotatedString(updateTip),
                    style = UpdateTipTextStyle
                ).size
            }
            // In the density the title is drawn in, whose text scale is the app's own
            val (width, height) = with(LocalDensity.current) {
                (result.width.toDp() + 12.dp * 2 + 1.dp).toSp() to (result.height.toDp() + 4.dp * 2).toSp()
            }
            Text(
                text = title,
                fontSize = 15.sp,
                color = if (isDeleted) ExtendedTheme.colors.textDisabled else ExtendedTheme.colors.text,
                inlineContent = mapOf(
                    "Update" to InlineTextContent(
                        placeholder = Placeholder(
                            width = width,
                            height = height,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                        ),
                        children = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        color = ExtendedTheme.colors.chip,
                                        shape = Corners.xs
                                    )
                                    .padding(vertical = 4.dp, horizontal = 12.dp)
                            ) {
                                Text(
                                    text = updateTip,
                                    style = UpdateTipTextStyle,
                                    color = ExtendedTheme.colors.onChip,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                    )
                )
            )
            if (isDeleted) {
                Text(
                    text = stringResource(id = R.string.tip_thread_store_deleted),
                    fontSize = 12.sp,
                    color = ExtendedTheme.colors.textDisabled
                )
            }
        }
    }
}
