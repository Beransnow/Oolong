package oolong.client.ui.page.main.notifications

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.model.NotificationType
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.page.ProvideNavigator
import oolong.client.ui.page.main.notifications.list.NotificationsListPage
import oolong.client.ui.widgets.compose.BackNavigationIcon
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTab
import oolong.client.ui.widgets.compose.GlassTabRow
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.LazyLoadHorizontalPager

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotificationsPage(
    navigator: Navigator = LocalNavigator.current,
    initialTab: Int = 0,
) {
    val pages = listOf<Pair<String, (@Composable (PaddingValues) -> Unit)>>(
        stringResource(id = R.string.title_reply_me) to @Composable {
            NotificationsListPage(type = NotificationType.ReplyMe, contentPadding = it)
        },
        stringResource(id = R.string.title_at_me) to @Composable {
            NotificationsListPage(type = NotificationType.AtMe, contentPadding = it)
        }
    )
    val pagerState = rememberPagerState(
        initialPage = initialTab,
    ) { pages.size }
    val coroutineScope = rememberCoroutineScope()
    ProvideNavigator(navigator = navigator) {
        GlassScaffold(
            backgroundColor = Color.Transparent,
            topBar = {
                GlassTopBar(
                    title = { NotificationTabs(pagerState, pages.map { it.first }) },
                    centerTitle = true,
                    navigationIcon = {
                        BackNavigationIcon {
                            navigator.navigateUp()
                        }
                    },
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) { paddingValues ->
            LazyLoadHorizontalPager(
                state = pagerState,
                key = { pages[it].first },
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Top,
                userScrollEnabled = true,
            ) {
                pages[it].second(paddingValues)
            }
        }
    }
}

/** The notifications tab of the main page; [NotificationsPage] is the page links open. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotificationsTab(
    initialTab: Int = 0,
) {
    val navigator = LocalNavigator.current
    val pages = listOf<Pair<String, (@Composable (PaddingValues) -> Unit)>>(
        stringResource(id = R.string.title_reply_me) to @Composable {
            NotificationsListPage(type = NotificationType.ReplyMe, contentPadding = it)
        },
        stringResource(id = R.string.title_at_me) to @Composable {
            NotificationsListPage(type = NotificationType.AtMe, contentPadding = it)
        }
    )
    val pagerState = rememberPagerState(
        initialPage = initialTab,
    ) { pages.size }
    val coroutineScope = rememberCoroutineScope()
    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { NotificationTabs(pagerState, pages.map { it.first }) },
                centerTitle = true,
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { paddingValues ->
        LazyLoadHorizontalPager(
            state = pagerState,
            key = { pages[it].first },
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Top,
            userScrollEnabled = true,
        ) {
            pages[it].second(paddingValues)
        }
    }
}

/** The pages' names on a tab capsule in the bar's title row: on these pages the tabs are the title. */
@Composable
private fun NotificationTabs(pagerState: PagerState, names: List<String>) {
    val coroutineScope = rememberCoroutineScope()
    GlassTabRow(
        selectedTabIndex = pagerState.currentPage,
        pagerState = pagerState,
        onSelect = { coroutineScope.launch { pagerState.animateScrollToPage(it) } },
    ) {
        names.forEachIndexed { index, name ->
            GlassTab(
                text = { Text(text = name) },
                selected = pagerState.currentPage == index,
                selectedContentColor = ExtendedTheme.colors.text,
                unselectedContentColor = ExtendedTheme.colors.textSecondary,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
            )
        }
    }
}
