package oolong.client.ui.page.main.explore

import oolong.client.testing.LegacyAppRule
import oolong.client.testing.captureScreenshot
import oolong.client.ui.page.main.explore.concern.ConcernScreen
import oolong.client.ui.page.main.explore.hot.HotScreen
import oolong.client.ui.page.main.explore.personalized.PersonalizedScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "+w393dp-h851dp-xxhdpi")
class ExploreScreenshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    @Test
    fun concern() {
        captureScreenshot {
            ConcernScreen(list = ExplorePreviewData.concern, events = FeedThreadEvents(), onRefresh = {}, onLoadMore = {})
        }
    }

    @Test
    fun personalized() {
        captureScreenshot {
            PersonalizedScreen(
                state = ExplorePreviewData.personalized,
                events = FeedThreadEvents(),
                onRefresh = {},
                onLoadMore = {},
                onDislike = { _, _, _ -> },
            )
        }
    }

    @Test
    fun hot() {
        captureScreenshot {
            HotScreen(
                state = ExplorePreviewData.hot,
                events = FeedThreadEvents(),
                onRefresh = {},
                onSelectThreadMeasure = {},
                onSelectForumMeasure = {},
                onOpenTopics = {},
                onOpenTopic = {},
                onOpenForum = {},
                onFollowForum = {},
            )
        }
    }
}
