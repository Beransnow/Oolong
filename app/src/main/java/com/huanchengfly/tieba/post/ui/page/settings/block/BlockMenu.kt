package com.huanchengfly.tieba.post.ui.page.settings.block

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.model.ThreadSummary
import com.huanchengfly.tieba.post.model.User
import com.huanchengfly.tieba.post.ui.widgets.compose.ContextMenuItem

/** The words of the block menu items, read where composition can. */
@Immutable
class BlockMenuLabels(
    val block: String,
    /** With the user's name for `%1$s`. */
    val user: String,
    /** With the forum's name without 吧 for `%1$s`. */
    val forum: String,
    val keywords: String,
)

@Composable
fun blockMenuLabels() = BlockMenuLabels(
    block = stringResource(id = R.string.menu_block),
    user = stringResource(id = R.string.menu_block_user_item),
    forum = stringResource(id = R.string.menu_block_forum_item),
    keywords = stringResource(id = R.string.menu_block_keywords_item),
)

/**
 * Blocking [thread]'s author, its forum unless [forumOffered] already, or keywords starting from
 * its title, as one context menu item in a group of its own that opens the choices in place.
 */
fun blockMenuItems(labels: BlockMenuLabels, shortcuts: BlockShortcuts, thread: ThreadSummary, forumOffered: Boolean = false): List<ContextMenuItem> =
    blockMenuItems(labels, shortcuts, thread.author, thread.forum.name.takeUnless { forumOffered }, thread.title)

/**
 * Blocking [author], the [forum] if given, or keywords starting from [text], as one context menu
 * item in a group of its own that opens the choices in place.
 */
fun blockMenuItems(labels: BlockMenuLabels, shortcuts: BlockShortcuts, author: User?, forum: String?, text: String): List<ContextMenuItem> {
    val choices = buildList {
        if (author != null && (author.id != 0L || author.name.isNotBlank())) {
            add(ContextMenuItem(Icons.Rounded.PersonOff, labels.user.format(author.displayName)) {
                shortcuts.blockUser(author.id, author.name, author.displayName)
            })
        }
        if (!forum.isNullOrBlank()) {
            add(ContextMenuItem(Icons.Rounded.Forum, labels.forum.format(forum.removeSuffix("吧"))) {
                shortcuts.blockForum(forum)
            })
        }
        add(ContextMenuItem(Icons.Rounded.TextFields, labels.keywords) {
            shortcuts.askKeywords(text)
        })
    }
    return listOf(ContextMenuItem(Icons.Rounded.Block, labels.block, startsGroup = true, children = choices))
}
