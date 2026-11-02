package com.huanchengfly.tieba.post.ui.page.thread

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.model.Post
import com.huanchengfly.tieba.post.model.PostContent
import com.huanchengfly.tieba.post.model.SubPost
import com.huanchengfly.tieba.post.models.PhotoViewData
import com.huanchengfly.tieba.post.ui.common.PbContentText
import com.huanchengfly.tieba.post.ui.common.PostBody
import com.huanchengfly.tieba.post.ui.common.relativeTime
import com.huanchengfly.tieba.post.ui.common.rememberSubPostText
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.PressFeedback
import com.huanchengfly.tieba.post.ui.common.theme.compose.Symbols
import com.huanchengfly.tieba.post.ui.common.theme.compose.pressScale
import com.huanchengfly.tieba.post.ui.page.settings.block.LocalBlockShortcuts
import com.huanchengfly.tieba.post.ui.widgets.compose.Avatar
import com.huanchengfly.tieba.post.ui.widgets.compose.BlockTip
import com.huanchengfly.tieba.post.ui.widgets.compose.BlockableContent
import com.huanchengfly.tieba.post.ui.widgets.compose.Chip
import com.huanchengfly.tieba.post.ui.widgets.compose.ContextMenuItem
import com.huanchengfly.tieba.post.ui.widgets.compose.buildChipInlineContent
import com.huanchengfly.tieba.post.ui.widgets.compose.longPressMenu
import com.huanchengfly.tieba.post.utils.StringUtil
import com.huanchengfly.tieba.post.utils.StringUtil.getShortNumString

/** The column left of a floor that holds its author's avatar, floor number and rule. */
private val Gutter = 52.dp
private val GutterStart = 4.dp
private val FloorAvatarSize = 36.dp
private val SubPostAvatarSize = 22.dp

/** Space kept above the end of a floor when its avatar sticks below the bar. */
private val StickyEndGap = 40.dp

/**
 * A reply of the thread, as a floor: its author's avatar and floor number in a gutter on the left,
 * with a rule down the gutter that runs on into the next floor's avatar, and the post beside it.
 * Replies inside the post hang off the rule on short branches.
 *
 * While a long floor scrolls under the top bar, its avatar and floor number stay just below the
 * bar, so it stays clear whose floor is being read, until the end of the floor pushes them away.
 *
 * @param listState the list the floor is in, to keep the avatar in view
 * @param key the floor's key in that list
 * @param stickBelow where the visible top chrome ends, in pixels from the end of the list's top
 * padding, for the avatar to stick below
 * @param startsRule whether this is the first floor of the list, where the rule starts at the avatar
 */
@Composable
fun ThreadFloor(
    item: PostItem,
    options: PostCardOptions,
    onEvent: (ThreadEvent) -> Unit,
    listState: LazyListState,
    key: Any,
    modifier: Modifier = Modifier,
    stickBelow: () -> Int = { 0 },
    startsRule: Boolean = false,
    isStoreMark: Boolean = false,
    canDelete: Boolean = false,
    photoViewData: (PostContent.Image) -> PhotoViewData? = { null },
    /** Whether the floor's menu offers storing the thread up to it. */
    canStore: Boolean = true,
) {
    val post = item.post
    val context = LocalContext.current
    val shortcuts = LocalBlockShortcuts.current
    val labels = postMenuLabels()
    val rule = floorRuleColor()
    var floorHeight by remember { mutableIntStateOf(0) }
    var badgeHeight by remember { mutableIntStateOf(0) }
    BlockableContent(
        block = item.block,
        tip = { reason, onShow ->
            BlockTip(
                reason = reason,
                onShow = onShow,
                label = stringResource(id = R.string.block_tip_floor, post.floor),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.fillMaxWidth().onSizeChanged { floorHeight = it.height }) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val x = (GutterStart + Gutter / 2).toPx()
                val top = if (startsRule) (FloorAvatarSize / 2 + 10.dp).toPx() else 0f
                drawLine(rule, Offset(x, top), Offset(x, size.height), 1.dp.toPx())
            }
            Column(modifier = Modifier.padding(start = GutterStart, end = 16.dp)) {
                Box(
                    modifier = Modifier.longPressMenu(
                        items = { postMenuItems(labels, context, post, options.hideReply, isStoreMark, canStore, canDelete, shortcuts, onEvent) },
                        onClick = { onEvent(ThreadEvent.Reply(post)) }.takeIf { !options.hideReply },
                        indication = null,
                    ),
                ) {
                    Row {
                        Spacer(modifier = Modifier.width(Gutter))
                        FloorBody(post, options, isStoreMark, photoViewData, onEvent)
                    }
                }
                if (post.subPostCount > 0 && post.subPosts.isNotEmpty()) {
                    FloorSubPosts(item, options, rule, onEvent)
                }
            }
            FloorBadge(
                post = post,
                onClick = { onEvent(ThreadEvent.OpenUser(post.author.id)) },
                modifier = Modifier
                    .padding(start = GutterStart, top = 10.dp)
                    .width(Gutter)
                    .offset {
                        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
                        val room = floorHeight - badgeHeight - StickyEndGap.roundToPx()
                        IntOffset(0, if (info == null) 0 else (stickBelow() - info.offset).coerceIn(0, room.coerceAtLeast(0)))
                    }
                    .onSizeChanged { badgeHeight = it.height },
            )
        }
    }
}

/** The avatar with the floor number on a tab across its lower edge. */
@Composable
private fun FloorBadge(post: Post, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val background = ExtendedTheme.colors.background
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(background)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(modifier = Modifier.padding(bottom = 8.dp)) {
                Avatar(
                    data = StringUtil.getAvatarUrl(post.author.portrait),
                    size = FloorAvatarSize,
                    contentDescription = null,
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(background)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(ExtendedTheme.colors.chip)
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.floor_short, post.floor),
                    style = TextStyle(fontFamily = rememberNumeralFamily(), fontSize = 12.sp, lineHeight = 14.sp),
                    color = ExtendedTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun FloorBody(
    post: Post,
    options: PostCardOptions,
    isStoreMark: Boolean,
    photoViewData: (PostContent.Image) -> PhotoViewData?,
    onEvent: (ThreadEvent) -> Unit,
) {
    val context = LocalContext.current
    val author = post.author
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp)) {
        // As tall as the avatar beside it, so the name sits level with the avatar's centre
        Row(modifier = Modifier.height(FloorAvatarSize), verticalAlignment = Alignment.CenterVertically) {
            ProvideTextStyle(
                MaterialTheme.typography.subtitle1.copy(
                    color = ExtendedTheme.colors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            ) {
                UserNameText(
                    userName = StringUtil.getUsernameAnnotatedString(context, author.name, author.nameShow),
                    userLevel = author.level,
                    isLz = author.id == options.threadAuthorId,
                    bawuType = author.bawuName(),
                    modifier = Modifier.clickable { onEvent(ThreadEvent.OpenUser(author.id)) },
                )
            }
        }
        if (isStoreMark) {
            Chip(
                text = stringResource(id = R.string.title_collected_floor),
                invertColor = true,
                prefixIcon = {
                    Icon(imageVector = Symbols.StarFill, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        PostBody(content = post.content, photoViewData = photoViewData, fontSize = 16.sp, lineHeight = 27.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = listOfNotNull(
                    relativeTime(post.time),
                    author.ipLocation.takeIf { it.isNotEmpty() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.caption,
                color = ExtendedTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            // The button pads its icon; shift it so the icon lines up with the text's right edge
            PostAgreeBtn(
                hasAgreed = post.agree.agreed,
                agreeNum = post.agree.count,
                onClick = { onEvent(ThreadEvent.AgreePost(post.id)) },
                modifier = Modifier.offset(x = 8.dp),
            )
        }
    }
}

/** The first replies inside a post, each on a branch off the floor's rule, then a link to the rest. */
@Composable
private fun FloorSubPosts(
    item: PostItem,
    options: PostCardOptions,
    rule: Color,
    onEvent: (ThreadEvent) -> Unit,
) {
    val post = item.post
    val context = LocalContext.current
    val shortcuts = LocalBlockShortcuts.current
    val labels = postMenuLabels()
    Column(modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)) {
        post.subPosts.forEach { subPost ->
            BlockableContent(
                block = item.subPostBlocks[subPost.id],
                tip = { reason, onShow ->
                    BlockTip(reason = reason, onShow = onShow, boxed = false, modifier = Modifier.padding(start = Gutter, end = 16.dp, bottom = 4.dp))
                },
            ) {
                SubPostBranch(rule = rule) {
                    SubPostLine(
                        subPost = subPost,
                        isLz = subPost.author.id == options.threadAuthorId,
                        onClick = { onEvent(ThreadEvent.OpenSubPosts(post, subPost.id)) },
                        menuItems = { subPostMenuItems(labels, context, post, subPost, options.hideReply, shortcuts, onEvent) },
                        maxLines = 4,
                    )
                }
            }
        }
        if (post.subPostCount > post.subPosts.size) {
            Text(
                text = pluralStringResource(id = R.plurals.open_all_sub_posts, count = post.subPostCount, post.subPostCount),
                style = MaterialTheme.typography.caption,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = ExtendedTheme.colors.accent,
                modifier = Modifier
                    .padding(start = Gutter, bottom = 12.dp)
                    .clickable { onEvent(ThreadEvent.OpenSubPosts(post, subPostId = 0)) }
                    .padding(vertical = 4.dp),
            )
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/** The colour of the rule down a floor and the branches off it to its replies. */
@Composable
fun floorRuleColor(): Color =
    ExtendedTheme.colors.textDisabled.copy(alpha = if (ExtendedTheme.colors.isNightMode) 0.45f else 0.7f)

/** Lines content up with a floor's body, past the avatar column the floor's rule runs down. */
fun Modifier.floorIndent(): Modifier = padding(start = GutterStart, end = 16.dp)

/**
 * [content], a reply, on a branch off a floor's rule. Inside a floor the floor draws the rule;
 * elsewhere [rail] draws it here too, on down to the next reply unless this is the [last].
 */
@Composable
fun SubPostBranch(
    rule: Color,
    modifier: Modifier = Modifier,
    rail: Boolean = false,
    last: Boolean = false,
    content: @Composable () -> Unit,
) {
    Row(modifier = modifier.height(IntrinsicSize.Min)) {
        Canvas(modifier = Modifier.width(Gutter).fillMaxSize()) {
            val x = Gutter.toPx() / 2
            val y = (SubPostAvatarSize / 2).toPx()
            val r = 10.dp.toPx()
            val stroke = 1.dp.toPx()
            if (rail) drawLine(rule, Offset(x, 0f), Offset(x, if (last) y - r else size.height), stroke)
            val branch = Path().apply {
                moveTo(x, y - r)
                quadraticTo(x, y, x + r, y)
                lineTo(size.width - 4.dp.toPx(), y)
            }
            drawPath(branch, rule, style = Stroke(stroke))
        }
        content()
    }
}

/**
 * A reply inside a post: the avatar, then who and what in one paragraph, then when. In a floor it
 * is cut to [maxLines] and shows its likes; on the replies' own page it shows in full, with
 * [onAgree] a like button.
 */
@Composable
fun SubPostLine(
    subPost: SubPost,
    isLz: Boolean,
    onClick: (() -> Unit)?,
    menuItems: () -> List<ContextMenuItem>,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    onAgree: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    Box(modifier = modifier.fillMaxWidth().longPressMenu(items = menuItems, onClick = onClick)) {
        Column(modifier = Modifier.padding(bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Avatar(
                    data = StringUtil.getAvatarUrl(subPost.author.portrait),
                    size = SubPostAvatarSize,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                ProvideTextStyle(value = MaterialTheme.typography.body2.copy(color = ExtendedTheme.colors.text)) {
                    PbContentText(
                        text = rememberSubPostText(subPost, isLz, nameColor = ExtendedTheme.colors.text),
                        modifier = Modifier.weight(1f),
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        emoticonSize = 0.9f,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = maxLines,
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
            Row(
                modifier = Modifier.padding(start = SubPostAvatarSize + 8.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = listOfNotNull(
                        relativeTime(subPost.time),
                        subPost.author.ipLocation.takeIf { it.isNotEmpty() },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.caption,
                    color = ExtendedTheme.colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                if (onAgree != null) {
                    // The button pads its icon; shift it so the icon lines up with the text's right edge
                    PostAgreeBtn(
                        hasAgreed = subPost.agree.agreed,
                        agreeNum = subPost.agree.count,
                        onClick = onAgree,
                        modifier = Modifier.offset(x = 8.dp),
                    )
                } else if (subPost.agree.count > 0) {
                    Icon(
                        imageVector = Symbols.Favorite,
                        contentDescription = stringResource(id = R.string.title_agree),
                        tint = ExtendedTheme.colors.textSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = subPost.agree.count.getShortNumString(),
                        style = MaterialTheme.typography.caption,
                        color = ExtendedTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}

/**
 * The first floor, laid out for reading: the title first, its author on one line under it, the
 * body in a larger size with images across the full width, then liking, collecting and sharing.
 */
@Composable
fun ThreadFirstPost(
    post: Post,
    state: ThreadState,
    options: PostCardOptions,
    isStoreMark: Boolean,
    canDelete: Boolean,
    photoViewData: (PostContent.Image) -> PhotoViewData?,
    onEvent: (ThreadEvent) -> Unit,
) {
    val thread = state.thread
    val context = LocalContext.current
    val shortcuts = LocalBlockShortcuts.current
    val labels = postMenuLabels()
    Box(
        modifier = Modifier.longPressMenu(
            items = {
                postMenuItems(labels, context, post, options.hideReply, isStoreMark, canStore = true, canDelete, shortcuts) { event ->
                    onEvent(if (event is ThreadEvent.Reply) ThreadEvent.Reply(null) else event)
                }
            },
            // Replying to the first floor replies to the thread
            onClick = { onEvent(ThreadEvent.Reply(null)) }.takeIf { !options.hideReply },
            indication = null,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp)) {
            FirstPostContent(
                post = post,
                title = post.title.ifBlank { thread?.title.orEmpty() },
                isStoreMark = isStoreMark,
                photoViewData = photoViewData,
                onOpenUser = { onEvent(ThreadEvent.OpenUser(it)) },
            )
            thread?.poll?.let { poll ->
                PollCard(
                    poll = poll,
                    canVote = state.viewer.isLoggedIn,
                    onVote = { onEvent(ThreadEvent.Vote(it)) },
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp),
                )
            }
            if (thread != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionPill(
                        icon = if (thread.agree.agreed) Symbols.FavoriteFill else Symbols.Favorite,
                        text = if (thread.agree.count > 0) thread.agree.count.getShortNumString() else stringResource(R.string.title_agree),
                        active = thread.agree.agreed,
                    ) { onEvent(ThreadEvent.AgreeThread) }
                    ActionPill(
                        icon = if (thread.isStored) Symbols.StarFill else Symbols.Star,
                        text = stringResource(if (thread.isStored) R.string.title_collected else R.string.title_collect),
                        active = thread.isStored,
                    ) { onEvent(if (thread.isStored) ThreadEvent.RemoveStore else ThreadEvent.StoreAtLastRead) }
                    ActionPill(icon = Symbols.Share, text = stringResource(R.string.title_share), active = false) {
                        onEvent(ThreadEvent.Share)
                    }
                }
            }
        }
    }
}

/**
 * A thread's first floor as the thread page shows it: its [title], the author, and the content
 * at reading size. Shared by the thread page and the peek at a thread from a list.
 *
 * @param onOpenUser opens the author's profile; null where the author can't be tapped
 */
@Composable
fun FirstPostContent(
    post: Post,
    title: String,
    modifier: Modifier = Modifier,
    isStoreMark: Boolean = false,
    photoViewData: (PostContent.Image) -> PhotoViewData? = { null },
    onOpenUser: ((Long) -> Unit)? = null,
) {
    val context = LocalContext.current
    val author = post.author
    Column(modifier = modifier) {
        if (title.isNotBlank()) {
            Text(
                text = title,
                fontSize = 22.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                color = ExtendedTheme.colors.text,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(14.dp))
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.clip(CircleShape).clickable(enabled = onOpenUser != null) { onOpenUser?.invoke(author.id) }) {
                Avatar(data = StringUtil.getAvatarUrl(author.portrait), size = 28.dp, contentDescription = null)
            }
            Spacer(modifier = Modifier.width(8.dp))
            ProvideTextStyle(
                MaterialTheme.typography.subtitle1.copy(
                    color = ExtendedTheme.colors.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            ) {
                UserNameText(
                    userName = StringUtil.getUsernameAnnotatedString(context, author.name, author.nameShow),
                    userLevel = author.level,
                    bawuType = author.bawuName(),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = onOpenUser != null) { onOpenUser?.invoke(author.id) },
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = listOfNotNull(
                    relativeTime(post.time),
                    author.ipLocation.takeIf { it.isNotEmpty() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.caption,
                color = ExtendedTheme.colors.textSecondary,
            )
        }
        if (isStoreMark) {
            Chip(
                text = stringResource(id = R.string.title_collected_floor),
                invertColor = true,
                prefixIcon = {
                    Icon(imageVector = Symbols.StarFill, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                modifier = Modifier.padding(start = 16.dp, top = 12.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        PostBody(
            content = post.content,
            photoViewData = photoViewData,
            fontSize = 17.sp,
            lineHeight = 30.sp,
            blockSpacing = 16.dp,
            textPadding = PaddingValues(horizontal = 16.dp),
        )
    }
}

@Composable
private fun ActionPill(icon: ImageVector, text: String, active: Boolean, onClick: () -> Unit) {
    val color = if (active) ExtendedTheme.colors.accent else ExtendedTheme.colors.text
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .pressScale(interactionSource)
            .height(36.dp)
            .clip(CircleShape)
            .background(ExtendedTheme.colors.chip)
            .clickable(interactionSource = interactionSource, indication = PressFeedback.Overlay, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text = text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

/** The narrow display face the app uses for figures. */
@Composable
private fun rememberNumeralFamily(): FontFamily {
    val context = LocalContext.current
    return remember { FontFamily(Typeface.createFromAsset(context.assets, "bebas.ttf")) }
}
