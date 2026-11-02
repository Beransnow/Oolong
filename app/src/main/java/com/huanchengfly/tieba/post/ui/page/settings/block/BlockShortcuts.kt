package com.huanchengfly.tieba.post.ui.page.settings.block

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.ApplicationScope
import com.huanchengfly.tieba.post.model.BlockRule
import com.huanchengfly.tieba.post.repository.BlockListRepository
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.Spacing
import com.huanchengfly.tieba.post.ui.common.theme.compose.TextStyles
import com.huanchengfly.tieba.post.ui.widgets.compose.PromptDialog
import com.huanchengfly.tieba.post.ui.widgets.compose.TextButton
import com.huanchengfly.tieba.post.ui.widgets.compose.blockReason
import com.huanchengfly.tieba.post.ui.widgets.compose.floatingGlass
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberDialogState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Blocking from where the content is: a rule added at once with every default, a toast to take
 * it back, and a prompt for keywords. [BlockShortcutsHost] shows the toast and the prompt.
 */
@Singleton
class BlockShortcuts @Inject constructor(
    private val repository: BlockListRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** What the toast says, while it shows. */
    internal var notice by mutableStateOf<Notice?>(null)
        private set

    /** What the keyword prompt starts with, while it shows. */
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
        block(BlockRule(match = BlockRule.Match.User(uid, name, displayName.takeIf { it != name }.orEmpty())))

    fun blockForum(name: String) = block(BlockRule(match = BlockRule.Match.Forum(name.removeSuffix("吧"))))

    /** Asks for keywords, starting from [text], and blocks them. */
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

/** The toast after blocking and the keyword prompt, over the whole window. */
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
        AnimatedVisibility(
            visible = notice != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            if (shown != null) UndoToast(shown, onUndo = { shortcuts.undo(shown) })
        }
    }
    val prompt = shortcuts.keywordPrompt
    if (prompt != null) {
        val state = rememberDialogState()
        LaunchedEffect(prompt) { state.show() }
        PromptDialog(
            dialogState = state,
            initialValue = prompt,
            title = { Text(text = stringResource(id = R.string.title_block_keywords)) },
            confirmText = stringResource(id = R.string.button_block),
            onConfirm = { text ->
                val words = text.split(' ', '　').filter { it.isNotBlank() }
                if (words.isNotEmpty()) shortcuts.block(BlockRule(match = BlockRule.Match.Keyword(words)))
                shortcuts.keywordPrompt = null
            },
            onCancel = { shortcuts.keywordPrompt = null },
        ) {
            Text(text = stringResource(id = R.string.tip_block_keywords))
        }
    }
}

/** A toast (design-system.md §9.3): a glass capsule above the bottom bar, with 撤销. */
@Composable
private fun UndoToast(notice: BlockShortcuts.Notice, onUndo: () -> Unit) {
    val colors = ExtendedTheme.colors
    val text = when (notice) {
        is BlockShortcuts.Notice.Blocked -> stringResource(id = R.string.toast_blocked, blockReason(notice.rule))
        is BlockShortcuts.Notice.Text -> notice.text
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = Spacing.md)
            .padding(bottom = ToastBottomGap)
            .widthIn(max = 480.dp)
            .heightIn(min = 48.dp)
            .floatingGlass(ToastShape)
            .padding(start = Spacing.lg, end = Spacing.xxs),
    ) {
        Text(
            text = text,
            style = TextStyles.label,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        TextButton(onClick = onUndo) {
            Text(text = stringResource(id = R.string.button_undo), style = TextStyles.label, color = colors.accent)
        }
    }
}

private val ToastShape = RoundedCornerShape(percent = 50)

/** Clear of the bottom bar, which floats about this high over the navigation bar. */
private val ToastBottomGap = 88.dp

private const val UndoMillis = 4_000L
