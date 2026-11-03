package oolong.client.ui.page.thread

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import oolong.client.model.Poll
import oolong.client.model.PollOption
import oolong.client.testing.LegacyAppRule
import oolong.client.testing.captureScreenshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "+w393dp-h900dp-xxhdpi")
class PollCardScreenshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    private val poll = Poll(
        title = "周末去哪里",
        multiple = false,
        options = persistentListOf(PollOption(1, "爬山", 42), PollOption(2, "看电影", 25), PollOption(3, "在家休息", 9)),
        voters = 76,
        hasVoted = false,
        chosen = persistentSetOf(),
        closed = false,
    )

    @Test
    fun openVotedAndClosed() {
        captureScreenshot {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(16.dp)) {
                PollCard(poll = poll, canVote = true, onVote = {})
                PollCard(poll = poll.copy(multiple = true, hasVoted = true, chosen = persistentSetOf(1, 3)), canVote = true, onVote = {})
                PollCard(poll = poll.copy(closed = true), canVote = true, onVote = {})
            }
        }
    }
}
