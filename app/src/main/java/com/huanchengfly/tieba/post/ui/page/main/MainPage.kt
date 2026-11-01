package com.huanchengfly.tieba.post.ui.page.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huanchengfly.tieba.post.LocalNotificationCountFlow
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.BaseComposeActivity.Companion.LocalWindowSizeClass
import com.huanchengfly.tieba.post.arch.GlobalEvent
import com.huanchengfly.tieba.post.arch.emitGlobalEvent
import com.huanchengfly.tieba.post.rememberPreferenceAsState
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.windowsizeclass.WindowHeightSizeClass
import com.huanchengfly.tieba.post.ui.common.windowsizeclass.WindowWidthSizeClass
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.page.ProvideNavigator
import com.huanchengfly.tieba.post.ui.page.main.explore.ExplorePage
import com.huanchengfly.tieba.post.ui.page.main.home.HomePage
import com.huanchengfly.tieba.post.ui.page.main.notifications.NotificationsPage
import com.huanchengfly.tieba.post.ui.page.main.user.UserPage
import com.huanchengfly.tieba.post.ui.utils.MainNavigationContentPosition
import com.huanchengfly.tieba.post.ui.utils.MainNavigationType
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.LazyLoadHorizontalPager
import com.huanchengfly.tieba.post.ui.widgets.compose.LocalChromePadding
import com.huanchengfly.tieba.post.ui.widgets.compose.animateHiddenFraction
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberHideOnScrollState
import com.huanchengfly.tieba.post.utils.appPreferences
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@Composable
private fun NavigationWrapper(
    currentPosition: Int,
    onChangePosition: (position: Int) -> Unit,
    onReselected: (position: Int) -> Unit,
    navigationItems: ImmutableList<NavigationItem>,
    navigationType: MainNavigationType,
    navigationContentPosition: MainNavigationContentPosition,
    content: @Composable () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = navigationType == MainNavigationType.NAVIGATION_RAIL) {
            NavigationRail(
                currentPosition = currentPosition,
                onChangePosition = onChangePosition,
                onReselected = onReselected,
                navigationItems = navigationItems,
                navigationContentPosition = navigationContentPosition
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(
                    if (navigationType == MainNavigationType.BOTTOM_NAVIGATION) Modifier
                    else Modifier.consumeWindowInsets(
                        WindowInsets.systemBars.union(WindowInsets.displayCutout)
                            .only(WindowInsetsSides.Start)
                    )
                )
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationGraphicsApi::class)
@RootNavGraph(start = true)
@Destination
@Composable
fun MainPage(
    navigator: Navigator = LocalNavigator.current,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val windowSizeClass = LocalWindowSizeClass.current
    val windowHeightSizeClass by rememberUpdatedState(newValue = windowSizeClass.heightSizeClass)
    val windowWidthSizeClass by rememberUpdatedState(newValue = windowSizeClass.widthSizeClass)

    val state by viewModel.state.collectAsStateWithLifecycle()
    val messageCount = state.messageCount

    val notificationCountFlow = LocalNotificationCountFlow.current
    LaunchedEffect(null) {
        notificationCountFlow.collect {
            viewModel.setMessageCount(it)
        }
    }

    val hideExplore by rememberPreferenceAsState(
        key = booleanPreferencesKey("hideExplore"),
        defaultValue = LocalContext.current.appPreferences.hideExplore
    )
    val pageCount by remember {
        derivedStateOf {
            if (hideExplore) 3 else 4
        }
    }
    val pagerState = rememberPagerState { pageCount }
    LaunchedEffect(hideExplore) {
        if (pagerState.currentPage == 3 && hideExplore) {
            pagerState.scrollToPage(2)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val themeColors = ExtendedTheme.colors
    val navigationItems by remember {
        derivedStateOf {
            listOfNotNull(
                NavigationItem(
                    id = "home",
                    icon = { AnimatedImageVector.animatedVectorResource(id = R.drawable.ic_animated_rounded_inventory_2) },
                    title = { stringResource(id = R.string.title_main) },
                    content = {
                        HomePage(
                            canOpenExplore = !LocalContext.current.appPreferences.hideExplore
                        ) {
                            coroutineScope.launch {
                                pagerState.scrollToPage(1)
                            }
                        }
                    }
                ),
                if (hideExplore) null
                else NavigationItem(
                    id = "explore",
                    icon = {
                        AnimatedImageVector.animatedVectorResource(id = R.drawable.ic_animated_toy_fans)
                    },
                    title = { stringResource(id = R.string.title_explore) },
                    content = {
                        ExplorePage()
                    }
                ),
                NavigationItem(
                    id = "notification",
                    icon = {
                        AnimatedImageVector.animatedVectorResource(id = R.drawable.ic_animated_rounded_notifications)
                    },
                    title = { stringResource(id = R.string.title_notifications) },
                    badge = messageCount > 0,
                    badgeText = "$messageCount",
                    onClick = {
                        viewModel.clearMessages()
                    },
                    content = {
                        NotificationsPage()
                    }
                ),
                NavigationItem(
                    id = "user",
                    icon = {
                        AnimatedImageVector.animatedVectorResource(id = R.drawable.ic_animated_rounded_person)
                    },
                    title = { stringResource(id = R.string.title_user) },
                    content = {
                        UserPage()
                    }
                ),
            ).toImmutableList()
        }
    }
    val navigationType by remember {
        derivedStateOf {
            when (windowWidthSizeClass) {
                WindowWidthSizeClass.Compact -> {
                    MainNavigationType.BOTTOM_NAVIGATION
                }

                WindowWidthSizeClass.Medium,
                WindowWidthSizeClass.Expanded -> {
                    MainNavigationType.NAVIGATION_RAIL
                }

                else -> {
                    MainNavigationType.BOTTOM_NAVIGATION
                }
            }
        }
    }

    /**
     * Content inside Navigation Rail/Drawer can also be positioned at top, bottom or center for
     * ergonomics and reachability depending upon the height of the device.
     */
    val navigationContentPosition by remember {
        derivedStateOf {
            when (windowHeightSizeClass) {
                WindowHeightSizeClass.Compact -> {
                    MainNavigationContentPosition.TOP
                }

                WindowHeightSizeClass.Medium,
                WindowHeightSizeClass.Expanded -> {
                    MainNavigationContentPosition.CENTER
                }

                else -> {
                    MainNavigationContentPosition.TOP
                }
            }
        }
    }
    val onReselected: (Int) -> Unit = {
        coroutineScope.emitGlobalEvent(
            GlobalEvent.Refresh(navigationItems[it].id)
        )
    }
    ProvideNavigator(navigator = navigator) {
        NavigationWrapper(
            currentPosition = pagerState.currentPage,
            onChangePosition = { coroutineScope.launch { pagerState.scrollToPage(it) } },
            onReselected = onReselected,
            navigationItems = navigationItems,
            navigationType = navigationType,
            navigationContentPosition = navigationContentPosition
        ) {
            val navBarState = rememberHideOnScrollState()
            val navBarCollapsedFraction by navBarState.animateHiddenFraction()
            LaunchedEffect(pagerState.currentPage) { navBarState.show() }
            val showBottomNavigation = navigationType == MainNavigationType.BOTTOM_NAVIGATION
            GlassScaffold(
                backgroundColor = Color.Transparent,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(navBarState.nestedScrollConnection),
                bottomBar = {
                    if (showBottomNavigation) {
                        FloatingNavBar(
                            currentPosition = pagerState.currentPage,
                            onChangePosition = {
                                coroutineScope.launch { pagerState.scrollToPage(it) }
                            },
                            onReselected = onReselected,
                            navigationItems = navigationItems,
                            collapsedFraction = { navBarCollapsedFraction },
                            themeColors = themeColors,
                        )
                    }
                }
            ) { paddingValues ->
                val chromePadding = if (showBottomNavigation) {
                    PaddingValues(bottom = paddingValues.calculateBottomPadding())
                } else {
                    PaddingValues(0.dp)
                }
                CompositionLocalProvider(LocalChromePadding provides chromePadding) {
                    LazyLoadHorizontalPager(
                        state = pagerState,
                        key = { navigationItems[it].id },
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.Top,
                        userScrollEnabled = false
                    ) {
                        navigationItems[it].content()
                    }
                }
            }
        }
    }
    BackHandler(enabled = pagerState.currentPage != 0) {
        coroutineScope.launch {
            pagerState.scrollToPage(0)
        }
    }
}

