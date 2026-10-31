package com.huanchengfly.tieba.post.ui.page.subposts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.model.Post
import com.huanchengfly.tieba.post.model.SubPost
import com.huanchengfly.tieba.post.model.plainText
import com.huanchengfly.tieba.post.ui.common.PostBody
import com.huanchengfly.tieba.post.ui.page.thread.PostAgreeBtn
import com.huanchengfly.tieba.post.ui.page.thread.UserNameText
import com.huanchengfly.tieba.post.ui.page.thread.bawuName
import com.huanchengfly.tieba.post.ui.widgets.compose.Avatar
import com.huanchengfly.tieba.post.ui.widgets.compose.BlockTip
import com.huanchengfly.tieba.post.ui.widgets.compose.BlockableContent
import com.huanchengfly.tieba.post.ui.widgets.compose.Card
import com.huanchengfly.tieba.post.ui.widgets.compose.LongClickMenu
import com.huanchengfly.tieba.post.ui.widgets.compose.Sizes
import com.huanchengfly.tieba.post.ui.widgets.compose.UserHeader
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberMenuState
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

/** A reply inside the post: author, time and like button, then what they said. */
@Composable
internal fun SubPostCard(
    post: Post,
    item: SubPostItem,
    threadAuthorId: Long,
    canDelete: Boolean,
    hideReply: Boolean,
    onEvent: (SubPostsEvent) -> Unit,
) {
    val subPost = item.subPost
    val menuState = rememberMenuState()
    BlockableContent(
        blocked = item.blocked,
        blockedTip = { BlockTip(text = { Text(text = stringResource(id = R.string.tip_blocked_sub_post)) }) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        LongClickMenu(
            menuState = menuState,
            indication = null,
            menuContent = {
                if (!hideReply) {
                    DropdownMenuItem(
                        onClick = {
                            onEvent(SubPostsEvent.Reply(post, subPost))
                            menuState.expanded = false
                        }
                    ) {
                        Text(text = stringResource(id = R.string.btn_reply))
                    }
                }
                DropdownMenuItem(
                    onClick = {
                        onEvent(SubPostsEvent.Copy(subPost.content.plainText))
                        menuState.expanded = false
                    }
                ) {
                    Text(text = stringResource(id = R.string.menu_copy))
                }
                DropdownMenuItem(
                    onClick = {
                        onEvent(SubPostsEvent.Report(subPost.id))
                        menuState.expanded = false
                    }
                ) {
                    Text(text = stringResource(id = R.string.title_report))
                }
                if (canDelete) {
                    DropdownMenuItem(
                        onClick = {
                            onEvent(SubPostsEvent.Delete(subPost))
                            menuState.expanded = false
                        }
                    ) {
                        Text(text = stringResource(id = R.string.title_delete))
                    }
                }
            },
            onClick = { onEvent(SubPostsEvent.Reply(post, subPost)) }.takeUnless { hideReply }
        ) {
            Card(
                header = { SubPostHeader(subPost, threadAuthorId, onEvent) },
                content = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .padding(start = Sizes.Small + 8.dp)
                            .fillMaxWidth()
                    ) {
                        PostBody(content = subPost.content)
                    }
                }
            )
        }
    }
}

@Composable
private fun SubPostHeader(subPost: SubPost, threadAuthorId: Long, onEvent: (SubPostsEvent) -> Unit) {
    val context = LocalContext.current
    val author = subPost.author
    UserHeader(
        avatar = {
            Avatar(
                data = StringUtil.getAvatarUrl(author.portrait),
                size = Sizes.Small,
                contentDescription = null
            )
        },
        name = {
            UserNameText(
                userName = StringUtil.getUsernameAnnotatedString(context, author.name, author.nameShow),
                userLevel = author.level,
                isLz = author.id == threadAuthorId,
                bawuType = author.bawuName(),
            )
        },
        desc = {
            Text(
                text = listOfNotNull(
                    getRelativeTimeString(context, subPost.time),
                    stringResource(R.string.text_ip_location, author.ipLocation).takeIf { author.ipLocation.isNotEmpty() },
                ).joinToString(" ")
            )
        },
        onClick = { onEvent(SubPostsEvent.OpenUser(author.id)) }
    ) {
        PostAgreeBtn(
            hasAgreed = subPost.agree.agreed,
            agreeNum = subPost.agree.count,
            onClick = { onEvent(SubPostsEvent.AgreeSubPost(subPost.id)) }
        )
    }
}
