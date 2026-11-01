package com.huanchengfly.tieba.post.ui.page.thread

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
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
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.widgets.compose.Avatar
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBar
import com.huanchengfly.tieba.post.utils.StringUtil.getShortNumString

/** Back, and the forum the thread is in, which opens the forum. */
@Composable
internal fun ThreadTopBar(
    forum: ForumSummary?,
    onEvent: (ThreadEvent) -> Unit,
    pinnedContent: (@Composable ColumnScope.() -> Unit)?,
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
                    // As tall as the name's line, at any font size; the image's own size would stretch the chip
                    Avatar(
                        data = forum.avatar,
                        size = with(LocalDensity.current) { 20.sp.toDp() },
                        contentDescription = forum.name,
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
        pinnedContent = pinnedContent,
    )
}

private val CapsuleShape = RoundedCornerShape(percent = 50)

/** The actions capsule's content at rest: liking the thread, and the menu. */
@Composable
internal fun RowScope.ThreadActions(agree: Agree, onEvent: (ThreadEvent) -> Unit) {
    ThreadAgreeButton(agree = agree, onClick = { onEvent(ThreadEvent.AgreeThread) })
    MenuButton(open = false, onClick = { onEvent(ThreadEvent.OpenMenu) })
}

/** The open menu's bottom row, where the actions capsule was: the like stays, "…" turns into ×. */
@Composable
internal fun RowScope.ThreadMenuFooter(agree: Agree, onEvent: (ThreadEvent) -> Unit, onClose: () -> Unit) {
    ThreadAgreeButton(agree = agree, onClick = { onEvent(ThreadEvent.AgreeThread) })
    MenuButton(open = true, onClick = onClose)
}

@Composable
private fun MenuButton(open: Boolean, onClick: () -> Unit) {
    val turn by animateFloatAsState(if (open) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "menuButton")
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CapsuleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val tint = ExtendedTheme.colors.textSecondary
        Icon(
            imageVector = Icons.Rounded.MoreHoriz,
            contentDescription = if (open) null else stringResource(id = R.string.btn_more),
            tint = tint,
            modifier = Modifier.graphicsLayer {
                alpha = 1f - turn
                rotationZ = 90f * turn
            },
        )
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = if (open) stringResource(id = R.string.reply_menu_close) else null,
            tint = tint,
            modifier = Modifier.graphicsLayer {
                alpha = turn
                rotationZ = -90f * (1f - turn)
            },
        )
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
            .height(40.dp)
            .clip(CapsuleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = if (agree.agreed) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = stringResource(id = R.string.title_agree),
            tint = color,
        )
        if (agree.count > 0) {
            Text(
                text = agree.count.getShortNumString(),
                color = color,
                fontSize = 13.sp,
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
        color = ExtendedTheme.colors.refreshIndicator,
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
                        color = ExtendedTheme.colors.emphasis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(text = stringResource(id = text), modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
    }
}
