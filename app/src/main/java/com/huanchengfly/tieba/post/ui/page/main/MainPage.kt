package com.huanchengfly.tieba.post.ui.page.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.huanchengfly.tieba.post.arch.GlobalEvent
import com.huanchengfly.tieba.post.arch.emitGlobalEvent
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.page.ProvideNavigator
import com.huanchengfly.tieba.post.ui.page.main.explore.ExplorePage
import com.huanchengfly.tieba.post.ui.page.main.home.HomePage
import com.huanchengfly.tieba.post.ui.page.main.notifications.NotificationsTab
import com.huanchengfly.tieba.post.ui.page.main.user.UserPage
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.LazyLoadHorizontalPager
import com.huanchengfly.tieba.post.ui.widgets.compose.LocalChromePadding
import com.huanchengfly.tieba.post.ui.widgets.compose.MyBackHandler
import com.huanchengfly.tieba.post.ui.widgets.compose.animateHiddenFraction
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberHideOnScrollState

/**
 * The four tabs. On a phone their navigation is the floating bar at the bottom; on wider windows
 * it is the rail beside the panes, which lives outside this page.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainPage(
    navigator: Navigator = LocalNavigator.current,
) {
    val tabs = LocalMainTabs.current
    val navigationItems = rememberMainTabItems(tabs)
    val selectedIndex = navigationItems.indexOfFirst { it.id == tabs.selected }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = selectedIndex) { navigationItems.size }
    LaunchedEffect(selectedIndex, navigationItems.size) {
        if (pagerState.currentPage != selectedIndex) pagerState.scrollToPage(selectedIndex)
    }

    val coroutineScope = rememberCoroutineScope()
    val themeColors = ExtendedTheme.colors
    val showBottomNavigation = !LocalNavigationRailShown.current
    val onReselected: (Int) -> Unit = {
        coroutineScope.emitGlobalEvent(
            GlobalEvent.Refresh(navigationItems[it].id)
        )
    }
    ProvideNavigator(navigator = navigator) {
        val navBarState = rememberHideOnScrollState()
        val navBarCollapsedFraction by navBarState.animateHiddenFraction()
        LaunchedEffect(pagerState.currentPage) { navBarState.show() }
        GlassScaffold(
            backgroundColor = Color.Transparent,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(navBarState.nestedScrollConnection),
            bottomBar = {
                if (showBottomNavigation) {
                    FloatingNavBar(
                        currentPosition = pagerState.currentPage,
                        onChangePosition = { tabs.selected = navigationItems[it].id },
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
                    when (navigationItems[it].id) {
                        MainTabs.HOME -> HomePage(
                            canOpenExplore = navigationItems.any { item -> item.id == MainTabs.EXPLORE }
                        ) {
                            tabs.selected = MainTabs.EXPLORE
                        }

                        MainTabs.EXPLORE -> ExplorePage()
                        MainTabs.NOTIFICATIONS -> NotificationsTab()
                        MainTabs.USER -> UserPage()
                    }
                }
            }
        }
    }
    MyBackHandler(enabled = tabs.selected != MainTabs.HOME) {
        tabs.selected = MainTabs.HOME
    }
}
