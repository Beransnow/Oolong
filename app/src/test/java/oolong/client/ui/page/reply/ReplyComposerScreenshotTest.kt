package oolong.client.ui.page.reply

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import oolong.client.testing.LegacyAppRule
import oolong.client.testing.captureScreenshot
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.core.network.VerificationChallenge
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The composer's inside in the states it shows, on a plain card standing in for its glass. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "+w393dp-h851dp-xxhdpi")
class ReplyComposerScreenshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    private val floor = ReplyTo(name = "AI星空", portrait = "", floor = 2, quote = "希望 Alex 洞穴转正，还有灾变")

    /** Replying to a floor for the first time: the target chip and the risk notice. */
    @Test
    fun firstReply() {
        captureScreenshot { Composer(replyTo = floor, text = "洞穴那个确实，", showNotice = true, hasTail = true) }
    }

    /** Replying to the thread, with pictures picked: the picture strip and both chips. */
    @Test
    fun withPictures() {
        captureScreenshot {
            Composer(
                replyTo = null,
                text = "截图如下",
                state = ReplyState(images = persistentListOf("content://a", "content://b"), originalImages = true),
                hasTail = true,
            )
        }
    }

    /** The server asked to verify: the text stays, with what to do instead. */
    @Test
    fun needsVerification() {
        captureScreenshot {
            Composer(
                replyTo = floor,
                text = "希望洞穴也能转正",
                state = ReplyState(error = ReplyError.Verification(VerificationChallenge.Security(3250021))),
            )
        }
    }

    /** Full screen: the words replied to are quoted and the text takes the room left. */
    @Test
    fun fullScreen() {
        captureScreenshot {
            Composer(
                replyTo = floor,
                text = "我觉得机械动力和农夫乐事都有可能。前者的齿轮、传动这一套和红石很像，官方要做可以直接借鉴；后者的作物种类本来就是原版最缺的。",
                fullScreen = true,
            )
        }
    }

    @Composable
    private fun Composer(
        replyTo: ReplyTo?,
        text: String,
        state: ReplyState = ReplyState(),
        fullScreen: Boolean = false,
        showNotice: Boolean = false,
        hasTail: Boolean = false,
    ) {
        val editor = remember { ReplyEditorState().apply { setText(text) } }
        Box(modifier = Modifier.padding(8.dp)) {
            ReplyComposerContent(
                replyTo = replyTo,
                editor = editor,
                state = state,
                fullScreen = fullScreen,
                showNotice = showNotice,
                hasTail = hasTail,
                canAttachImages = replyTo == null,
                onEvent = {},
                handle = { Box(modifier = Modifier.fillMaxWidth().height(if (fullScreen) 28.dp else 16.dp)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (fullScreen) Modifier.height(600.dp) else Modifier)
                    .clip(RoundedCornerShape(24.dp))
                    .background(ExtendedTheme.colors.sheet),
            )
        }
    }
}
