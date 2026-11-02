package com.huanchengfly.tieba.post.ui.page.subposts

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.model.Post
import com.huanchengfly.tieba.post.model.SubPost
import com.huanchengfly.tieba.post.model.plainText
import com.huanchengfly.tieba.post.model.wordText
import com.huanchengfly.tieba.post.ui.common.PostBody
import com.huanchengfly.tieba.post.ui.page.settings.block.BlockMenuLabels
import com.huanchengfly.tieba.post.ui.page.settings.block.BlockShortcuts
import com.huanchengfly.tieba.post.ui.page.settings.block.LocalBlockShortcuts
import com.huanchengfly.tieba.post.ui.page.settings.block.blockMenuItems
import com.huanchengfly.tieba.post.ui.page.settings.block.blockMenuLabels
import com.huanchengfly.tieba.post.ui.page.thread.PostMenuLabels
import com.huanchengfly.tieba.post.ui.page.thread.SubPostBranch
import com.huanchengfly.tieba.post.ui.page.thread.SubPostLine
import com.huanchengfly.tieba.post.ui.page.thread.UserNameText
import com.huanchengfly.tieba.post.ui.page.thread.bawuName
import com.huanchengfly.tieba.post.ui.page.thread.floorIndent
import com.huanchengfly.tieba.post.ui.page.thread.floorRuleColor
import com.huanchengfly.tieba.post.ui.page.thread.postMenuLabels
import com.huanchengfly.tieba.post.ui.widgets.compose.Avatar
import com.huanchengfly.tieba.post.ui.widgets.compose.BlockTip
import com.huanchengfly.tieba.post.ui.widgets.compose.BlockableContent
import com.huanchengfly.tieba.post.ui.widgets.compose.Card
import com.huanchengfly.tieba.post.ui.widgets.compose.ContextMenuItem
import com.huanchengfly.tieba.post.ui.widgets.compose.Sizes
import com.huanchengfly.tieba.post.ui.widgets.compose.UserHeader
import com.huanchengfly.tieba.post.utils.DateTimeUtils.getRelativeTimeString
import com.huanchengfly.tieba.post.utils.StringUtil

/** Everything the user can do on the sub-post page. */
sealed interface SubPostsEvent {
    data object Back : SubPostsEvent
    data object Retry : SubPostsEvent
    data object LoadMore : SubPostsEvent
    data object LoadPrevious : SubPostsEvent
    data object OpenThread : SubPostsEvent
    data object AgreePost : SubPostsEvent
    data class AgreeSubPost(val subPostId: Long) : SubPostsEvent
    data class OpenUser(val userId: Long) : SubPostsEvent

    /** Reply in the post, to its author when [subPost] is null or to the author of [subPost]. */
    data class Reply(val post: Post, val subPost: SubPost? = null) : SubPostsEvent

    /** Reply in the post without replying to anyone in particular. */
    data object ReplyToPost : SubPostsEvent
    data class Copy(val text: String) : SubPostsEvent
    data class Report(val postId: Long) : SubPostsEvent

    /** Delete [subPost], or the post itself when null. */
    data class Delete(val subPost: SubPost?) : SubPostsEvent
}

/**
 * A reply inside the post, as a floor shows its replies but in full: on a branch off the rule that
 * runs down from the post, the last one ending it.
 */
@Composable
internal fun SubPostReply(
    post: Post,
    item: SubPostItem,
    threadAuthorId: Long,
    canDelete: Boolean,
    hideReply: Boolean,
    last: Boolean,
    onEvent: (SubPostsEvent) -> Unit,
) {
    val subPost = item.subPost
    val context = LocalContext.current
    val shortcuts = LocalBlockShortcuts.current
    val blockLabels = blockMenuLabels()
    BlockableContent(
        block = item.block,
        modifier = Modifier.fillMaxWidth().floorIndent(),
    ) {
        // BlockableContent only lays out its tip with the modifier, so the reply takes its own
        SubPostBranch(rule = floorRuleColor(), modifier = Modifier.fillMaxWidth().floorIndent(), rail = true, last = last) {
            SubPostLine(
                subPost = subPost,
                isLz = subPost.author.id == threadAuthorId,
                onClick = { onEvent(SubPostsEvent.Reply(post, subPost)) }.takeUnless { hideReply },
                menuItems = { replyMenuItems(context, blockLabels, post, subPost, hideReply, canDelete, shortcuts, onEvent) },
                onAgree = { onEvent(SubPostsEvent.AgreeSubPost(subPost.id)) },
            )
        }
    }
}

/** What a long press on a reply offers here, with blocking its author after when [shortcuts] are there. */
private fun replyMenuItems(
    context: Context,
    blockLabels: BlockMenuLabels,
    post: Post,
    subPost: SubPost,
    hideReply: Boolean,
    canDelete: Boolean,
    shortcuts: BlockShortcuts?,
    onEvent: (SubPostsEvent) -> Unit,
): List<ContextMenuItem> = buildList {
    if (!hideReply) {
        add(ContextMenuItem(Icons.AutoMirrored.Rounded.Reply, context.getString(R.string.btn_reply)) { onEvent(SubPostsEvent.Reply(post, subPost)) })
    }
    add(ContextMenuItem(Icons.Rounded.ContentCopy, context.getString(R.string.menu_copy)) { onEvent(SubPostsEvent.Copy(subPost.content.plainText)) })
    add(ContextMenuItem(Icons.Rounded.Flag, context.getString(R.string.title_report), startsGroup = true) { onEvent(SubPostsEvent.Report(subPost.id)) })
    if (canDelete) add(ContextMenuItem(Icons.Rounded.Delete, context.getString(R.string.title_delete)) { onEvent(SubPostsEvent.Delete(subPost)) })
    if (shortcuts != null) addAll(blockMenuItems(blockLabels, shortcuts, subPost.author, forum = null, text = subPost.content.wordText))
}
