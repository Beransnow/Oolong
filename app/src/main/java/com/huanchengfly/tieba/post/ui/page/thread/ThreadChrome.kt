package com.huanchengfly.tieba.post.ui.page.thread

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.model.Agree
import com.huanchengfly.tieba.post.model.ForumSummary
import com.huanchengfly.tieba.post.model.User
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.loadMoreIndicator
import com.huanchengfly.tieba.post.ui.widgets.compose.Avatar
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBar
import com.huanchengfly.tieba.post.ui.widgets.compose.Sizes
import com.huanchengfly.tieba.post.ui.widgets.compose.floatingGlass
import com.huanchengfly.tieba.post.utils.StringUtil
import com.huanchengfly.tieba.post.utils.StringUtil.getShortNumString

/** Back, and the forum the thread is in, which opens the forum. */
@Composable
internal fun ThreadTopBar(
    forum: ForumSummary?,
    onEvent: (ThreadEvent) -> Unit,
    hiddenFraction: () -> Float,
) {
    GlassTopBar(
        title = {
            if (forum != null && forum.name.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .height(IntrinsicSize.Min)
                        .clip(RoundedCornerShape(100))
                        .background(ExtendedTheme.colors.chip)
                        .clickable { onEvent(ThreadEvent.OpenForum) }
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(
                        data = forum.avatar,
                        contentDescription = forum.name,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                    )
                    Text(
                        text = stringResource(id = R.string.title_forum, forum.name),
                        fontSize = 14.sp,
                        color = ExtendedTheme.colors.text,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = { BackNavigationIcon { onEvent(ThreadEvent.Back) } },
        hiddenFraction = hiddenFraction,
    )
}

private val ReplyBarHeight = 64.dp
private val ReplyBarShape = RoundedCornerShape(percent = 50)

/**
 * A floating capsule with the reply box, liking the thread and the thread menu. It slides down
 * out of view as [hiddenFraction] goes to 1.
 *
 * @param viewer the logged-in user, or null for a guest, who gets no reply box
 */
@Composable
internal fun ThreadReplyBar(
    viewer: User?,
    agree: Agree,
    hideReply: Boolean,
    onEvent: (ThreadEvent) -> Unit,
    hiddenFraction: () -> Float,
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.navigationBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
            )
            .height(ReplyBarHeight)
            .graphicsLayer { translationY = with(density) { ReplyBarHeight.toPx() } * hiddenFraction() },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .floatingGlass(ReplyBarShape)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (viewer != null && !hideReply) {
                Avatar(
                    data = StringUtil.getAvatarUrl(viewer.portrait),
                    size = Sizes.Tiny,
                    contentDescription = viewer.name,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(ReplyBarShape)
                        .background(ExtendedTheme.colors.bottomBarSurface)
                        .clickable { onEvent(ThreadEvent.Reply(post = null)) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.tip_reply_thread),
                        style = MaterialTheme.typography.caption,
                        color = ExtendedTheme.colors.onBottomBarSurface,
                    )
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            ThreadAgreeButton(agree = agree, onClick = { onEvent(ThreadEvent.AgreeThread) })
            Box(
                modifier = Modifier
                    .clip(ReplyBarShape)
                    .clickable { onEvent(ThreadEvent.OpenMenu) }
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(id = R.string.btn_more),
                    tint = ExtendedTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ThreadAgreeButton(agree: Agree, onClick: () -> Unit) {
    val color by animateColorAsState(
        if (agree.agreed) ExtendedTheme.colors.accent else ExtendedTheme.colors.textSecondary,
        label = "threadAgreeColor"
    )
    Row(
        modifier = Modifier
            .clip(ReplyBarShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = if (agree.agreed) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = stringResource(id = R.string.title_agree),
            tint = color,
        )
        if (agree.count > 0) {
            Text(
                text = agree.count.getShortNumString(),
                style = MaterialTheme.typography.caption,
                color = color,
                fontSize = 12.sp,
            )
        }
    }
}

/** What pulling up at the end of the list does. */
@Composable
internal fun LoadMorePill(
    isLoading: Boolean,
    loadEnd: Boolean,
    willLoad: Boolean,
    hasMore: Boolean,
) {
    Surface(
        elevation = 8.dp,
        shape = RoundedCornerShape(100),
        color = ExtendedTheme.colors.loadMoreIndicator,
        contentColor = ExtendedTheme.colors.text
    ) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(10.dp)
                .animateContentSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProvideTextStyle(value = MaterialTheme.typography.body2.copy(fontSize = 13.sp)) {
                val text = when {
                    isLoading -> R.string.text_loading
                    loadEnd -> R.string.no_more
                    hasMore -> if (willLoad) R.string.release_to_load else R.string.pull_to_load
                    else -> if (willLoad) R.string.release_to_load_latest_posts else R.string.pull_to_load_latest_posts
                }
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 3.dp,
                        color = ExtendedTheme.colors.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(text = stringResource(id = text), modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
    }
}
