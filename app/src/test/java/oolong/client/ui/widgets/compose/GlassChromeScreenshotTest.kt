package oolong.client.ui.widgets.compose

import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import oolong.client.R
import oolong.client.testing.LegacyAppRule
import oolong.client.testing.captureScreenshot
import oolong.client.ui.page.main.FloatingNavBar
import oolong.client.ui.page.main.NavigationItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "+w393dp-h851dp-xxhdpi")
class GlassChromeScreenshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    @OptIn(ExperimentalAnimationGraphicsApi::class)
    private val navigationItems = persistentListOf(
        NavigationItem(
            id = "home",
            icon = { AnimatedImageVector.animatedVectorResource(R.drawable.ic_animated_rounded_inventory_2) },
            title = { "首页" },
        ),
        NavigationItem(
            id = "explore",
            icon = { AnimatedImageVector.animatedVectorResource(R.drawable.ic_animated_toy_fans) },
            title = { "动态" },
        ),
        NavigationItem(
            id = "notification",
            icon = { AnimatedImageVector.animatedVectorResource(R.drawable.ic_animated_rounded_notifications) },
            title = { "消息" },
            badge = true,
            badgeText = "3",
        ),
        NavigationItem(
            id = "user",
            icon = { AnimatedImageVector.animatedVectorResource(R.drawable.ic_animated_rounded_person) },
            title = { "我" },
        ),
    )

    private val swatches = listOf(
        Color(0xFFE57373), Color(0xFF64B5F6), Color(0xFF81C784), Color(0xFFFFD54F), Color(0xFFBA68C8),
    )

    @Composable
    private fun Page(
        tier: GlassTier,
        collapsed: Float,
        firstVisibleItem: Int,
        withNavBar: Boolean = true,
        topBarHidden: Float = 0f,
    ) {
        CompositionLocalProvider(LocalGlassTier provides tier) {
            GlassScaffold(
                topBar = {
                    GlassTopBar(title = { GlassTopBarTitle(text = "首页") }, hiddenFraction = { topBarHidden })
                },
                bottomBar = {
                    if (withNavBar) {
                        FloatingNavBar(
                            currentPosition = 0,
                            onChangePosition = {},
                            onReselected = {},
                            navigationItems = navigationItems,
                            collapsedFraction = { collapsed },
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) { padding ->
                LazyColumn(
                    state = rememberLazyListState(initialFirstVisibleItemIndex = firstVisibleItem),
                    contentPadding = padding.plus(PaddingValues(horizontal = 16.dp)),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(20) { index ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp)
                                .background(swatches[index % swatches.size], RoundedCornerShape(14.dp))
                                .padding(16.dp)
                        ) {
                            Text(text = "第 $index 项", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    /** The main page: a scaffold with the navigation pill around a tab's own scaffold. */
    @Test
    fun nestedScaffolds() = captureScreenshot {
        GlassScaffold(
            bottomBar = {
                FloatingNavBar(
                    currentPosition = 0,
                    onChangePosition = {},
                    onReselected = {},
                    navigationItems = navigationItems,
                    collapsedFraction = { 0f },
                )
            },
            backgroundColor = Color.Transparent,
            modifier = Modifier.fillMaxSize(),
        ) { outer ->
            CompositionLocalProvider(
                LocalChromePadding provides PaddingValues(bottom = outer.calculateBottomPadding())
            ) {
                Page(GlassTier.forDevice(), collapsed = 0f, firstVisibleItem = 3, withNavBar = false)
            }
        }
    }

    @Test
    fun atTop() = captureScreenshot { Page(GlassTier.forDevice(), collapsed = 0f, firstVisibleItem = 0) }

    @Test
    fun scrolledBehindBars() =
        captureScreenshot { Page(GlassTier.forDevice(), collapsed = 0f, firstVisibleItem = 3) }

    @Test
    fun navBarCollapsed() =
        captureScreenshot { Page(GlassTier.forDevice(), collapsed = 1f, firstVisibleItem = 3) }

    @Test
    fun topBarHidden() =
        captureScreenshot { Page(GlassTier.forDevice(), collapsed = 0f, firstVisibleItem = 3, topBarHidden = 1f) }

    @Test
    fun solidTier() = captureScreenshot { Page(GlassTier.Solid, collapsed = 0f, firstVisibleItem = 3) }

    @Test
    fun blurTier() = captureScreenshot { Page(GlassTier.Blur, collapsed = 0f, firstVisibleItem = 3) }
}
