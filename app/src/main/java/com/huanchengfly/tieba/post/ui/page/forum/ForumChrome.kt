package com.huanchengfly.tieba.post.ui.page.forum

import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Tab
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.model.ForumDetail
import com.huanchengfly.tieba.post.model.ForumSort
import com.huanchengfly.tieba.post.model.ForumTab
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.widgets.compose.Avatar
import com.huanchengfly.tieba.post.ui.widgets.compose.AvatarPlaceholder
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.ClickMenu
import com.huanchengfly.tieba.post.ui.widgets.compose.CollapsingState
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBar
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBarRowHeight
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBarTitleStart
import com.huanchengfly.tieba.post.ui.widgets.compose.MenuScope
import com.huanchengfly.tieba.post.ui.widgets.compose.PagerTabIndicator
import com.huanchengfly.tieba.post.ui.widgets.compose.ScrollableTabRow
import com.huanchengfly.tieba.post.ui.widgets.compose.TabClickMenu
import com.huanchengfly.tieba.post.ui.widgets.compose.picker.ListSinglePicker
import com.huanchengfly.tieba.post.ui.widgets.compose.placeholder.PlaceholderHighlight
import com.huanchengfly.tieba.post.ui.widgets.compose.placeholder.fade
import com.huanchengfly.tieba.post.ui.widgets.compose.placeholder.placeholder
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberMenuState
import com.huanchengfly.tieba.post.utils.StringUtil.getShortNumString
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The forum's front: its avatar and name over its slogan, how many follow it and post in it, the
 * viewer's title and level in its colour, and a full-width button to follow or sign in.
 *
 * It collapses with [collapse], following the finger: the avatar and name rise into the top bar's
 * row and stay there as its title, the slogan fades, and the rest slides up under the bar, fading
 * as it goes. The header tells [collapse] how far it can collapse once it has measured itself.
 */
@Composable
internal fun ForumHeader(
    forum: ForumDetail,
    loggedIn: Boolean,
    onEvent: (ForumEvent) -> Unit,
    collapse: CollapsingState,
    modifier: Modifier = Modifier,
) {
    val openDetail = Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = { onEvent(ForumEvent.OpenDetail) }
    )
    Layout(
        content = {
            Box(modifier = Modifier.layoutId(HeaderPart.Avatar).then(openDetail)) {
                Avatar(data = forum.avatar, size = ForumAvatarSize, contentDescription = null)
            }
            Text(
                text = stringResource(id = R.string.title_forum, forum.name),
                fontSize = NameFontSize,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                color = ExtendedTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.layoutId(HeaderPart.Name).then(openDetail),
            )
            if (forum.slogan.isNotBlank()) {
                Text(
                    text = forum.slogan,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = ExtendedTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.layoutId(HeaderPart.Slogan),
                )
            }
            ForumHeaderDetails(forum, loggedIn, onEvent, modifier = Modifier.layoutId(HeaderPart.Details))
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val padding = HeaderPadding.roundToPx()
        val avatarSize = ForumAvatarSize.roundToPx()
        val nameStart = padding + avatarSize + NameGap.roundToPx()
        val nameWidth = (width - nameStart - padding).coerceAtLeast(0)
        val name = measurables.first { it.layoutId == HeaderPart.Name }
        val nameHeight = name.minIntrinsicHeight(nameWidth)
        val slogan = measurables.firstOrNull { it.layoutId == HeaderPart.Slogan }
            ?.measure(Constraints(maxWidth = nameWidth))
        val sloganHeight = slogan?.height ?: 0
        val identityHeight = max(avatarSize, nameHeight + sloganHeight)
        val detailsWidth = (width - 2 * padding).coerceAtLeast(0)
        val details = measurables.first { it.layoutId == HeaderPart.Details }
            .measure(Constraints.fixedWidth(detailsWidth))
        val detailsTop = padding + identityHeight + DetailsGap.roundToPx()
        val height = detailsTop + details.height + padding
        if (collapse.heightOffsetLimit != -height.toFloat()) collapse.heightOffsetLimit = -height.toFloat()
        val collapsed = -collapse.heightOffset

        // Docked, the avatar sits small and centred in the bar's row above the header, with the
        // name after it at the title's size. The avatar rises with the header until it docks.
        val rowHeight = GlassTopBarRowHeight.toPx()
        val dockedAvatarSize = DockedAvatarSize.toPx()
        val avatarTop = padding + (identityHeight - avatarSize) / 2f
        val dockedAvatarTop = -(rowHeight + dockedAvatarSize) / 2f
        val progress = (collapsed / (avatarTop - dockedAvatarTop)).coerceIn(0f, 1f)
        val avatarScale = lerp(1f, dockedAvatarSize / avatarSize, progress)
        val titleStart = GlassTopBarTitleStart.toPx()

        val dockedNameScale = DockedNameFontSize.value / NameFontSize.value
        val nameScale = lerp(1f, dockedNameScale, progress)
        val dockedNameStart = titleStart + dockedAvatarSize + DockedNameGap.toPx()
        // Narrowed as it docks so that it ends before the bar's actions
        val dockedNameWidth = (width - dockedNameStart - ForumBarActionsWidth.toPx()) / dockedNameScale
        val namePlaceable = name.measure(
            Constraints(maxWidth = lerp(nameWidth.toFloat(), dockedNameWidth, progress).roundToInt().coerceIn(0, nameWidth))
        )
        val nameCenter = padding + (identityHeight - nameHeight - sloganHeight) / 2f + nameHeight / 2f
        val nameTop = lerp(nameCenter, -rowHeight / 2f, progress) - namePlaceable.height * nameScale / 2f
        val nameX = lerp(nameStart.toFloat(), dockedNameStart, progress)

        val detailsY = detailsTop - collapsed
        // The details slide under the bar's row and are cut at its edge, fading out before half
        // of them has gone
        val cut = (-detailsY).coerceIn(0f, details.height.toFloat())
        val detailsAlpha = ((detailsY + details.height / 2f) / (details.height / 2f + DetailsFade.toPx())).coerceIn(0f, 1f)

        layout(width, (height - collapsed).roundToInt().coerceIn(0, height)) {
            details.placeWithLayer(padding, detailsY.roundToInt()) {
                alpha = detailsAlpha
                clip = cut > 0f
                shape = TopCutShape(cut)
            }
            slogan?.placeWithLayer(nameX.roundToInt(), (nameTop + namePlaceable.height * nameScale).roundToInt()) {
                alpha = (1f - progress / SloganFadeEnd).coerceIn(0f, 1f)
            }
            measurables.first { it.layoutId == HeaderPart.Avatar }
                .measure(Constraints.fixed(avatarSize, avatarSize))
                .placeWithLayer(lerp(padding.toFloat(), titleStart, progress).roundToInt(), lerp(avatarTop, dockedAvatarTop, progress).roundToInt()) {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = avatarScale
                    scaleY = avatarScale
                }
            namePlaceable.placeWithLayer(nameX.roundToInt(), nameTop.roundToInt()) {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = nameScale
                scaleY = nameScale
            }
        }
    }
}

/**
 * Below the forum's name: its figures and the viewer's level, then a full-width button to follow
 * or sign in. A visitor also sees how many signed in today; once the viewer has signed in, the
 * button turns tonal and stays where it was, so the header keeps its shape. The rules are the
 * first item of the latest threads, not a button here.
 */
@Composable
private fun ForumHeaderDetails(
    forum: ForumDetail,
    loggedIn: Boolean,
    onEvent: (ForumEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = forumAccentOrPrimary()
    Column(modifier = modifier) {
        // Figures on the left, the viewer's level on the right, on one baseline
        Row(verticalAlignment = Alignment.Bottom) {
            ForumStat(forum.memberCount.getShortNumString(), stringResource(id = R.string.forum_stat_members))
            Spacer(modifier = Modifier.width(24.dp))
            ForumStat(forum.threadCount.getShortNumString(), stringResource(id = R.string.forum_stat_threads))
            if (!forum.liked) {
                Spacer(modifier = Modifier.width(24.dp))
                ForumStat(forum.todaySignCount.getShortNumString(), stringResource(id = R.string.forum_stat_signed_today))
            }
            Spacer(modifier = Modifier.weight(1f))
            AnimatedVisibility(visible = forum.liked) {
                ForumLevel(forum, accent)
            }
        }
        if (loggedIn) {
            Spacer(modifier = Modifier.height(SectionGap))
            val done = forum.liked && forum.signedIn
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonHeight)
                    .clip(CircleShape)
                    .background(if (done) accent.copy(alpha = if (ExtendedTheme.colors.isNightMode) 0.2f else 0.12f) else accent)
                    .clickable(enabled = !done) { onEvent(ForumEvent.FollowOrSignIn) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (done) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = when {
                        !forum.liked -> stringResource(id = R.string.button_follow)
                        done -> stringResource(id = R.string.button_signed_in, forum.signedDays)
                        else -> stringResource(id = R.string.button_sign_in)
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (done) accent else onForumAccent(),
                )
            }
        }
    }
}

private enum class HeaderPart { Avatar, Name, Slogan, Details }

/** Clips away the top [cut] pixels. */
private class TopCutShape(private val cut: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Rectangle(Rect(0f, cut, size.width, size.height))
}

private val HeaderPadding = 16.dp
private val NameGap = 14.dp
private val DetailsGap = 14.dp
private val SectionGap = 14.dp
private val ButtonHeight = 38.dp
private val NameFontSize = 24.sp
private val DockedAvatarSize = 28.dp
private val DockedNameGap = 10.dp

/** The bar title's size. */
private val DockedNameFontSize = 20.sp

/** Search and the menu, and the row's padding after them. */
private val ForumBarActionsWidth = 48.dp * 2 + 4.dp

/** How far below the bar's edge the details start to fade. */
private val DetailsFade = 32.dp

/** How far into docking the slogan has faded out. */
private const val SloganFadeEnd = 0.4f

@Composable
private fun ForumStat(value: String, label: String) {
    Column {
        Text(text = value, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = ExtendedTheme.colors.text)
        Text(text = label, style = MaterialTheme.typography.caption, color = ExtendedTheme.colors.textSecondary)
    }
}

/**
 * The viewer's title and level in the forum, in the forum's colour, on one baseline, with the
 * progress to the next level under both.
 */
@Composable
private fun ForumLevel(forum: ForumDetail, accent: Color, modifier: Modifier = Modifier) {
    val numerals = rememberNumerals()
    Column(modifier = modifier.width(IntrinsicSize.Max), horizontalAlignment = Alignment.End) {
        Row {
            if (forum.levelName.isNotBlank()) {
                Text(
                    text = forum.levelName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    modifier = Modifier.alignByBaseline(),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = "LV", fontFamily = numerals, fontSize = 14.sp, color = accent, modifier = Modifier.alignByBaseline())
            Text(
                text = "${forum.level}",
                fontFamily = numerals,
                fontSize = 30.sp,
                lineHeight = 30.sp,
                color = accent,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(min = 88.dp)
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
 * The top bar: back, search and the menu, with [content] (the header and tabs) below in the bar.
 * It has no title of its own: the header's name docks in its place as the header collapses.
 */
@Composable
internal fun ForumTopBar(
    onEvent: (ForumEvent) -> Unit,
    canSearch: Boolean = true,
    menuContent: (@Composable MenuScope.() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val accent = LocalForumAccent.current
    GlassTopBar(
        wash = if (accent.isSpecified) accent.copy(alpha = if (ExtendedTheme.colors.isNightMode) 0.28f else 0.18f) else Color.Unspecified,
        title = {},
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
    DropdownMenuItem(onClick = {
        onEvent(ForumEvent.Share)
        dismiss()
    }) {
        Text(text = stringResource(id = R.string.title_share))
    }
    DropdownMenuItem(onClick = {
        onEvent(ForumEvent.SendToDesktop)
        dismiss()
    }) {
        Text(text = stringResource(id = R.string.title_send_to_desktop))
    }
    if (liked) {
        DropdownMenuItem(onClick = {
            onEvent(ForumEvent.Unfollow)
            dismiss()
        }) {
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
