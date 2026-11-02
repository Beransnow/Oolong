package com.huanchengfly.tieba.post.ui.page.settings.block

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.TextFields
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.model.ThreadSummary
import com.huanchengfly.tieba.post.model.User
import com.huanchengfly.tieba.post.ui.widgets.compose.ContextMenuItem

/**
 * Blocking [thread]'s author, its forum unless [forumOffered] already, or keywords starting from
 * its title, as context menu items in a group of their own.
 */
fun blockMenuItems(context: Context, shortcuts: BlockShortcuts, thread: ThreadSummary, forumOffered: Boolean = false): List<ContextMenuItem> =
    blockMenuItems(context, shortcuts, thread.author, thread.forum.name.takeUnless { forumOffered }, thread.title)

/**
 * Blocking [author], the [forum] if given, or keywords starting from [text], as context menu items
 * in a group of their own.
 */
fun blockMenuItems(context: Context, shortcuts: BlockShortcuts, author: User?, forum: String?, text: String): List<ContextMenuItem> =
    buildList {
        if (author != null && (author.id != 0L || author.name.isNotBlank())) {
            add(ContextMenuItem(Icons.Rounded.PersonOff, context.getString(R.string.menu_block_user, author.displayName)) {
                shortcuts.blockUser(author.id, author.name, author.displayName)
            })
        }
        if (!forum.isNullOrBlank()) {
            add(ContextMenuItem(Icons.Rounded.Block, context.getString(R.string.menu_block_forum, forum.removeSuffix("吧"))) {
                shortcuts.blockForum(forum)
            })
        }
        add(ContextMenuItem(Icons.Rounded.TextFields, context.getString(R.string.menu_block_keywords)) {
            shortcuts.askKeywords(text)
        })
    }.mapIndexed { i, item -> if (i == 0) item.copy(startsGroup = true) else item }
