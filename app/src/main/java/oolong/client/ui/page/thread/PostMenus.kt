package oolong.client.ui.page.thread

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import oolong.client.R
import oolong.client.model.Post
import oolong.client.model.SubPost
import oolong.client.model.wordText
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.page.settings.block.BlockMenuLabels
import oolong.client.ui.page.settings.block.BlockShortcuts
import oolong.client.ui.page.settings.block.blockMenuItems
import oolong.client.ui.page.settings.block.blockMenuLabels
import oolong.client.ui.widgets.compose.ContextMenuItem

/** How a post looks, beyond the post itself. */
data class PostCardOptions(
    val threadAuthorId: Long,
    val hideBlocked: Boolean = false,
    val hideReply: Boolean = false,
)

/** The labels of the menus of posts and replies, read where they show. */
@Immutable
internal class PostMenuLabels(
    val reply: String,
    val replyInside: String,
    val copy: String,
    val store: String,
    val unstore: String,
    val report: String,
    val delete: String,
    val block: BlockMenuLabels,
)

@Composable
internal fun postMenuLabels() = PostMenuLabels(
    reply = stringResource(id = R.string.btn_reply),
    replyInside = stringResource(id = R.string.title_reply),
    copy = stringResource(id = R.string.menu_copy),
    store = stringResource(id = R.string.title_collect_floor),
    unstore = stringResource(id = R.string.title_collect_on),
    report = stringResource(id = R.string.title_report),
    delete = stringResource(id = R.string.title_delete),
    block = blockMenuLabels(),
)

/** What a long press on a post offers, with blocking its author after when [shortcuts] are there. */
internal fun postMenuItems(
    labels: PostMenuLabels,
    context: Context,
    post: Post,
    hideReply: Boolean,
    isStoreMark: Boolean,
    canStore: Boolean,
    canDelete: Boolean,
    shortcuts: BlockShortcuts?,
    onEvent: (ThreadEvent) -> Unit,
): List<ContextMenuItem> = buildList {
    if (!hideReply) add(ContextMenuItem(Symbols.Reply, labels.reply) { onEvent(ThreadEvent.Reply(post)) })
    add(ContextMenuItem(Symbols.ContentCopy, labels.copy) { onEvent(ThreadEvent.Copy(post.content)) })
    if (canStore) {
        add(
            if (isStoreMark) {
                ContextMenuItem(Symbols.BookmarkRemove, labels.unstore) { onEvent(ThreadEvent.RemoveStore) }
            } else {
                ContextMenuItem(Symbols.Bookmark, labels.store) { onEvent(ThreadEvent.StoreAt(post)) }
            },
        )
    }
    add(ContextMenuItem(Symbols.Flag, labels.report, startsGroup = true) { onEvent(ThreadEvent.Report(post.id)) })
    if (canDelete) add(ContextMenuItem(Symbols.Delete, labels.delete) { onEvent(ThreadEvent.DeletePost(post)) })
    // Blocking from inside a thread leaves its forum out
    if (shortcuts != null) addAll(blockMenuItems(labels.block, shortcuts, post.author, forum = null, text = post.content.wordText))
}

/** What a long press on a reply inside a post offers. */
internal fun subPostMenuItems(
    labels: PostMenuLabels,
    context: Context,
    post: Post,
    subPost: SubPost,
    hideReply: Boolean,
    shortcuts: BlockShortcuts?,
    onEvent: (ThreadEvent) -> Unit,
): List<ContextMenuItem> = buildList {
    if (!hideReply) add(ContextMenuItem(Symbols.Reply, labels.replyInside) { onEvent(ThreadEvent.Reply(post, subPost)) })
    add(ContextMenuItem(Symbols.ContentCopy, labels.copy) { onEvent(ThreadEvent.Copy(subPost.content)) })
    add(ContextMenuItem(Symbols.Flag, labels.report, startsGroup = true) { onEvent(ThreadEvent.Report(subPost.id)) })
    if (shortcuts != null) addAll(blockMenuItems(labels.block, shortcuts, subPost.author, forum = null, text = subPost.content.wordText))
}
