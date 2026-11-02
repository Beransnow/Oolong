package com.huanchengfly.tieba.post.ui.page.settings.block

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.ApplicationScope
import com.huanchengfly.tieba.post.model.BlockRule
import com.huanchengfly.tieba.post.repository.BlockListRepository
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.TextStyles
import com.huanchengfly.tieba.post.ui.widgets.compose.PromptDialog
import com.huanchengfly.tieba.post.ui.widgets.compose.TextButton
import com.huanchengfly.tieba.post.ui.widgets.compose.blockReason
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberDialogState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Blocking from where the content is: a rule added at once with every default, a bar to take it
 * back, and a prompt for keywords. [BlockShortcutsHost] shows the bar and the prompt.
 */
@Singleton
class BlockShortcuts @Inject constructor(
    private val repository: BlockListRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** The rule just added, which the bar offers to take back. */
    internal var added by mutableStateOf<AddedRule?>(null)
        private set

    /** What the keyword prompt starts with, while it shows. */
    internal var keywordPrompt by mutableStateOf<String?>(null)

    internal class AddedRule(val rule: BlockRule, val id: Long)

    fun block(rule: BlockRule) {
        scope.launch {
            val id = repository.add(rule)
            added = AddedRule(rule, id)
        }
    }

    fun blockUser(uid: Long, name: String, displayName: String = "") =
        block(BlockRule(match = BlockRule.Match.User(uid, name, displayName.takeIf { it != name }.orEmpty())))

    fun blockForum(name: String) = block(BlockRule(match = BlockRule.Match.Forum(name.removeSuffix("吧"))))

    /** Asks for keywords, starting from [text], and blocks them. */
    fun askKeywords(text: String) {
        keywordPrompt = text
    }

    internal fun undo(rule: AddedRule) {
        if (added === rule) added = null
        scope.launch { repository.remove(rule.id) }
    }

    internal fun dismiss(rule: AddedRule) {
        if (added === rule) added = null
    }
}

val LocalBlockShortcuts = staticCompositionLocalOf<BlockShortcuts?> { null }

/** The bar after blocking and the keyword prompt, over the whole window. */
@Composable
fun BlockShortcutsHost(shortcuts: BlockShortcuts) {
    Box(modifier = Modifier.fillMaxSize()) {
        val added = shortcuts.added
        LaunchedEffect(added) {
            if (added != null) {
                delay(UndoMillis)
                shortcuts.dismiss(added)
            }
        }
        // Kept through the exit, when the rule is gone already
        val shown = remember { mutableStateOf(added) }.apply { if (added != null) value = added }.value
        AnimatedVisibility(
            visible = added != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            if (shown != null) UndoBar(shown, onUndo = { shortcuts.undo(shown) })
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

@Composable
private fun UndoBar(added: BlockShortcuts.AddedRule, onUndo: () -> Unit) {
    val colors = ExtendedTheme.colors
    val shape = RoundedCornerShape(percent = 50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(16.dp)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .shadow(12.dp, shape)
            .clip(shape)
            .background(colors.sheet)
            .padding(start = 20.dp, end = 4.dp),
    ) {
        Text(
            text = stringResource(id = R.string.toast_blocked, blockReason(added.rule)),
            style = TextStyles.label,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onUndo) {
            Text(text = stringResource(id = R.string.button_undo), color = colors.accent)
        }
    }
}

private const val UndoMillis = 4_000L
