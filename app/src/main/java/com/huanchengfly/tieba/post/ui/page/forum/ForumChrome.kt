package com.huanchengfly.tieba.post.ui.page.forum

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Tab
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.model.ForumDetail
import com.huanchengfly.tieba.post.model.ForumSort
import com.huanchengfly.tieba.post.model.ForumTab
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.widgets.compose.Avatar
import com.huanchengfly.tieba.post.ui.widgets.compose.AvatarPlaceholder
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.ClickMenu
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBar
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBarTitle
import com.huanchengfly.tieba.post.ui.widgets.compose.MenuScope
import com.huanchengfly.tieba.post.ui.widgets.compose.PagerTabIndicator
import com.huanchengfly.tieba.post.ui.widgets.compose.ScrollableTabRow
import com.huanchengfly.tieba.post.ui.widgets.compose.Sizes
import com.huanchengfly.tieba.post.ui.widgets.compose.TabClickMenu
import com.huanchengfly.tieba.post.ui.widgets.compose.picker.ListSinglePicker
import com.huanchengfly.tieba.post.ui.widgets.compose.placeholder.PlaceholderHighlight
import com.huanchengfly.tieba.post.ui.widgets.compose.placeholder.fade
import com.huanchengfly.tieba.post.ui.widgets.compose.placeholder.placeholder
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberMenuState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.math.max
import kotlin.math.min

/** The forum's avatar and name, the viewer's level in it, and the follow or sign-in button. */
@Composable
internal fun ForumHeader(
    forum: ForumDetail,
    loggedIn: Boolean,
    onEvent: (ForumEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(data = forum.avatar, size = Sizes.Large, contentDescription = null)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(id = R.string.title_forum, forum.name),
                style = MaterialTheme.typography.h6,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onEvent(ForumEvent.OpenDetail) }
                ),
            )
            AnimatedVisibility(visible = forum.liked) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = max(0f, min(1f, forum.score / max(1f, forum.levelUpScore.toFloat()))),
                        modifier = Modifier
                            .clip(RoundedCornerShape(100))
                            .height(8.dp),
                        color = ExtendedTheme.colors.primary,
                        backgroundColor = ExtendedTheme.colors.primary.copy(alpha = 0.25f)
                    )
                    Text(
                        text = stringResource(id = R.string.tip_forum_header_liked, forum.level.toString(), forum.levelName),
                        style = MaterialTheme.typography.caption,
                        color = ExtendedTheme.colors.textSecondary,
                        fontSize = 10.sp,
                    )
                }
            }
        }
        if (loggedIn) {
            Button(
                onClick = { onEvent(ForumEvent.FollowOrSignIn) },
                elevation = null,
                shape = RoundedCornerShape(100),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = ExtendedTheme.colors.primary,
                    contentColor = ExtendedTheme.colors.onAccent
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                enabled = !forum.liked || !forum.signedIn
            ) {
                val text = when {
                    !forum.liked -> stringResource(id = R.string.button_follow)
                    forum.signedIn -> stringResource(id = R.string.button_signed_in, forum.signedDays)
                    else -> stringResource(id = R.string.button_sign_in)
                }
                Text(text = text, fontSize = 13.sp)
            }
        }
    }
}

@Composable
internal fun ForumHeaderPlaceholder(
    forumName: String,
    loggedIn: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarPlaceholder(size = Sizes.Large)
        Text(
            text = stringResource(id = R.string.title_forum, forumName),
            style = MaterialTheme.typography.h6,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (loggedIn) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100))
                    .placeholder(visible = true, highlight = PlaceholderHighlight.fade())
                    .padding(horizontal = 18.dp, vertical = 6.dp)
            ) {
                Text(text = stringResource(id = R.string.button_sign_in), fontSize = 13.sp)
            }
        }
    }
}

/**
 * The top bar: back, the forum's name once the header has scrolled away, search and the menu,
 * with [content] (the header and tabs) below in the bar.
 */
@Composable
internal fun ForumTopBar(
    forumName: String,
    showTitle: Boolean,
    onEvent: (ForumEvent) -> Unit,
    canSearch: Boolean = true,
    menuContent: (@Composable MenuScope.() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    GlassTopBar(
        title = { if (showTitle) GlassTopBarTitle(text = stringResource(id = R.string.title_forum, forumName)) },
        navigationIcon = { BackNavigationIcon(onBackPressed = { onEvent(ForumEvent.Back) }) },
        actions = {
            if (canSearch) {
                IconButton(onClick = { onEvent(ForumEvent.Search) }) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = stringResource(id = R.string.btn_search_in_forum)
                    )
                }
            }
            if (menuContent != null) {
                ClickMenu(menuContent = menuContent, menuState = rememberMenuState(), triggerShape = CircleShape) {
                    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(id = R.string.btn_more)
                        )
                    }
                }
            }
        },
        content = content,
    )
}

@Composable
internal fun ForumMenu(onEvent: (ForumEvent) -> Unit, liked: Boolean, dismiss: () -> Unit) {
    DropdownMenuItem(onClick = { onEvent(ForumEvent.Share); dismiss() }) {
        Text(text = stringResource(id = R.string.title_share))
    }
    DropdownMenuItem(onClick = { onEvent(ForumEvent.SendToDesktop); dismiss() }) {
        Text(text = stringResource(id = R.string.title_send_to_desktop))
    }
    if (liked) {
        DropdownMenuItem(onClick = { onEvent(ForumEvent.Unfollow); dismiss() }) {
            Text(text = stringResource(id = R.string.title_unfollow))
        }
    }
}

@Composable
private fun tabTextStyle() = MaterialTheme.typography.button.copy(
    fontWeight = FontWeight.Bold,
    fontSize = 13.sp,
    letterSpacing = 0.sp,
)

/** The forum's tabs; tapping the selected latest tab again picks how its threads are ordered. */
@Composable
internal fun ForumTabRow(
    tabs: ImmutableList<ForumTab>,
    pagerState: PagerState,
    sortType: Int,
    onSelect: (Int) -> Unit,
    onEvent: (ForumEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = pagerState.currentPage.coerceIn(0, max(0, tabs.lastIndex))
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        indicator = { tabPositions -> PagerTabIndicator(pagerState = pagerState, tabPositions = tabPositions) },
        divider = {},
        backgroundColor = Color.Transparent,
        contentColor = ExtendedTheme.colors.primary,
        edgePadding = 0.dp,
        modifier = modifier.wrapContentWidth(align = Alignment.Start),
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            if (tab == ForumTab.Latest) {
                TabClickMenu(
                    selected = selected,
                    onClick = { onSelect(index) },
                    text = { Text(text = tab.title(), style = tabTextStyle()) },
                    menuContent = {
                        ListSinglePicker(
                            itemTitles = persistentListOf(
                                stringResource(id = R.string.title_sort_by_reply),
                                stringResource(id = R.string.title_sort_by_send),
                            ),
                            itemValues = SortTypes,
                            selectedPosition = SortTypes.indexOf(sortType),
                            onItemSelected = { _, _, value, changed ->
                                if (changed) onEvent(ForumEvent.SetSort(value))
                                dismiss()
                            }
                        )
                    },
                    selectedContentColor = ExtendedTheme.colors.primary,
                    unselectedContentColor = ExtendedTheme.colors.textSecondary
                )
            } else {
                Tab(
                    selected = selected,
                    onClick = { onSelect(index) },
                    selectedContentColor = ExtendedTheme.colors.primary,
                    unselectedContentColor = ExtendedTheme.colors.textSecondary
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .height(48.dp)
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(text = tab.title(), style = tabTextStyle())
                    }
                }
            }
        }
    }
}

private val SortTypes = persistentListOf(ForumSort.REPLY_TIME, ForumSort.POST_TIME)

@Composable
internal fun ForumTab.title(): String = when (this) {
    ForumTab.Good -> stringResource(id = R.string.tab_forum_good)
    ForumTab.Hot -> stringResource(id = R.string.tab_forum_hot)
    ForumTab.Latest -> stringResource(id = R.string.tab_forum_latest)
    is ForumTab.Custom -> name
}

/** The tab names as placeholders, while the forum loads. */
@Composable
internal fun ForumTabRowPlaceholder(modifier: Modifier = Modifier) {
    Row(modifier = modifier.height(48.dp)) {
        ForumTab.Fixed.forEach { tab ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.title(),
                    modifier = Modifier.placeholder(visible = true, highlight = PlaceholderHighlight.fade()),
                    style = tabTextStyle(),
                )
            }
        }
    }
}
