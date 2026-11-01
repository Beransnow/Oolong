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
import com.huanchengfly.tieba.post.utils.StringUtil.getShortNumString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import android.graphics.Typeface
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

/**
 * The forum's front: its avatar and name with how many follow it and post in it, the viewer's
 * level in its colour, and a full-width button to follow or sign in, next to the rules.
 */
@Composable
internal fun ForumHeader(
    forum: ForumDetail,
    loggedIn: Boolean,
    onEvent: (ForumEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = forumAccentOrPrimary()
    val openDetail = Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = { onEvent(ForumEvent.OpenDetail) }
    )
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = openDetail) { Avatar(data = forum.avatar, size = ForumAvatarSize, contentDescription = null) }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f).then(openDetail)) {
                Text(
                    text = stringResource(id = R.string.title_forum, forum.name),
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = ExtendedTheme.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (forum.slogan.isNotBlank()) {
                    Text(
                        text = forum.slogan,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = ExtendedTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        // Figures on the left, the viewer's level on the right, on one baseline
        Row(verticalAlignment = Alignment.Bottom) {
            ForumStat(forum.memberCount.getShortNumString(), stringResource(id = R.string.forum_stat_members))
            Spacer(modifier = Modifier.width(24.dp))
            ForumStat(forum.threadCount.getShortNumString(), stringResource(id = R.string.forum_stat_threads))
            Spacer(modifier = Modifier.weight(1f))
            AnimatedVisibility(visible = forum.liked) {
                ForumLevel(forum, accent)
            }
        }
        if (loggedIn || forum.ruleTitle != null) {
            Spacer(modifier = Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (loggedIn) {
                    val canAct = !forum.liked || !forum.signedIn
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(CircleShape)
                            .background(accent)
                            .clickable(enabled = canAct) { onEvent(ForumEvent.FollowOrSignIn) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = when {
                                !forum.liked -> stringResource(id = R.string.button_follow)
                                forum.signedIn -> stringResource(id = R.string.button_signed_in, forum.signedDays)
                                else -> stringResource(id = R.string.button_sign_in)
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = onForumAccent(),
                        )
                    }
                }
                if (forum.ruleTitle != null) {
                    Box(
                        modifier = Modifier
                            .then(if (loggedIn) Modifier else Modifier.weight(1f))
                            .height(40.dp)
                            .clip(CircleShape)
                            .background(ExtendedTheme.colors.chip)
                            .clickable { onEvent(ForumEvent.OpenRule) }
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(id = R.string.desc_forum_rule),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ExtendedTheme.colors.text,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForumStat(value: String, label: String) {
    Column {
        Text(text = value, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = ExtendedTheme.colors.text)
        Text(text = label, style = MaterialTheme.typography.caption, color = ExtendedTheme.colors.textSecondary)
    }
}

/** The viewer's level in the forum, in the forum's colour, with progress to the next one. */
@Composable
private fun ForumLevel(forum: ForumDetail, accent: Color, modifier: Modifier = Modifier) {
    val numerals = rememberNumerals()
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = "LV", fontFamily = numerals, fontSize = 14.sp, color = accent)
            Text(text = "${forum.level}", fontFamily = numerals, fontSize = 30.sp, lineHeight = 30.sp, color = accent)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(88.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(max(0f, min(1f, forum.score / max(1f, forum.levelUpScore.toFloat()))))
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
        }
    }
}

@Composable
private fun rememberNumerals(): FontFamily {
    val context = LocalContext.current
    return remember { FontFamily(Typeface.createFromAsset(context.assets, "bebas.ttf")) }
}

private val ForumAvatarSize = 64.dp

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
        AvatarPlaceholder(size = 64.dp)
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
    val accent = LocalForumAccent.current
    GlassTopBar(
        wash = if (accent.isSpecified) accent.copy(alpha = if (ExtendedTheme.colors.isNightMode) 0.28f else 0.18f) else Color.Unspecified,
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
        contentColor = forumAccentOrPrimary(),
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
                    selectedContentColor = ExtendedTheme.colors.text,
                    unselectedContentColor = ExtendedTheme.colors.textSecondary
                )
            } else {
                Tab(
                    selected = selected,
                    onClick = { onSelect(index) },
                    selectedContentColor = ExtendedTheme.colors.text,
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
