package com.huanchengfly.tieba.post.ui.page.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.huanchengfly.tieba.post.ui.page.search.SearchViewModel
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.LazyLoadHorizontalPager
import com.huanchengfly.tieba.post.ui.widgets.compose.LocalChromePadding
import com.huanchengfly.tieba.post.ui.widgets.compose.MyBackHandler
import com.huanchengfly.tieba.post.ui.widgets.compose.animateHiddenFraction
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberHideOnScrollState
import kotlinx.coroutines.launch

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
    val search = rememberMainSearchState()
    val searchViewModel: SearchViewModel = hiltViewModel()
    val searchUiState by searchViewModel.state.collectAsStateWithLifecycle()
    var searchInput by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(searchUiState.keyword) {
        if (searchUiState.keyword.isNotEmpty()) searchInput = searchUiState.keyword
    }
    val focusManager = LocalFocusManager.current
    val openSearch: () -> Unit = {
        coroutineScope.launch { search.open() }
    }
    val closeSearch: () -> Unit = {
        focusManager.clearFocus()
        coroutineScope.launch {
            search.close()
            // Opened again, search starts from the history
            searchInput = ""
            searchViewModel.search("")
        }
    }
    val searchShown by remember { derivedStateOf { search.progress.value > 0f } }

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
                        modifier = Modifier
                            .graphicsLayer { alpha = 1f - search.progress.value }
                            .then(if (search.open) Modifier.clearAndSetSemantics {} else Modifier),
                        onSearchButtonPlaced = { search.buttonBounds = it },
                        searching = searchShown,
                    )
                }
            },
            overlay = {
                if (showBottomNavigation) {
                    MainSearchField(
                        state = search,
                        input = searchInput,
                        onInputChange = {
                            searchInput = it
                            searchViewModel.onInputChange(it)
                        },
                        onSearch = {
                            searchInput = it
                            focusManager.clearFocus()
                            searchViewModel.search(it)
                        },
                        onOpen = openSearch,
                        onClose = closeSearch,
                    )
                }
            },
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
                    // Gives way to search, which on the translucent theme has no ground of its own
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - search.progress.value },
                    verticalAlignment = Alignment.Top,
                    userScrollEnabled = false
                ) {
                    when (navigationItems[it].id) {
                        MainTabs.HOME -> HomePage(
                            canOpenExplore = navigationItems.any { item -> item.id == MainTabs.EXPLORE },
                            showSearchBox = !showBottomNavigation,
                        ) {
                            tabs.selected = MainTabs.EXPLORE
                        }

                        MainTabs.EXPLORE -> ExplorePage()
                        MainTabs.NOTIFICATIONS -> NotificationsTab()
                        MainTabs.USER -> UserPage()
                    }
                }
                if (searchShown) {
                    CompositionLocalProvider(LocalChromePadding provides mainSearchPadding()) {
                        Box(modifier = Modifier.graphicsLayer { alpha = search.progress.value }) {
                            MainSearchContent(
                                state = searchUiState,
                                input = searchInput,
                                viewModel = searchViewModel,
                                navigator = navigator,
                                onSearch = {
                                    searchInput = it
                                    focusManager.clearFocus()
                                    searchViewModel.search(it)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    MyBackHandler(enabled = tabs.selected != MainTabs.HOME) {
        tabs.selected = MainTabs.HOME
    }
    // After the tabs' handler, so it is asked first: back from results goes to the history
    MyBackHandler(enabled = search.open) {
        if (searchUiState.keyword.isNotEmpty()) searchViewModel.search("") else closeSearch()
    }
}
