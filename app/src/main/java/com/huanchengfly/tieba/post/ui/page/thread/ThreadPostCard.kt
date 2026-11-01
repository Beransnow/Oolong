package com.huanchengfly.tieba.post.ui.page.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.model.Post
import com.huanchengfly.tieba.post.model.PostContent
import com.huanchengfly.tieba.post.model.SubPost
import com.huanchengfly.tieba.post.model.plainText
import com.huanchengfly.tieba.post.models.PhotoViewData
import com.huanchengfly.tieba.post.ui.common.PbContentText
import com.huanchengfly.tieba.post.ui.common.PostBody
import com.huanchengfly.tieba.post.ui.common.rememberSubPostText
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.widgets.compose.Avatar
import com.huanchengfly.tieba.post.ui.widgets.compose.BlockTip
import com.huanchengfly.tieba.post.ui.widgets.compose.BlockableContent
import com.huanchengfly.tieba.post.ui.widgets.compose.Card
import com.huanchengfly.tieba.post.ui.widgets.compose.Chip
import com.huanchengfly.tieba.post.ui.widgets.compose.LongClickMenu
import com.huanchengfly.tieba.post.ui.widgets.compose.MenuState
import com.huanchengfly.tieba.post.ui.widgets.compose.Sizes
import com.huanchengfly.tieba.post.ui.widgets.compose.UserHeader
import com.huanchengfly.tieba.post.ui.widgets.compose.buildChipInlineContent
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberMenuState
import com.huanchengfly.tieba.post.utils.DateTimeUtils.getRelativeTimeString
import com.huanchengfly.tieba.post.utils.StringUtil

/** How a post card looks, beyond the post itself. */
data class PostCardOptions(
    val threadAuthorId: Long,
    val immersive: Boolean = false,
    val hideBlocked: Boolean = false,
    val hideReply: Boolean = false,
)

/**
 * A post of the thread: author, floor and like button, then the content and, below it, the first
 * few replies inside it. A long press opens its menu.
 *
 * @param isStoreMark whether the thread store marks the thread as read up to this post
 * @param canDelete whether the user may delete the post
 * @param canStore whether the menu offers to mark the thread store at this post
 */
@Composable
fun ThreadPostCard(
    item: PostItem,
    options: PostCardOptions,
    onEvent: (ThreadEvent) -> Unit,
    modifier: Modifier = Modifier,
    isStoreMark: Boolean = false,
    canDelete: Boolean = false,
    canStore: Boolean = true,
    showSubPosts: Boolean = true,
    photoViewData: (PostContent.Image) -> PhotoViewData? = { null },
) {
    val post = item.post
    val indented = post.floor > 1 && !options.immersive
    val indent = Modifier.padding(start = if (indented) Sizes.Small + 8.dp else 0.dp)
    val menuState = rememberMenuState()
    BlockableContent(
        blocked = item.blocked,
        blockedTip = {
            BlockTip {
                Text(text = stringResource(id = R.string.tip_blocked_post, post.floor))
            }
        },
        hideBlockedContent = options.hideBlocked || options.immersive,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp)
    ) {
        LongClickMenu(
            menuState = menuState,
            indication = null,
            onClick = { onEvent(ThreadEvent.Reply(post)) }.takeIf { !options.hideReply },
            menuContent = {
                PostMenuItems(post, options.hideReply, isStoreMark, canStore, canDelete, menuState, onEvent)
            }
        ) {
            Card(
                header = {
                    if (!options.immersive) {
                        PostHeader(post, options.threadAuthorId, onEvent)
                    }
                },
                content = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = indent.fillMaxWidth()
                    ) {
                        if (post.title.isNotBlank()) {
                            Text(
                                text = post.title,
                                style = MaterialTheme.typography.subtitle1,
                                fontSize = 15.sp
                            )
                        }
                        if (isStoreMark) {
                            Chip(
                                text = stringResource(id = R.string.title_collected_floor),
                                invertColor = true,
                                prefixIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                        PostBody(content = post.content, photoViewData = photoViewData)
                    }

                    if (showSubPosts && post.subPostCount > 0 && post.subPosts.isNotEmpty() && !options.immersive) {
                        SubPosts(item, options, onEvent, indent)
                    }
                }
            )
        }
    }
}

@Composable
private fun PostHeader(post: Post, threadAuthorId: Long, onEvent: (ThreadEvent) -> Unit) {
    val author = post.author
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
                userName = StringUtil.getUsernameAnnotatedString(LocalContext.current, author.name, author.nameShow),
                userLevel = author.level,
                isLz = author.id == threadAuthorId,
                bawuType = author.bawuName(),
            )
        },
        desc = { Text(text = postDescription(post)) },
        onClick = { onEvent(ThreadEvent.OpenUser(author.id)) }
    ) {
        if (post.floor > 1) {
            PostAgreeBtn(
                hasAgreed = post.agree.agreed,
                agreeNum = post.agree.count,
                onClick = { onEvent(ThreadEvent.AgreePost(post.id)) }
            )
        }
    }
}

/** When it was posted, on which floor and from where. */
@Composable
private fun postDescription(post: Post): String {
    val context = LocalContext.current
    return listOfNotNull(
        getRelativeTimeString(context, post.time),
        stringResource(R.string.tip_post_floor, post.floor).takeIf { post.floor > 1 },
        stringResource(R.string.text_ip_location, post.author.ipLocation).takeIf { post.author.ipLocation.isNotEmpty() },
    ).joinToString(" · ")
}

@Composable
private fun SubPosts(
    item: PostItem,
    options: PostCardOptions,
    onEvent: (ThreadEvent) -> Unit,
    indent: Modifier,
) {
    val post = item.post
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(indent)
            .clip(RoundedCornerShape(6.dp))
            .background(ExtendedTheme.colors.floorCard)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        post.subPosts.forEach { subPost ->
            BlockableContent(
                blocked = subPost.id in item.blockedSubPosts,
                blockedTip = {
                    Text(
                        text = stringResource(id = R.string.tip_blocked_sub_post),
                        style = MaterialTheme.typography.body2.copy(
                            color = ExtendedTheme.colors.textDisabled,
                            fontSize = 13.sp
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                },
            ) {
                SubPostItem(
                    post = post,
                    subPost = subPost,
                    isLz = subPost.author.id == options.threadAuthorId,
                    hideReply = options.hideReply,
                    onEvent = onEvent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                )
            }
        }

        if (post.subPostCount > post.subPosts.size) {
            Text(
                text = stringResource(id = R.string.open_all_sub_posts, post.subPostCount),
                style = MaterialTheme.typography.caption,
                fontSize = 13.sp,
                color = ExtendedTheme.colors.accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .clickable { onEvent(ThreadEvent.OpenSubPosts(post, subPostId = 0)) }
                    .padding(horizontal = 12.dp)
            )
        }
    }
}

@Composable
private fun SubPostItem(
    post: Post,
    subPost: SubPost,
    isLz: Boolean,
    hideReply: Boolean,
    onEvent: (ThreadEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val menuState = rememberMenuState()
    LongClickMenu(
        menuState = menuState,
        menuContent = { SubPostMenuItems(post, subPost, hideReply, menuState, onEvent) },
        shape = RoundedCornerShape(0),
        onClick = { onEvent(ThreadEvent.OpenSubPosts(post, subPost.id)) }
    ) {
        ProvideTextStyle(value = MaterialTheme.typography.body2.copy(color = ExtendedTheme.colors.text)) {
            PbContentText(
                text = rememberSubPostText(subPost, isLz),
                modifier = modifier,
                fontSize = 13.sp,
                emoticonSize = 0.9f,
                overflow = TextOverflow.Ellipsis,
                maxLines = 4,
                lineSpacing = 0.4.sp,
                inlineContent = mapOf(
                    "Lz" to buildChipInlineContent(
                        stringResource(id = R.string.tip_lz),
                        backgroundColor = ExtendedTheme.colors.textSecondary.copy(alpha = 0.1f),
                        color = ExtendedTheme.colors.textSecondary
                    ),
                )
            )
        }
    }
}

/** What a long press on a post offers. */
@Composable
internal fun ColumnScope.PostMenuItems(
    post: Post,
    hideReply: Boolean,
    isStoreMark: Boolean,
    canStore: Boolean,
    canDelete: Boolean,
    menuState: MenuState,
    onEvent: (ThreadEvent) -> Unit,
) {
    if (!hideReply) {
        DropdownMenuItem(
            onClick = {
                onEvent(ThreadEvent.Reply(post))
                menuState.expanded = false
            }
        ) {
            Text(text = stringResource(id = R.string.btn_reply))
        }
    }
    DropdownMenuItem(
        onClick = {
            onEvent(ThreadEvent.Copy(post.content.plainText))
            menuState.expanded = false
        }
    ) {
        Text(text = stringResource(id = R.string.menu_copy))
    }
    DropdownMenuItem(
        onClick = {
            onEvent(ThreadEvent.Report(post.id))
            menuState.expanded = false
        }
    ) {
        Text(text = stringResource(id = R.string.title_report))
    }
    if (canStore) {
        DropdownMenuItem(
            onClick = {
                onEvent(if (isStoreMark) ThreadEvent.RemoveStore else ThreadEvent.StoreAt(post))
                menuState.expanded = false
            }
        ) {
            Text(
                text = stringResource(
                    id = if (isStoreMark) R.string.title_collect_on else R.string.title_collect_floor
                )
            )
        }
    }
    if (canDelete) {
        DropdownMenuItem(
            onClick = {
                onEvent(ThreadEvent.DeletePost(post))
                menuState.expanded = false
            }
        ) {
            Text(text = stringResource(id = R.string.title_delete))
        }
    }
}

/** What a long press on a reply inside a post offers. */
@Composable
internal fun ColumnScope.SubPostMenuItems(
    post: Post,
    subPost: SubPost,
    hideReply: Boolean,
    menuState: MenuState,
    onEvent: (ThreadEvent) -> Unit,
) {
    if (!hideReply) {
        DropdownMenuItem(
            onClick = {
                onEvent(ThreadEvent.Reply(post, subPost))
                menuState.expanded = false
            }
        ) {
            Text(text = stringResource(id = R.string.title_reply))
        }
    }
    DropdownMenuItem(
        onClick = {
            onEvent(ThreadEvent.Copy(subPost.content.plainText))
            menuState.expanded = false
        }
    ) {
        Text(text = stringResource(id = R.string.menu_copy))
    }
    DropdownMenuItem(
        onClick = {
            onEvent(ThreadEvent.Report(subPost.id))
            menuState.expanded = false
        }
    ) {
        Text(text = stringResource(id = R.string.title_report))
    }
}
