package oolong.client.ui.page.main.explore

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.arch.GlobalEvent
import oolong.client.arch.emitGlobalEvent
import oolong.client.arch.onGlobalEvent
import oolong.client.ui.common.LocalAccount
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.page.main.explore.concern.ConcernPage
import oolong.client.ui.page.main.explore.hot.HotPage
import oolong.client.ui.page.main.explore.personalized.PersonalizedPage
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTab
import oolong.client.ui.widgets.compose.GlassTabRow
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.LazyLoadHorizontalPager

@Immutable
data class ExplorePageItem(
    val id: String,
    val name: @Composable (selected: Boolean) -> Unit,
    val content: @Composable (contentPadding: PaddingValues) -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExplorePageTab(
    pagerState: PagerState,
    pages: ImmutableList<ExplorePageItem>
) {
    val coroutineScope = rememberCoroutineScope()

    GlassTabRow(selectedTabIndex = pagerState.currentPage, pagerState = pagerState) {
        pages.fastForEachIndexed { index, item ->
            GlassTab(
                text = { item.name(pagerState.currentPage == index) },
                selected = pagerState.currentPage == index,
                selectedContentColor = ExtendedTheme.colors.text,
                unselectedContentColor = ExtendedTheme.colors.textSecondary,
                onClick = {
                    coroutineScope.launch {
                        if (pagerState.currentPage == index) {
                            emitGlobalEvent(GlobalEvent.Refresh(item.id))
                        } else {
                            pagerState.animateScrollToPage(index)
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun TabText(
    text: String,
    selected: Boolean
) {
    val style = MaterialTheme.typography.button.copy(
        letterSpacing = 0.75.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center
    )
    Text(text = text, style = style)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExplorePage() {
    val account = LocalAccount.current
    val navigator = LocalNavigator.current

    val loggedIn = remember(account) { account != null }

    val pages = remember {
        listOfNotNull(
            if (loggedIn) ExplorePageItem(
                "concern",
                { TabText(text = stringResource(id = R.string.title_concern), selected = it) },
                { ConcernPage(contentPadding = it) }
            ) else null,
            ExplorePageItem(
                "personalized",
                { TabText(text = stringResource(id = R.string.title_personalized), selected = it) },
                { PersonalizedPage(contentPadding = it) }
            ),
            ExplorePageItem(
                "hot",
                { TabText(text = stringResource(id = R.string.title_hot), selected = it) },
                { HotPage(contentPadding = it) }
            ),
        ).toImmutableList()
    }
    val pagerState = rememberPagerState(initialPage = if (account != null) 1 else 0) { pages.size }
    val coroutineScope = rememberCoroutineScope()

    onGlobalEvent<GlobalEvent.Refresh>(
        filter = { it.key == "explore" }
    ) {
        coroutineScope.emitGlobalEvent(GlobalEvent.Refresh(pages[pagerState.currentPage].id))
    }

    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            // The tabs are the page's title; the bottom bar already names the page
            GlassTopBar(title = { ExplorePageTab(pagerState = pagerState, pages = pages) }, centerTitle = true)
        },
        modifier = Modifier.fillMaxSize(),
    ) { paddingValues ->
        LazyLoadHorizontalPager(
            state = pagerState,
            key = { pages[it].id },
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Top,
            userScrollEnabled = true,
        ) {
            pages[it].content(paddingValues)
        }
    }
}
