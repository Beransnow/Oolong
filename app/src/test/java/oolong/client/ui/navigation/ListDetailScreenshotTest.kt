package oolong.client.ui.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import oolong.client.testing.LegacyAppRule
import oolong.client.testing.captureScreenshot
import oolong.client.ui.common.navigation.FramedSinglePaneSceneStrategy
import oolong.client.ui.page.main.MainTabs
import oolong.client.ui.page.main.NavigationRail
import oolong.client.ui.page.main.rememberMainTabItems
import oolong.client.ui.widgets.compose.BackNavigationIcon
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.GlassTopBarTitle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The rail and the panes on windows wider than a phone, with stand-in pages. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ListDetailScreenshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    /** A tablet held sideways: the list on the left, the page it opened on the right. */
    @Test
    @Config(qualifiers = "+w1280dp-h800dp-mdpi")
    fun tablet() {
        captureScreenshot { Shell(listOf(Fake.Home, Fake.Thread), twoPane = true) }
    }

    /** Nothing open yet: the right pane is empty. */
    @Test
    @Config(qualifiers = "+w1280dp-h800dp-mdpi")
    fun tabletEmpty() {
        captureScreenshot { Shell(listOf(Fake.Home), twoPane = true) }
    }

    /** A forum opened from a thread takes the left pane; the thread stays on the right. */
    @Test
    @Config(qualifiers = "+w1280dp-h800dp-mdpi")
    fun tabletListOverPage() {
        captureScreenshot { Shell(listOf(Fake.Home, Fake.Thread, Fake.Forum), twoPane = true) }
    }

    /** An unfolded foldable, just wide enough for two panes: the list narrows to leave the page room. */
    @Test
    @Config(qualifiers = "+w880dp-h840dp-xhdpi")
    fun foldable() {
        captureScreenshot { Shell(listOf(Fake.Home, Fake.Thread), twoPane = true) }
    }

    /** A phone held sideways: wide but short, so one pane and a rail without labels. */
    @Test
    @Config(qualifiers = "+w851dp-h393dp-xxhdpi")
    fun phoneLandscape() {
        captureScreenshot { Shell(listOf(Fake.Home, Fake.Thread), twoPane = false, showLabels = false) }
    }

    private enum class Fake(val title: String, val role: PaneRole) {
        Home("首页", PaneRole.List),
        Forum("贴吧", PaneRole.List),
        Thread("帖子", PaneRole.Detail),
    }

    @Composable
    private fun Shell(stack: List<Fake>, twoPane: Boolean, showLabels: Boolean = true) {
        val state = remember { ListDetailState() }
        Row {
            NavigationRail(
                currentPosition = 0,
                onSelect = {},
                navigationItems = rememberMainTabItems(remember { MainTabs(messageCount = 3) }),
                showLabels = showLabels,
            )
            NavDisplay(
                backStack = stack,
                onBack = {},
                sceneStrategies = listOfNotNull(
                    if (twoPane) ListDetailSceneStrategy<Fake>(state) {} else null,
                    FramedSinglePaneSceneStrategy(),
                ),
                entryProvider = { fake ->
                    NavEntry(fake, metadata = paneRole(fake.role)) {
                        // As the app does: a page to read keeps to its reading width
                        if (fake.role == PaneRole.Detail) ReadingWidth { FakePage(fake) } else FakePage(fake)
                    }
                },
            )
        }
    }

    @Composable
    private fun FakePage(fake: Fake) {
        GlassScaffold(
            topBar = {
                GlassTopBar(
                    title = { GlassTopBarTitle(text = fake.title) },
                    navigationIcon = if (fake == Fake.Home) null else {
                        { BackNavigationIcon(onBackPressed = {}) }
                    },
                )
            },
        ) { padding ->
            LazyColumn(contentPadding = padding) {
                items(30) {
                    Text(
                        text = "${fake.title}的第 ${it + 1} 行",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}
