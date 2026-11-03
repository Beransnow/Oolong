package oolong.client.ui.page.settings.block

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.arch.ApplicationScope
import oolong.client.model.BlockRule
import oolong.client.repository.BlockListRepository
import oolong.client.ui.widgets.compose.GlassToast
import oolong.client.ui.widgets.compose.blockReason
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Blocking from where the content is: a rule added at once with every default, a toast to take
 * it back, and a sheet to pick keywords. [BlockShortcutsHost] shows the toast and the sheet.
 */
@Singleton
class BlockShortcuts @Inject constructor(
    private val repository: BlockListRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** What the toast says, while it shows. */
    internal var notice by mutableStateOf<Notice?>(null)
        private set

    /** What the keyword sheet picks words out of, while it shows. */
    internal var keywordPrompt by mutableStateOf<String?>(null)

    /** A toast with a way to take back what it reports. */
    internal sealed interface Notice {
        val undo: () -> Unit

        /** [rule] was just added. */
        class Blocked(val rule: BlockRule, override val undo: () -> Unit) : Notice

        class Text(val text: String, override val undo: () -> Unit) : Notice
    }

    fun block(rule: BlockRule) {
        scope.launch {
            val id = repository.add(rule)
            notice = Notice.Blocked(rule) { scope.launch { repository.remove(listOf(id)) } }
        }
    }

    fun blockUser(uid: Long, name: String, displayName: String = "") =
        block(BlockRule(condition = BlockRule.Condition.User(uid, name, displayName.takeIf { it != name }.orEmpty())))

    fun blockForum(name: String) = block(BlockRule(condition = BlockRule.Condition.Forum(name.removeSuffix("吧"))))

    /** Asks for keywords, picked out of [text], and blocks them. */
    fun askKeywords(text: String) {
        keywordPrompt = text
    }

    /** Says [text] with a way to [undo] what it reports. */
    fun showUndo(text: String, undo: () -> Unit) {
        notice = Notice.Text(text, undo)
    }

    internal fun undo(notice: Notice) {
        dismiss(notice)
        notice.undo()
    }

    internal fun dismiss(notice: Notice) {
        if (this.notice === notice) this.notice = null
    }
}

val LocalBlockShortcuts = staticCompositionLocalOf<BlockShortcuts?> { null }

/** The toast after blocking and the keyword sheet, over the whole window. */
@Composable
fun BlockShortcutsHost(shortcuts: BlockShortcuts) {
    Box(modifier = Modifier.fillMaxSize()) {
        val notice = shortcuts.notice
        LaunchedEffect(notice) {
            if (notice != null) {
                delay(UndoMillis)
                shortcuts.dismiss(notice)
            }
        }
        // Kept through the exit, when the notice is gone already
        val shown = remember { mutableStateOf(notice) }.apply { if (notice != null) value = notice }.value
        if (shown != null) {
            GlassToast(
                visible = notice != null,
                text = when (shown) {
                    is BlockShortcuts.Notice.Blocked -> stringResource(id = R.string.toast_blocked, blockReason(shown.rule))
                    is BlockShortcuts.Notice.Text -> shown.text
                },
                action = stringResource(id = R.string.button_undo),
                onAction = { shortcuts.undo(shown) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = ToastBottomGap),
            )
        }
    }
    val prompt = shortcuts.keywordPrompt
    if (prompt != null) {
        KeywordSheet(
            text = prompt,
            onBlock = { words -> shortcuts.block(BlockRule(condition = BlockRule.Condition.Text(BlockRule.Pattern.Words(words)))) },
            onDismiss = { shortcuts.keywordPrompt = null },
        )
    }
}

/** Clear of the bottom bar, which floats about this high over the navigation bar. */
private val ToastBottomGap = 88.dp

private const val UndoMillis = 4_000L
