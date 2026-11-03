package com.huanchengfly.tieba.post.ui.page.reader

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Text
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.model.PostContent
import com.huanchengfly.tieba.post.models.PhotoViewData
import com.huanchengfly.tieba.post.ui.common.PostBody
import com.huanchengfly.tieba.post.ui.common.theme.compose.Corners
import com.huanchengfly.tieba.post.ui.common.theme.compose.Dimens
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedColors
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.LocalExtendedColors
import com.huanchengfly.tieba.post.ui.common.theme.compose.Motion
import com.huanchengfly.tieba.post.ui.common.theme.compose.Radius
import com.huanchengfly.tieba.post.ui.common.theme.compose.SmoothCornerShape
import com.huanchengfly.tieba.post.ui.common.theme.compose.Spacing
import com.huanchengfly.tieba.post.ui.common.theme.compose.Symbols
import com.huanchengfly.tieba.post.ui.common.theme.compose.TextStyles
import com.huanchengfly.tieba.post.ui.common.theme.compose.pressScale
import com.huanchengfly.tieba.post.ui.common.theme.compose.scrim
import com.huanchengfly.tieba.post.ui.common.windowsizeclass.LocalWindowSizeClass
import com.huanchengfly.tieba.post.ui.common.windowsizeclass.WindowWidthSizeClass
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBar
import com.huanchengfly.tieba.post.ui.widgets.compose.SegmentedControl
import com.huanchengfly.tieba.post.ui.widgets.compose.TextButton
import com.huanchengfly.tieba.post.ui.widgets.compose.floatingGlass
import com.huanchengfly.tieba.post.utils.ThemeUtil
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/** How the reader sets its text, chosen by the reader. */
data class ReaderStyle(
    val fontSize: Int = DEFAULT_FONT_SIZE,
    val spacing: Int = 1,
    val serif: Boolean = false,
    val page: ReaderPaper = ReaderPaper.White,
) {
    val lineHeight: Float get() = LINE_HEIGHTS[spacing.coerceIn(LINE_HEIGHTS.indices)]
    val fontFamily: FontFamily get() = if (serif) FontFamily.Serif else FontFamily.Default

    companion object {
        const val DEFAULT_FONT_SIZE = 17
        val FONT_SIZES = listOf(15, 17, 19, 21)
        val LINE_HEIGHTS = listOf(1.6f, 30f / 17, 2f)
    }
}

/** The page's colour in a light appearance; a dark appearance reads on the theme's own. */
enum class ReaderPaper(
    val background: Color,
    val text: Color,
    val secondary: Color,
    val divider: Color,
    val chip: Color,
) {
    White(Color(0xFFFFFFFF), Color(0xFF1F2329), Color(0xFF8A9099), Color(0xFFE6E8EC), Color(0xFFF1F2F5)),
    Paper(Color(0xFFF6F0E4), Color(0xFF3A3128), Color(0xFF978A78), Color(0xFFE4DAC8), Color(0xFFECE3D2)),
    Green(Color(0xFFE4EEDF), Color(0xFF22301F), Color(0xFF73846D), Color(0xFFCFDCC8), Color(0xFFD6E4CF)),
}

/** The theme's colours with the reader's page, so the chrome's glass and the sheets match it. */
private fun ExtendedColors.onPaper(paper: ReaderPaper): ExtendedColors {
    if (isNightMode) return if (ThemeUtil.isTranslucentTheme(theme)) copy(theme = ThemeUtil.THEME_BLUE_DARK, background = sheet) else this
    return copy(
        theme = ThemeUtil.THEME_DEFAULT,
        background = paper.background,
        sheet = paper.background,
        card = paper.background,
        inset = paper.chip,
        chip = paper.chip,
        divider = paper.divider,
        text = paper.text,
        textSecondary = paper.secondary,
        topBarContent = paper.text,
        topBarContentSecondary = paper.secondary,
    )
}

@Composable
fun ReaderScreen(
    state: ReaderState,
    style: ReaderStyle,
    onStyleChange: (ReaderStyle) -> Unit,
    onBack: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenSubPosts: (postId: Long) -> Unit,
    onReadUpTo: (postId: Long) -> Unit,
    photoViewData: (postId: Long, PostContent.Image) -> PhotoViewData?,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    initialChrome: Boolean = false,
) {
    val colors = ExtendedTheme.colors.onPaper(style.page)
    CompositionLocalProvider(LocalExtendedColors provides colors) {
        ReaderContent(state, style, onStyleChange, onBack, onLoadMore, onRetry, onOpenSubPosts, onReadUpTo, photoViewData, modifier, listState, initialChrome)
    }
}

private enum class ReaderSheet { Contents, Text }

@Composable
private fun ReaderContent(
    state: ReaderState,
    style: ReaderStyle,
    onStyleChange: (ReaderStyle) -> Unit,
    onBack: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenSubPosts: (postId: Long) -> Unit,
    onReadUpTo: (postId: Long) -> Unit,
    photoViewData: (postId: Long, PostContent.Image) -> PhotoViewData?,
    modifier: Modifier,
    listState: LazyListState,
    initialChrome: Boolean,
) {
    val colors = ExtendedTheme.colors
    val document = state.document
    val scope = rememberCoroutineScope()
    var chrome by rememberSaveable { mutableStateOf(initialChrome) }
    var sheet by remember { mutableStateOf<ReaderSheet?>(null) }
    val openNotes = remember { mutableStateListOf<String>() }

    // The block at the top of the screen; the cover comes before the first
    val current by remember(listState) { derivedStateOf { (listState.firstVisibleItemIndex - 1).coerceAtLeast(0) } }
    val atEnd by remember(listState) { derivedStateOf { !listState.canScrollForward } }
    val block = document.blocks.getOrNull(current)
    val chapter = document.chapterAt(current).let { document.chapters.getOrNull(it)?.title }
    val progress = when {
        block == null -> 0
        atEnd && !state.hasMore -> 100
        else -> (block.floor * 100 / state.lastFloor.coerceAtLeast(1)).coerceIn(0, 99)
    }

    LaunchedEffect(listState, document) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> if (last >= listState.layoutInfo.totalItemsCount - PRELOAD_BLOCKS) onLoadMore() }
    }
    // The contents list what has loaded, so while it is open the rest loads
    LaunchedEffect(sheet, document, state.hasMore) {
        if (sheet == ReaderSheet.Contents && state.hasMore) onLoadMore()
    }
    LaunchedEffect(listState, document) {
        snapshotFlow { current }
            .collect { index ->
                val foot = document.blocks.drop(index).firstNotNullOfOrNull { it as? ReaderBlock.Foot }
                if (foot != null) onReadUpTo(foot.postId)
            }
    }
    BackHandler(enabled = chrome && sheet == null) { chrome = false }

    val pageMargin = if (LocalWindowSizeClass.current.widthSizeClass == WindowWidthSizeClass.Compact) Dimens.PageGutter else Dimens.PageGutterWide
    val thread = state.thread
    val title = thread?.title ?: state.preview?.title.orEmpty()
    GlassScaffold(
        modifier = modifier,
        backgroundColor = colors.background,
        overlay = {
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = chrome,
                    enter = slideInVertically(Motion.present()) { -it } + fadeIn(Motion.fadeIn()),
                    exit = slideOutVertically(Motion.dismiss()) { -it } + fadeOut(Motion.fadeOut()),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    GlassTopBar(
                        title = {
                            Column {
                                Text(text = title, style = TextStyles.name, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = listOfNotNull(chapter ?: stringResource(R.string.reader_beginning), block?.let { "$progress%" }).joinToString(" · "),
                                    style = TextStyles.footnote,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        navigationIcon = { BackNavigationIcon(onBack) },
                    )
                }
                AnimatedVisibility(
                    visible = chrome,
                    enter = slideInVertically(Motion.present()) { it } + fadeIn(Motion.fadeIn()),
                    exit = slideOutVertically(Motion.dismiss()) { it } + fadeOut(Motion.fadeOut()),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    ReaderBottomBar(
                        onContents = { sheet = ReaderSheet.Contents },
                        onText = { sheet = ReaderSheet.Text },
                    )
                }
                when (sheet) {
                    ReaderSheet.Contents -> ContentsSheet(
                        document = document,
                        current = current,
                        loading = state.hasMore,
                        onGo = { index ->
                            chrome = false
                            scope.launch { listState.scrollToItem(index + 1) }
                        },
                        onDismiss = { sheet = null },
                    )
                    ReaderSheet.Text -> TextSheet(style = style, onChange = onStyleChange, onDismiss = { sheet = null })
                    null -> Unit
                }
            }
        },
    ) { _ ->
        val insets = WindowInsets.statusBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val top = insets.asPaddingValues().calculateTopPadding()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { chrome = !chrome } },
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = pageMargin, end = pageMargin, top = top + RunningHeadHeight, bottom = bottom + 96.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "cover") {
                    ReaderCover(
                        forum = thread?.forum?.name ?: state.preview?.forum?.name.orEmpty(),
                        title = title,
                        author = (thread?.author ?: state.preview?.author)?.displayName.orEmpty(),
                        start = document.blocks.firstOrNull()?.time,
                    )
                }
                itemsIndexed(document.blocks, key = { _, it -> it.key }, contentType = { _, it -> it::class }) { _, block ->
                    ReaderBlockView(
                        block = block,
                        style = style,
                        notesOpen = block.key in openNotes,
                        onToggleNotes = { if (!openNotes.remove(block.key)) openNotes += block.key },
                        onOpenSubPosts = onOpenSubPosts,
                        photoViewData = photoViewData,
                    )
                }
                item(key = "end") {
                    ReaderEnd(state.loading, state.hasMore, empty = document.blocks.isEmpty(), onRetry = onRetry)
                }
            }
            // A quiet running head while the chrome is away: where in the text, and how far through
            AnimatedVisibility(
                visible = !chrome,
                enter = fadeIn(Motion.fadeIn()),
                exit = fadeOut(Motion.fadeOut()),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(0.6f to colors.background, 1f to colors.background.copy(alpha = 0f)))
                        .padding(top = top)
                        .height(RunningHeadHeight)
                        .padding(horizontal = pageMargin),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = chapter ?: title,
                        style = TextStyles.footnote,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (block != null) {
                        Text(text = "$progress%", style = TextStyles.footnote, color = colors.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderCover(forum: String, title: String, author: String, start: Long?) {
    val colors = ExtendedTheme.colors
    Column(modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.xl)) {
        if (forum.isNotEmpty()) {
            Text(text = stringResource(R.string.title_forum, forum), style = TextStyles.footnote, color = colors.textSecondary)
        }
        Text(
            text = title,
            style = TextStyles.display.copy(fontFamily = FontFamily.Serif, lineHeight = 34.sp),
            color = colors.text,
            modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.sm),
        )
        val meta = listOfNotNull(author.takeIf { it.isNotEmpty() }, start?.let { formatDate(it) }).joinToString(" · ")
        Text(text = meta, style = TextStyles.caption, color = colors.textSecondary)
        Box(
            modifier = Modifier
                .padding(top = Spacing.xl)
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.divider)
        )
    }
}

@Composable
private fun ReaderBlockView(
    block: ReaderBlock,
    style: ReaderStyle,
    notesOpen: Boolean,
    onToggleNotes: () -> Unit,
    onOpenSubPosts: (Long) -> Unit,
    photoViewData: (Long, PostContent.Image) -> PhotoViewData?,
) {
    val colors = ExtendedTheme.colors
    val size = style.fontSize.sp
    when (block) {
        is ReaderBlock.UpdateMark -> if (block.gap != null) {
            val date = formatDate(block.time)
            val text = if (block.gap > LONG_PAUSE) {
                stringResource(R.string.reader_updated_after, date, gapText(block.gap))
            } else stringResource(R.string.reader_updated, date)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.padding(top = Spacing.xxl, bottom = Spacing.xl),
            ) {
                Box(Modifier.weight(1f).height(1.dp).background(colors.divider))
                Text(text = text, style = TextStyles.footnote, color = colors.textSecondary)
                Box(Modifier.weight(1f).height(1.dp).background(colors.divider))
            }
        }

        is ReaderBlock.Heading -> Text(
            text = block.title,
            style = TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = if (block.level == 1) size * 1.3f else size * 1.06f,
                lineHeight = if (block.level == 1) size * 1.8f else size * 1.5f,
                fontWeight = if (block.level == 1) FontWeight.Bold else FontWeight.SemiBold,
            ),
            color = colors.text,
            modifier = Modifier
                .semantics { this.heading() }
                .padding(top = if (block.level == 1) size.value.dp * 1.6f else size.value.dp, bottom = size.value.dp * 0.8f),
        )

        is ReaderBlock.Paragraph -> Text(
            text = block.text,
            style = TextStyle(
                fontFamily = style.fontFamily,
                fontSize = size,
                lineHeight = size * style.lineHeight,
                letterSpacing = 0.01.em,
                textIndent = TextIndent(firstLine = 2.em),
                textAlign = TextAlign.Justify,
            ),
            color = colors.text,
            modifier = Modifier.padding(bottom = size.value.dp * 0.75f),
        )

        is ReaderBlock.Chat -> Column(
            modifier = Modifier
                .padding(bottom = size.value.dp)
                .fillMaxWidth()
                .clip(Corners.md)
                .background(colors.chip)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            val chatSize = size * 0.9f
            block.room?.let {
                Text(text = it, style = TextStyles.footnote, fontWeight = FontWeight.SemiBold, color = colors.textSecondary, modifier = Modifier.padding(bottom = Spacing.xxs))
            }
            block.lines.forEach { line ->
                Row {
                    Text(text = line.name, fontSize = chatSize, lineHeight = chatSize * 1.65f, fontWeight = FontWeight.SemiBold, color = colors.text)
                    Text(
                        text = line.text,
                        fontSize = chatSize,
                        lineHeight = chatSize * 1.65f,
                        fontFamily = style.fontFamily,
                        color = colors.text,
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                }
            }
        }

        is ReaderBlock.Break -> Text(
            text = "·  ·  ·",
            style = TextStyles.caption,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = size.value.dp),
        )

        is ReaderBlock.Media -> PostBody(
            content = persistentListOf(block.content),
            photoViewData = { photoViewData(block.postId, it) },
            modifier = Modifier.padding(bottom = size.value.dp),
        )

        is ReaderBlock.Notes -> Column(modifier = Modifier.padding(bottom = Spacing.lg)) {
            Text(
                text = pluralStringResource(R.plurals.reader_notes, block.notes.size, block.notes.size),
                style = TextStyles.caption,
                color = colors.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Corners.sm)
                    .border(1.dp, colors.divider, Corners.sm)
                    .clickable(onClick = onToggleNotes)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            )
            if (notesOpen) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    modifier = Modifier.padding(start = Spacing.sm, top = Spacing.xs),
                ) {
                    block.notes.forEach { note ->
                        Row {
                            Text(text = "#${note.floor}", style = TextStyles.caption, color = colors.textSecondary.copy(alpha = 0.7f))
                            Text(
                                text = note.text.ifEmpty { "…" },
                                style = TextStyles.caption,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(start = Spacing.xs),
                            )
                        }
                    }
                }
            }
        }

        is ReaderBlock.Foot -> Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = size.value.dp * 1.2f),
        ) {
            Text(text = "#${block.floor} · ${formatDate(block.time)}", style = TextStyles.footnote, color = colors.textSecondary)
            if (block.subPostCount > 0) {
                val interaction = remember { MutableInteractionSource() }
                Text(
                    text = pluralStringResource(R.plurals.title_sub_posts_header, block.subPostCount, block.subPostCount),
                    style = TextStyles.footnote,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .pressScale(interaction)
                        .clip(Corners.full)
                        .background(colors.chip)
                        .clickable(interactionSource = interaction, indication = LocalIndication.current) { onOpenSubPosts(block.postId) }
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                )
            }
        }
    }
}

@Composable
private fun ReaderEnd(loading: LoadStatus, hasMore: Boolean, empty: Boolean, onRetry: () -> Unit) {
    val colors = ExtendedTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .padding(vertical = Spacing.xl),
    ) {
        when {
            loading is LoadStatus.Error -> TextButton(onClick = onRetry) {
                Text(text = stringResource(R.string.button_retry), style = TextStyles.label)
            }
            loading == LoadStatus.Loading || hasMore || empty ->
                CircularProgressIndicator(color = colors.textSecondary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            else -> Text(text = stringResource(R.string.reader_up_to_date), style = TextStyles.footnote, color = colors.textSecondary)
        }
    }
}

/** The tools, in a capsule like the navigation bar's: icons with short labels. */
@Composable
private fun ReaderBottomBar(onContents: () -> Unit, onText: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(bottom = Spacing.md),
    ) {
        Row(
            modifier = Modifier
                .floatingGlass(BarShape)
                .padding(Spacing.xxs),
        ) {
            BarTool(Symbols.Toc, stringResource(R.string.reader_contents), onContents)
            BarTool(Symbols.TextFields, stringResource(R.string.reader_text), onText)
        }
    }
}

@Composable
private fun BarTool(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = ExtendedTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .widthIn(min = 72.dp)
            .heightIn(min = 52.dp)
            .clip(BarShape)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = colors.text)
        Text(text = label, style = TextStyles.footnote, color = colors.text)
    }
}

private val BarShape = RoundedCornerShape(percent = 50)

/** A sheet over the reader that closes when swiped down, the scrim is tapped or back is pressed. */
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun ReaderSheetLayout(onDismiss: () -> Unit, content: @Composable (close: () -> Unit) -> Unit) {
    val colors = ExtendedTheme.colors
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden, animationSpec = Motion.settle(), skipHalfExpanded = true)
    LaunchedEffect(state) {
        state.show()
        snapshotFlow { state.currentValue }.collect { if (it == ModalBottomSheetValue.Hidden && !state.isVisible) onDismiss() }
    }
    val close: () -> Unit = { scope.launch { state.hide() } }
    BackHandler(onBack = close)
    ModalBottomSheetLayout(
        sheetContent = {
            Column(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.lg),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp, bottom = Spacing.sm)
                        .size(width = 36.dp, height = 5.dp)
                        .clip(CircleShape)
                        .background(colors.text.copy(alpha = 0.18f)),
                )
                content(close)
            }
        },
        sheetState = state,
        sheetShape = SmoothCornerShape(Radius.xl, bottom = false),
        sheetBackgroundColor = colors.sheet,
        scrimColor = colors.scrim,
        content = {},
    )
}

@Composable
private fun ContentsSheet(document: ReaderDocument, current: Int, loading: Boolean, onGo: (block: Int) -> Unit, onDismiss: () -> Unit) {
    val colors = ExtendedTheme.colors
    val hasChapters = document.chapters.isNotEmpty()
    var chapters by rememberSaveable { mutableStateOf(hasChapters) }
    ReaderSheetLayout(onDismiss) { close ->
        Text(text = stringResource(R.string.reader_contents), style = TextStyles.title, color = colors.text)
        if (hasChapters) {
            SegmentedControl(
                options = persistentListOf(stringResource(R.string.reader_chapters), stringResource(R.string.reader_updates)),
                selected = if (chapters) 0 else 1,
                onSelect = { chapters = it == 0 },
                modifier = Modifier.padding(top = Spacing.sm).fillMaxWidth(),
            )
        }
        val currentChapter = document.chapterAt(current)
        val currentUpdate = document.updateAt(current)
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = ((if (chapters) currentChapter else currentUpdate) - 3).coerceAtLeast(0))
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(top = Spacing.sm)
                .heightIn(max = 480.dp),
        ) {
            if (chapters) {
                itemsIndexed(document.chapters, key = { i, _ -> "c$i" }) { i, chapter ->
                    ContentsRow(
                        title = chapter.title,
                        detail = formatDate(chapter.time),
                        indent = chapter.level > 1,
                        selected = i == currentChapter,
                    ) {
                        onGo(chapter.block)
                        close()
                    }
                }
            } else {
                itemsIndexed(document.updates, key = { i, _ -> "u$i" }) { i, update ->
                    ContentsRow(
                        title = formatDate(update.time),
                        subtitle = update.preview ?: stringResource(R.string.reader_pictures),
                        detail = pluralStringResource(R.plurals.reader_floors, update.floors, update.floors),
                        selected = i == currentUpdate,
                    ) {
                        onGo(update.block)
                        close()
                    }
                }
            }
            if (loading) {
                item(key = "loading") {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(Spacing.md)) {
                        CircularProgressIndicator(color = colors.textSecondary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentsRow(
    title: String,
    detail: String,
    selected: Boolean,
    subtitle: String? = null,
    indent: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = ExtendedTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(Corners.md)
            .background(if (selected) colors.chip else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { this.selected = selected }
            .padding(start = if (indent) Spacing.xxl else Spacing.sm, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (indent) TextStyles.caption else TextStyles.label,
                fontWeight = if (selected) FontWeight.SemiBold else null,
                color = if (indent && !selected) colors.textSecondary else colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(text = subtitle, style = TextStyles.footnote, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(text = detail, style = TextStyles.footnote, color = colors.textSecondary)
    }
}

@Composable
private fun TextSheet(style: ReaderStyle, onChange: (ReaderStyle) -> Unit, onDismiss: () -> Unit) {
    val colors = ExtendedTheme.colors
    ReaderSheetLayout(onDismiss) {
        Text(text = stringResource(R.string.reader_text), style = TextStyles.title, color = colors.text)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(top = Spacing.md)) {
            OptionRow(stringResource(R.string.reader_font_size)) {
                ReaderStyle.FONT_SIZES.forEach { size ->
                    Option(selected = style.fontSize == size, onClick = { onChange(style.copy(fontSize = size)) }) {
                        Text(text = "A", fontSize = (size - 2).sp, color = it)
                    }
                }
            }
            OptionRow(stringResource(R.string.reader_line_spacing)) {
                listOf(R.string.reader_spacing_tight, R.string.reader_spacing_normal, R.string.reader_spacing_loose).forEachIndexed { i, label ->
                    Option(selected = style.spacing == i, onClick = { onChange(style.copy(spacing = i)) }) {
                        Text(text = stringResource(label), style = TextStyles.caption, color = it)
                    }
                }
            }
            OptionRow(stringResource(R.string.reader_font)) {
                listOf(false to R.string.reader_font_sans, true to R.string.reader_font_serif).forEach { (serif, label) ->
                    Option(selected = style.serif == serif, onClick = { onChange(style.copy(serif = serif)) }) {
                        Text(text = stringResource(label), style = TextStyles.caption, fontFamily = if (serif) FontFamily.Serif else FontFamily.Default, color = it)
                    }
                }
            }
            if (!colors.isNightMode) {
                OptionRow(stringResource(R.string.reader_page)) {
                    ReaderPaper.entries.forEach { paper ->
                        val label = stringResource(
                            when (paper) {
                                ReaderPaper.White -> R.string.reader_page_white
                                ReaderPaper.Paper -> R.string.reader_page_paper
                                ReaderPaper.Green -> R.string.reader_page_green
                            }
                        )
                        val chosen = style.page == paper
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(paper.background)
                                .border(if (chosen) 2.dp else 1.dp, if (chosen) colors.emphasis else paper.divider, CircleShape)
                                .clickable(role = Role.RadioButton) { onChange(style.copy(page = paper)) }
                                .semantics {
                                    contentDescription = label
                                    selected = chosen
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, options: @Composable RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = TextStyles.caption, color = ExtendedTheme.colors.textSecondary, modifier = Modifier.width(56.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f), content = options)
    }
}

@Composable
private fun RowScope.Option(selected: Boolean, onClick: () -> Unit, content: @Composable (Color) -> Unit) {
    val colors = ExtendedTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .pressScale(interaction)
            .heightIn(min = 40.dp)
            .clip(Corners.full)
            .background(if (selected) colors.emphasis else colors.chip)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected },
    ) {
        content(if (selected) colors.onEmphasis else colors.text)
    }
}

/** [seconds] as a date: month and day, with the year when it isn't this year. */
@Composable
private fun formatDate(seconds: Long): String {
    val locale = Locale.getDefault()
    return remember(seconds, locale) {
        val time = Calendar.getInstance().apply { timeInMillis = seconds * 1000 }
        val thisYear = time.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)
        val pattern = DateFormat.getBestDateTimePattern(locale, if (thisYear) "MMMd" else "yMMMd")
        java.text.SimpleDateFormat(pattern, locale).format(time.time)
    }
}

/** How long a pause of [seconds] was, roughly. */
@Composable
private fun gapText(seconds: Long): String {
    val days = seconds / DAY
    return when {
        days >= 60 -> (days / 30).toInt().let { pluralStringResource(R.plurals.reader_months, it, it) }
        days >= 14 -> (days / 7).toInt().let { pluralStringResource(R.plurals.reader_weeks, it, it) }
        else -> days.toInt().let { pluralStringResource(R.plurals.reader_days, it, it) }
    }
}

private const val DAY = 86_400L

/** A pause longer than this is told on the update mark. */
private const val LONG_PAUSE = 14 * DAY

/** How near the end of what has loaded the next page starts loading, in blocks. */
private const val PRELOAD_BLOCKS = 60

private val RunningHeadHeight = 36.dp
