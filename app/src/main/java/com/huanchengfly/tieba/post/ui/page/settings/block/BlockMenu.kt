package com.huanchengfly.tieba.post.ui.page.settings.block

import android.content.Context
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.model.ThreadSummary
import com.huanchengfly.tieba.post.model.User
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.widgets.compose.ContextMenuItem
import com.huanchengfly.tieba.post.ui.widgets.compose.MenuState

/**
 * A menu's own [items] and 屏蔽… after them, which turns the menu into the ways to block what it
 * is about: its [author], its [forum], or keywords starting from [text].
 */
@Composable
fun ColumnScope.BlockableMenuItems(
    author: User?,
    forum: String?,
    text: String,
    menuState: MenuState,
    items: @Composable ColumnScope.() -> Unit,
) {
    val shortcuts = LocalBlockShortcuts.current
    // Back to the menu's own items each time it opens
    var blocking by remember(menuState.expanded) { mutableStateOf(false) }
    if (shortcuts == null || !blocking) {
        items()
        if (shortcuts != null) {
            DropdownMenuItem(onClick = { blocking = true }) {
                Text(text = stringResource(id = R.string.menu_block))
            }
        }
        return
    }
    fun close() {
        menuState.expanded = false
    }
    DropdownMenuItem(onClick = { blocking = false }) {
        Text(text = "‹ " + stringResource(id = R.string.button_block), color = ExtendedTheme.colors.textSecondary)
    }
    if (author != null && (author.id != 0L || author.name.isNotBlank())) {
        DropdownMenuItem(onClick = { shortcuts.blockUser(author.id, author.name, author.displayName).also { close() } }) {
            Text(text = stringResource(id = R.string.menu_block_user, author.displayName))
        }
    }
    if (!forum.isNullOrBlank()) {
        DropdownMenuItem(onClick = { shortcuts.blockForum(forum).also { close() } }) {
            Text(text = stringResource(id = R.string.menu_block_forum, forum.removeSuffix("吧")))
        }
    }
    DropdownMenuItem(onClick = { shortcuts.askKeywords(text.take(KeywordPrefill)).also { close() } }) {
        Text(text = stringResource(id = R.string.menu_block_keywords))
    }
}

/**
 * Blocking [thread]'s author, its forum unless [forumOffered] already, or keywords starting from
 * its title, as context menu items in a group of their own.
 */
fun blockMenuItems(context: Context, shortcuts: BlockShortcuts, thread: ThreadSummary, forumOffered: Boolean = false): List<ContextMenuItem> =
    buildList {
        val author = thread.author
        if (author.id != 0L || author.name.isNotBlank()) {
            add(ContextMenuItem(Icons.Rounded.PersonOff, context.getString(R.string.menu_block_user, author.displayName)) {
                shortcuts.blockUser(author.id, author.name, author.displayName)
            })
        }
        if (!forumOffered && thread.forum.name.isNotBlank()) {
            add(ContextMenuItem(Icons.Rounded.Block, context.getString(R.string.menu_block_forum, thread.forum.name.removeSuffix("吧"))) {
                shortcuts.blockForum(thread.forum.name)
            })
        }
        add(ContextMenuItem(Icons.Rounded.TextFields, context.getString(R.string.menu_block_keywords)) {
            shortcuts.askKeywords(thread.title)
        })
    }.mapIndexed { i, item -> if (i == 0) item.copy(startsGroup = true) else item }

/** How much of the content the keyword prompt starts with. */
private const val KeywordPrefill = 12
