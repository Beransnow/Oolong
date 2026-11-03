package oolong.client.ui.page.user

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Tab
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.arch.LoadStatus
import oolong.client.model.UserProfile
import oolong.client.models.PhotoViewData
import oolong.client.models.PicItem
import oolong.client.ui.common.navigation.openedWithHero
import oolong.client.ui.common.navigation.pageHeroTarget
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Motion
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.common.windowsizeclass.LocalWindowSizeClass
import oolong.client.ui.common.windowsizeclass.WindowWidthSizeClass
import oolong.client.ui.navigation.EditProfileKey
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.navigation.UserFollowsKey
import oolong.client.ui.navigation.UserProfileKey
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.page.ProvideNavigator
import oolong.client.ui.page.user.likeforum.UserLikeForumPage
import oolong.client.ui.page.user.post.UserPostPage
import oolong.client.ui.widgets.compose.Avatar
import oolong.client.ui.widgets.compose.BackNavigationIcon
import oolong.client.ui.widgets.compose.Button
import oolong.client.ui.widgets.compose.Chip
import oolong.client.ui.widgets.compose.ContextMenuItem
import oolong.client.ui.widgets.compose.ErrorScreen
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.LazyLoadHorizontalPager
import oolong.client.ui.widgets.compose.LocalChromePadding
import oolong.client.ui.widgets.compose.MenuIconButton
import oolong.client.ui.widgets.compose.NetworkImage
import oolong.client.ui.widgets.compose.PagerTabIndicator
import oolong.client.ui.widgets.compose.ProvideContentColor
import oolong.client.ui.widgets.compose.PullRefresh
import oolong.client.ui.widgets.compose.ScrollableTabRow
import oolong.client.ui.widgets.compose.Sizes
import oolong.client.ui.widgets.compose.UserHeader
import oolong.client.ui.widgets.compose.placeholder.PlaceholderHighlight
import oolong.client.ui.widgets.compose.placeholder.fade
import oolong.client.ui.widgets.compose.placeholder.placeholder
import oolong.client.ui.widgets.compose.states.DefaultLoadingScreen
import oolong.client.ui.widgets.compose.states.StateScreen
import oolong.client.utils.StringUtil
import oolong.client.utils.StringUtil.getShortNumString
import oolong.client.utils.TiebaUtil
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

@Composable
fun UserProfilePage(
    key: UserProfileKey,
    navigator: Navigator = LocalNavigator.current,
    viewModel: UserProfileViewModel = hiltViewModel<UserProfileViewModel, UserProfileViewModel.Factory> { it.create(key) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showBlack by remember { mutableStateOf(false) }
    if (showBlack) {
        state.profile?.let { user ->
            BlackDialog(
                name = user.nameShow.ifEmpty { user.name },
                settings = state.black,
                onSave = viewModel::setBlack,
                onDismiss = { showBlack = false },
            )
        }
    }
    ProvideNavigator(navigator = navigator) {
        StateScreen(
            modifier = Modifier.fillMaxSize(),
            isEmpty = state.profile == null,
            isError = state.load is LoadStatus.Error,
            isLoading = state.load == LoadStatus.Loading,
            onReload = viewModel::refreshProfile,
            errorScreen = { ErrorScreen(error = (state.load as? LoadStatus.Error)?.error) },
            loadingScreen = {
                val hero = openedWithHero()
                if (hero != null) UserProfileSkeleton(avatar = hero.image, onBack = { navigator.navigateUp() }) else DefaultLoadingScreen()
            },
        ) {
            state.profile?.let {
                UserProfileContent(
                    user = it,
                    showActionBtn = state.loggedIn,
                    disableButton = state.following,
                    isSelf = state.isSelf,
                    onBack = { navigator.navigateUp() },
                    onFollow = viewModel::toggleFollow,
                    onUnfollow = viewModel::toggleFollow,
                    onRefresh = viewModel::refreshAll,
                    onAddToList = viewModel::addToList,
                    onBlack = {
                        viewModel.loadBlack()
                        showBlack = true
                    }.takeIf { state.loggedIn },
                    listContent = { tab, expanded -> UserProfileList(state, tab, expanded, viewModel) },
                )
            }
        }
    }
}

/** One of the profile's lists, opened the first time it shows. */
@Composable
private fun UserProfileList(state: UserProfileState, tab: UserTab, expanded: Boolean, viewModel: UserProfileViewModel) {
    LaunchedEffect(tab) { viewModel.open(tab) }
    when (tab) {
        UserTab.Forums -> UserLikeForumPage(
            list = state.forums,
            onRefresh = { viewModel.refresh(tab) },
            onLoadMore = { viewModel.loadMore(tab) },
            fluid = expanded,
            enablePullRefresh = expanded,
        )

        else -> UserPostPage(
            list = if (tab == UserTab.Threads) state.threads else state.posts,
            hidden = state.postsHidden,
            onRefresh = { viewModel.refresh(tab) },
            onLoadMore = { viewModel.loadMore(tab) },
            onAgree = { viewModel.agree(tab, it.id, it.firstPostId) },
            fluid = expanded,
            enablePullRefresh = expanded,
        )
    }
}

@Composable
private fun UserProfileContent(
    user: UserProfile,
    showActionBtn: Boolean,
    disableButton: Boolean,
    isSelf: Boolean,
    onBack: () -> Unit,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
    onRefresh: () -> Unit,
    onAddToList: (allow: Boolean) -> Unit,
    /** Opens the blacklist settings; null when no one is logged in. */
    onBlack: (() -> Unit)?,
    listContent: @Composable (UserTab, Boolean) -> Unit,
) {
    when (LocalWindowSizeClass.current.widthSizeClass) {
        WindowWidthSizeClass.Expanded -> {
            UserProfileContentExpanded(
                user = user,
                showActionBtn = showActionBtn,
                disableButton = disableButton,
                isSelf = isSelf,
                onBack = onBack,
                onFollow = onFollow,
                onUnfollow = onUnfollow,
                onAddToList = onAddToList,
                onBlack = onBlack,
                listContent = listContent,
            )
        }

        else -> {
            UserProfileContentNormal(
                user = user,
                showActionBtn = showActionBtn,
                disableButton = disableButton,
                isSelf = isSelf,
                onBack = onBack,
                onFollow = onFollow,
                onUnfollow = onUnfollow,
                onRefresh = onRefresh,
                onAddToList = onAddToList,
                onBlack = onBlack,
                listContent = listContent,
            )
        }
    }
}

@Composable
private fun UserProfileToolbar(
    user: UserProfile,
    isSelf: Boolean,
    showTitle: Boolean,
    onBack: () -> Unit,
    onAddToList: (allow: Boolean) -> Unit,
    /** Opens the blacklist settings; null when no one is logged in. */
    onBlack: (() -> Unit)?,
) {
    GlassTopBar(
        title = {
            AnimatedVisibility(
                visible = showTitle,
                enter = fadeIn(Motion.fadeIn()),
                exit = fadeOut(Motion.fadeOut())
            ) {
                ToolbarUserTitle(user = user)
            }
        },
        navigationIcon = {
            BackNavigationIcon(onBackPressed = onBack)
        },
        actions = {
            if (!isSelf) {
                val toBlackList = stringResource(id = R.string.menu_add_user_to_black_list)
                val toWhiteList = stringResource(id = R.string.menu_add_user_to_white_list)
                val black = stringResource(id = R.string.menu_black_user)
                MenuIconButton(
                    icon = Symbols.NoAccounts,
                    contentDescription = stringResource(id = R.string.btn_block),
                    items = {
                        listOfNotNull(
                            ContextMenuItem(Symbols.VisibilityOff, toBlackList) { onAddToList(false) },
                            ContextMenuItem(Symbols.Visibility, toWhiteList) { onAddToList(true) },
                            onBlack?.let { ContextMenuItem(Symbols.Block, black, startsGroup = true, onClick = it) },
                        )
                    },
                )
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserProfileContentNormal(
    user: UserProfile,
    showActionBtn: Boolean,
    disableButton: Boolean,
    isSelf: Boolean,
    onBack: () -> Unit,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
    onRefresh: () -> Unit,
    onAddToList: (allow: Boolean) -> Unit,
    /** Opens the blacklist settings; null when no one is logged in. */
    onBlack: (() -> Unit)?,
    listContent: @Composable (UserTab, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val density = LocalDensity.current

    var heightOffset by rememberSaveable { mutableFloatStateOf(0f) }
    // Unknown until the header has been measured, and it is not cut down till then: a guess
    // shorter than it showed only its bottom, without the avatar, as the page came in
    var headerHeight by rememberSaveable { mutableFloatStateOf(HeaderUnmeasured) }

    val isShowHeaderArea by remember {
        derivedStateOf {
            headerHeight == HeaderUnmeasured || heightOffset.absoluteValue < headerHeight
        }
    }

    GlassScaffold(
        topBar = {
            UserProfileToolbar(
                user = user,
                isSelf = isSelf,
                showTitle = !isShowHeaderArea,
                onBack = onBack,
                onAddToList = onAddToList,
                onBlack = onBlack,
            )
        }
    ) { paddingValues ->
        var isFakeRefreshing by remember { mutableStateOf(false) }

        LaunchedEffect(isFakeRefreshing) {
            if (isFakeRefreshing) {
                delay(1000)
                isFakeRefreshing = false
            }
        }

        PullRefresh(
            refreshing = isFakeRefreshing,
            onRefresh = {
                onRefresh()
                isFakeRefreshing = true
            },
            indicatorTop = paddingValues.calculateTopPadding(),
        ) {
            val headerNestedScrollConnection = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset {
                        if (available.y < 0) {
                            val prevHeightOffset = heightOffset
                            if (headerHeight == HeaderUnmeasured) return Offset.Zero
                            heightOffset = max(heightOffset + available.y, -headerHeight)
                            if (prevHeightOffset != heightOffset) {
                                return available.copy(x = 0f)
                            }
                        }

                        return Offset.Zero
                    }

                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset {
                        if (available.y > 0f) {
                            // Adjust the height offset in case the consumed delta Y is less than what was
                            // recorded as available delta Y in the pre-scroll.
                            val prevHeightOffset = heightOffset
                            heightOffset = min(heightOffset + available.y, 0f)
                            if (prevHeightOffset != heightOffset) {
                                return available.copy(x = 0f)
                            }
                        }

                        return Offset.Zero
                    }
                }
            }

            ProvideContentColor(color = ExtendedTheme.colors.text) {
                // The header collapses under the bar; the tab lists scroll to the screen bottom.
                Column(
                    modifier = Modifier
                        .padding(top = paddingValues.calculateTopPadding())
                        .nestedScroll(headerNestedScrollConnection)
                ) {
                    val pages = remember {
                        listOfNotNull(
                            UserProfilePageData(
                                id = "threads",
                                tab = UserTab.Threads,
                                title = {
                                    stringResource(
                                        id = R.string.title_profile_threads_tab,
                                        it.threadCount.getShortNumString()
                                    )
                                },
                                content = { tab, fluid -> listContent(tab, fluid) }
                            ),
                            UserProfilePageData(
                                id = "posts",
                                tab = UserTab.Posts,
                                title = {
                                    stringResource(
                                        id = R.string.title_profile_posts_tab,
                                        it.postCount.getShortNumString()
                                    )
                                },
                                content = { tab, fluid -> listContent(tab, fluid) }
                            ).takeIf { isSelf },
                            UserProfilePageData(
                                id = "concern_forums",
                                tab = UserTab.Forums,
                                title = {
                                    stringResource(
                                        id = R.string.title_profile_concern_forums_tab,
                                        it.forumCount.toString()
                                    )
                                },
                                content = { tab, fluid -> listContent(tab, fluid) }
                            ),
                        ).toImmutableList()
                    }
                    val pagerState = rememberPagerState { pages.size }

                    val containerHeight by remember {
                        derivedStateOf {
                            with(density) {
                                (headerHeight + heightOffset).toDp()
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .then(if (headerHeight == HeaderUnmeasured) Modifier else Modifier.height(containerHeight))
                            .clipToBounds()
                    ) {
                        Box(
                            modifier = Modifier
                                .wrapContentHeight(
                                    align = Alignment.Bottom,
                                    unbounded = true
                                )
                                .onSizeChanged {
                                    headerHeight = it.height.toFloat()
                                }
                        ) {
                            UserProfileDetail(
                                user = user,
                                modifier = Modifier
                                    .padding(16.dp)
                                    .padding(top = 8.dp),
                                showBtn = showActionBtn,
                                isSelf = isSelf,
                                onBtnClick = {
                                    if (disableButton || !showActionBtn) {
                                        return@UserProfileDetail
                                    }
                                    if (isSelf) {
                                        navigator.navigate(EditProfileKey)
                                    } else if (!user.followed) {
                                        onFollow()
                                    } else {
                                        onUnfollow()
                                    }
                                },
                                onCopyIdClick = {
                                    TiebaUtil.copyText(
                                        context,
                                        user.id.toString()
                                    )
                                }
                            )
                        }
                    }

                    UserProfileTabRow(
                        user = user,
                        pages = pages,
                        pagerState = pagerState,
//                                    modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    CompositionLocalProvider(
                        LocalChromePadding provides
                            PaddingValues(bottom = paddingValues.calculateBottomPadding())
                    ) {
                        LazyLoadHorizontalPager(
                            state = pagerState,
                            key = { pages[it].id },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            pages[it].content(pages[it].tab, false)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserProfileContentExpanded(
    user: UserProfile,
    showActionBtn: Boolean,
    disableButton: Boolean,
    isSelf: Boolean,
    onBack: () -> Unit,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
    onAddToList: (allow: Boolean) -> Unit,
    /** Opens the blacklist settings; null when no one is logged in. */
    onBlack: (() -> Unit)?,
    listContent: @Composable (UserTab, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current

    GlassScaffold(
        topBar = {
            UserProfileToolbar(
                user = user,
                isSelf = isSelf,
                showTitle = false,
                onBack = onBack,
                onAddToList = onAddToList,
                onBlack = onBlack,
            )
        }
    ) { paddingValues ->
        ProvideContentColor(color = ExtendedTheme.colors.text) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .padding(top = paddingValues.calculateTopPadding())
                        .fillMaxHeight()
                        .fillMaxWidth(0.75f)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val pages = remember {
                        listOfNotNull(
                            UserProfilePageData(
                                id = "threads",
                                tab = UserTab.Threads,
                                title = {
                                    stringResource(
                                        id = R.string.title_profile_threads_tab,
                                        it.threadCount.getShortNumString()
                                    )
                                },
                                content = { tab, expanded -> listContent(tab, expanded) }
                            ),
                            UserProfilePageData(
                                id = "posts",
                                tab = UserTab.Posts,
                                title = {
                                    stringResource(
                                        id = R.string.title_profile_posts_tab,
                                        it.postCount.getShortNumString()
                                    )
                                },
                                content = { tab, expanded -> listContent(tab, expanded) }
                            ).takeIf { isSelf },
                            UserProfilePageData(
                                id = "concern_forums",
                                tab = UserTab.Forums,
                                title = {
                                    stringResource(
                                        id = R.string.title_profile_concern_forums_tab,
                                        it.forumCount.toString()
                                    )
                                },
                                content = { tab, expanded -> listContent(tab, expanded) }
                            ),
                        ).toImmutableList()
                    }
                    val pagerState = rememberPagerState { pages.size }

                    UserProfileDetail(
                        user = user,
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.Top),
                        showBtn = showActionBtn,
                        isSelf = isSelf,
                        onBtnClick = {
                            if (disableButton || !showActionBtn) {
                                return@UserProfileDetail
                            }
                            if (isSelf) {
                                navigator.navigate(EditProfileKey)
                            } else if (!user.followed) {
                                onFollow()
                            } else {
                                onUnfollow()
                            }
                        },
                        onCopyIdClick = {
                            TiebaUtil.copyText(
                                context,
                                user.id.toString()
                            )
                        }
                    )

                    Column(
                        modifier = Modifier.weight(2f)
                    ) {
                        UserProfileTabRow(
                            user = user,
                            pages = pages,
                            pagerState = pagerState,
                        )

                        CompositionLocalProvider(
                            LocalChromePadding provides
                                PaddingValues(bottom = paddingValues.calculateBottomPadding())
                        ) {
                            LazyLoadHorizontalPager(
                                state = pagerState,
                                key = { pages[it].id },
                                modifier = Modifier.weight(1f)
                            ) {
                                pages[it].content(pages[it].tab, true)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Immutable
data class UserProfilePageData(
    val id: String,
    val tab: UserTab,
    val title: @Composable (UserProfile) -> String,
    val content: @Composable (UserTab, Boolean) -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserProfileTabRow(
    user: UserProfile,
    pages: ImmutableList<UserProfilePageData>,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()

    val tabTextStyle = MaterialTheme.typography.button.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 0.sp
    )

    ScrollableTabRow(
        selectedTabIndex = pagerState.currentPage,
        indicator = { tabPositions ->
            PagerTabIndicator(
                pagerState = pagerState,
                tabPositions = tabPositions
            )
        },
        divider = {},
        backgroundColor = Color.Transparent,
        contentColor = ExtendedTheme.colors.emphasis,
        edgePadding = 0.dp,
        modifier = modifier.wrapContentWidth(align = Alignment.Start),
    ) {
        pages.fastForEachIndexed { i, pageData ->
            Tab(
                selected = pagerState.currentPage == i,
                onClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(i)
                    }
                },
                selectedContentColor = ExtendedTheme.colors.emphasis,
                unselectedContentColor = ExtendedTheme.colors.textSecondary,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(48.dp)
                        .padding(horizontal = 16.dp)
                ) {
                    Text(text = pageData.title(user), style = tabTextStyle)
                }
            }
        }
    }
}

@Composable
private fun ToolbarUserTitle(
    user: UserProfile,
    modifier: Modifier = Modifier,
) {
    UserHeader(
        avatar = {
            Avatar(
                data = StringUtil.getAvatarUrl(user.portrait),
                size = Sizes.Small,
                contentDescription = null
            )
        },
        name = {
            Text(
                text = StringUtil.getUsernameAnnotatedString(
                    LocalContext.current,
                    user.name,
                    user.displayName,
                    LocalContentColor.current
                )
            )
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserProfileDetail(
    user: UserProfile,
    modifier: Modifier = Modifier,
    showBtn: Boolean = true,
    isSelf: Boolean = false,
    onBtnClick: () -> Unit = {},
    onCopyIdClick: () -> Unit = {},
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val avatar = StringUtil.getBigAvatarUrl(user.portrait)
            // Over the small avatar lists show, already at hand, until the big one loads; where
            // an avatar a profile was opened from lands
            Box(
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .pageHeroTarget()
                    .size(ProfileAvatarSize)
                    .clip(CircleShape),
            ) {
                Avatar(data = StringUtil.getAvatarUrl(user.portrait), contentDescription = null, modifier = Modifier.matchParentSize(), decodedAt = Sizes.Small)
                // Opens in the picture viewer, where it can be saved
                NetworkImage(
                    imageUri = avatar,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    photoViewData = remember(avatar) {
                        PhotoViewData(
                            picItems = listOf(
                                PicItem(picId = "avatar:${user.portrait}", picIndex = 1, url = avatar, originUrl = avatar, showOriginBtn = false, originSize = 0),
                            ),
                        )
                    },
                    contentScale = ContentScale.Crop,
                    skipNetworkCheck = true,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (showBtn) {
                Button(
                    onClick = onBtnClick,
                    colors = if (!user.followed || isSelf) {
                        ButtonDefaults.buttonColors()
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    },
                    border = if (!user.followed || isSelf) {
                        null
                    } else {
                        ButtonDefaults.outlinedBorder
                    },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isSelf) {
                            Icon(imageVector = Symbols.Edit, contentDescription = null)
                            Text(text = stringResource(id = R.string.menu_edit_info))
                        } else if (!user.followed) {
                            Icon(imageVector = Symbols.Add, contentDescription = null)
                            Text(text = stringResource(id = R.string.button_follow))
                        } else {
                            Text(text = stringResource(id = R.string.button_unfollow))
                        }
                    }
                }
            }
        }
        Text(
            text = StringUtil.getUsernameAnnotatedString(
                LocalContext.current,
                user.name,
                user.displayName,
                LocalContentColor.current
            ),
            style = MaterialTheme.typography.h6,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold
        )
        ProvideTextStyle(value = MaterialTheme.typography.body2) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(IntrinsicSize.Min)
            ) {
                // Each count opens the list it counts
                val navigator = LocalNavigator.current
                val name = user.nameShow.ifEmpty { user.name }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable(role = Role.Button) { navigator.navigate(UserFollowsKey(user.id, fans = false, name = name)) },
                ) {
                    Text(
                        text = stringResource(id = R.string.text_stat_follow),
                        color = ExtendedTheme.colors.textSecondary
                    )
                    Text(
                        text = user.followCount.getShortNumString(),
                        color = ExtendedTheme.colors.text,
                        fontWeight = FontWeight.Bold
                    )
                }
                HorizontalDivider(modifier = Modifier.fillMaxHeight())
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable(role = Role.Button) { navigator.navigate(UserFollowsKey(user.id, fans = true, name = name)) },
                ) {
                    Text(
                        text = stringResource(id = R.string.text_stat_fans),
                        color = ExtendedTheme.colors.textSecondary
                    )
                    Text(
                        text = user.fanCount.getShortNumString(),
                        color = ExtendedTheme.colors.text,
                        fontWeight = FontWeight.Bold
                    )
                }
                HorizontalDivider(modifier = Modifier.fillMaxHeight())
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.text_stat_agrees),
                        color = ExtendedTheme.colors.textSecondary
                    )
                    Text(
                        text = user.agreeCount.getShortNumString(),
                        color = ExtendedTheme.colors.text,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Text(
            text = user.intro.takeIf { it.isNotEmpty() }
                ?: stringResource(id = R.string.tip_no_intro),
            style = MaterialTheme.typography.body2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val verifiedText = user.bazhuTitle ?: user.godField?.let { stringResource(id = R.string.text_god_verify, it) }
        if (verifiedText != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Symbols.Verified,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = ExtendedTheme.colors.emphasis,
                )
                Text(
                    text = verifiedText,
                    style = MaterialTheme.typography.body2,
                    color = ExtendedTheme.colors.emphasis,
                )
            }
        }
        val sexEmoji = when (user.sex) {
            1 -> "♂"
            2 -> "♀"
            else -> "?"
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Chip(text = sexEmoji, invertColor = true)
            Chip(
                text = stringResource(
                    id = R.string.text_profile_user_id,
                    user.tiebaUid
                ),
                appendIcon = {
                    Icon(
                        imageVector = Symbols.ContentCopy,
                        contentDescription = null
                    )
                },
                onClick = onCopyIdClick
            )
            if (user.ipLocation.isNotEmpty()) {
                Chip(
                    text = stringResource(
                        id = R.string.text_profile_ip_location,
                        user.ipLocation
                    ),
                )
            }
            Chip(
                text = stringResource(
                    id = R.string.text_profile_tb_age,
                    user.tbAge
                )
            )
        }
    }
}

@Composable
private fun HorizontalDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = Modifier
            .background(ExtendedTheme.colors.divider)
            .width(1.dp)
            .then(modifier)
    )
}

/**
 * A profile while it loads, opened from an avatar: the bar and the avatar where the header will
 * show it, so the avatar has somewhere to land; the rest stands in grey.
 */
@Composable
private fun UserProfileSkeleton(avatar: String?, onBack: () -> Unit) {
    GlassScaffold(
        topBar = {
            GlassTopBar(title = {}, navigationIcon = { BackNavigationIcon(onBackPressed = onBack) })
        },
    ) { paddingValues ->
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .padding(16.dp)
                .padding(top = 8.dp),
        ) {
            // At the size lists show it, so it is the copy at hand, there from the start
            Avatar(
                data = avatar,
                contentDescription = null,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .pageHeroTarget()
                    .size(ProfileAvatarSize),
                decodedAt = Sizes.Small,
            )
            Box(modifier = Modifier.size(width = 160.dp, height = 24.dp).placeholder(visible = true, highlight = PlaceholderHighlight.fade()))
            Box(modifier = Modifier.size(width = 220.dp, height = 16.dp).placeholder(visible = true, highlight = PlaceholderHighlight.fade()))
        }
    }
}

private val ProfileAvatarSize = 96.dp

/** A header height not yet measured. */
private const val HeaderUnmeasured = -1f
