package oolong.client.ui.widgets.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oolong.client.testing.LegacyAppRule
import oolong.client.testing.captureScreenshot
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "+w393dp-h200dp-xxhdpi")
class VoicePlayerScreenshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    @After
    fun stop() {
        VoicePlayback.stop()
    }

    @Test
    fun playedPartly() {
        VoicePlayback.url = "https://example.com/half"
        VoicePlayback.state = VoiceState.Playing
        VoicePlayback.position = 9_000
        captureScreenshot {
            Column(modifier = Modifier.padding(16.dp)) {
                VoicePlayer(url = "https://example.com/half", duration = 23_000)
            }
        }
    }

    @Test
    fun shortAndLong() {
        captureScreenshot {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
                VoicePlayer(url = "https://example.com/short", duration = 4_000)
                VoicePlayer(url = "https://example.com/half", duration = 23_000)
                VoicePlayer(url = "https://example.com/long", duration = 75_000)
            }
        }
    }
}
