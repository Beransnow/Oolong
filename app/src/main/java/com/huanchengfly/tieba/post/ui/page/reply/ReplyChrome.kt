package com.huanchengfly.tieba.post.ui.page.reply

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.widgets.compose.MyBackHandler
import com.huanchengfly.tieba.post.ui.widgets.compose.floatingGlass
import com.huanchengfly.tieba.post.utils.appPreferences
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Which of the bottom chrome's surfaces is open: none, the reply composer, or the menu. */
enum class ChromeSurface { None, Composer, Menu }

/** What the bottom chrome shows, held by the page so its events can open the composer. */
@Stable
class ReplyChromeState {
    var surface by mutableStateOf(ChromeSurface.None)
    var fullScreen by mutableStateOf(false)
    var replyTo by mutableStateOf<ReplyTo?>(null)

    fun openComposer(replyTo: ReplyTo?) {
        this.replyTo = replyTo
        fullScreen = false
        surface = ChromeSurface.Composer
    }

    fun openMenu() {
        surface = ChromeSurface.Menu
    }

    fun close() {
        surface = ChromeSurface.None
        fullScreen = false
    }
}

@Composable
fun rememberReplyChromeState(): ReplyChromeState = remember { ReplyChromeState() }

/** Height of each capsule at rest. */
val ReplyCapsuleHeight = 52.dp
private val CapsuleShape = RoundedCornerShape(percent = 50)
private val SideMargin = 16.dp
private val BottomMargin = 8.dp
private val ComposerMargin = 8.dp
private val ComposerRadius = 24.dp

/** The space the capsules take above the navigation bar, for a scaffold's bottom bar slot. */
val ReplyChromeBarHeight = ReplyCapsuleHeight + BottomMargin * 2

/**
 * The bottom chrome of a page one can reply on, drawn over the page: a reply capsule that opens
 * into the composer and, beside it, an actions capsule that may open into a menu.
 *
 * Each open surface is the capsule's own glass growing in place, like Liquid Glass: the glass
 * springs from the capsule's bounds to the surface's, a little past them and back, and what is
 * inside condenses out of a blur once it has room. The other capsule dissolves where it is.
 *
 * @param replyCapsule the reply capsule's content at rest, or null when the page offers no reply
 * @param actions the actions capsule's content at rest, or null for none
 * @param menu what the menu holds above its footer, or null when [actions] opens no menu
 * @param menuFooter the menu's bottom row, which sits where [actions] was
 * @param composer the composer's content, given the drag handle to put at its top
 * @param panel what shows in place of the keyboard, such as the emoticons
 * @param panelShown whether [panel] shows instead of the keyboard
 * @param hiddenFraction how far the capsules have slid away for scrolling, 0 to 1
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReplyChrome(
    state: ReplyChromeState,
    replyCapsule: (@Composable RowScope.() -> Unit)?,
    actions: (@Composable RowScope.() -> Unit)?,
    menu: (@Composable ColumnScope.() -> Unit)?,
    menuFooter: @Composable RowScope.() -> Unit,
    composer: @Composable (handle: @Composable () -> Unit) -> Unit,
    panel: @Composable () -> Unit,
    panelShown: Boolean,
    onOpenComposer: () -> Unit,
    onDismiss: () -> Unit,
    onExitFullScreen: () -> Unit,
    onEnterFullScreen: () -> Unit,
    hiddenFraction: () -> Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val colors = ExtendedTheme.colors
    val surface = state.surface
    val composing = surface == ChromeSurface.Composer
    val menuOpen = surface == ChromeSurface.Menu
    val full = composing && state.fullScreen

    MyBackHandler(enabled = surface != ChromeSurface.None) {
        if (full) onExitFullScreen() else onDismiss()
    }

    var chrome by remember { mutableStateOf<LayoutCoordinates?>(null) }
    BoxWithConstraints(modifier = modifier.fillMaxSize().onGloballyPositioned { chrome = it }) {
        val maxHeightPx = constraints.maxHeight.toFloat()
        val bars = WindowInsets.navigationBars.union(WindowInsets.displayCutout)
        val navBottom = bars.getBottom(density)
        val statusTop = WindowInsets.statusBars.getTop(density)
        val imeBottom = WindowInsets.ime.getBottom(density)
        val imeTarget = WindowInsets.imeAnimationTarget.getBottom(density)
        val rememberedImeHeight = rememberImeHeight(imeBottom, imeTarget)
        // Leaving a panel for the keyboard, the panel holds its place until the keyboard is all the
        // way up, so the composer does not drop with the panel and then rise with the keyboard
        // Decided in the same frame the panel goes, as a frame without it would let the composer drop.
        // The keyboard is asked for at that moment but reports its height only once it moves, so
        // the panel holds until the keyboard is up, or briefly when no keyboard comes
        var holdingPanel by remember { mutableStateOf(false) }
        if (panelShown) {
            if (!holdingPanel) holdingPanel = true
        } else if (holdingPanel && imeTarget > 0 && imeBottom >= imeTarget) {
            holdingPanel = false
        }
        val currentImeTarget by rememberUpdatedState(imeTarget)
        LaunchedEffect(panelShown) {
            if (!panelShown) {
                delay(KeyboardWaitMillis)
                if (currentImeTarget == 0) holdingPanel = false
            }
        }
        val panelUp = composing && (panelShown || holdingPanel)
        // The composer sits on the keyboard, following it frame by frame as it slides, or on the
        // panel that takes its place, which is as tall as the keyboard last was
        val keyboardTop = when {
            panelUp -> maxOf(rememberedImeHeight, imeBottom, navBottom)
            else -> maxOf(imeBottom, navBottom)
        }
        // With the keyboard or a panel up, the composer rests on it as one piece with it
        val docked = composing && keyboardTop > navBottom

        // Where things are, in this box's pixels: the capsules at rest and the open surfaces
        var replyCapsuleBounds by remember { mutableStateOf<Rect?>(null) }
        var actionsBounds by remember { mutableStateOf<Rect?>(null) }
        var composerBounds by remember { mutableStateOf<Rect?>(null) }
        var menuBounds by remember { mutableStateOf<Rect?>(null) }

        val replyGlass = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
        val menuGlass = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
        var replyGlassShown by remember { mutableStateOf(false) }
        var menuGlassShown by remember { mutableStateOf(false) }
        val glassSpring = spring<Rect>(dampingRatio = 0.74f, stiffness = 420f)
        val settle = spring<Rect>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

        // The composer's glass follows the composer as it opens, grows with its text and moves
        // with the keyboard; closing, it springs back into the capsule
        LaunchedEffect(composing) {
            if (composing) {
                replyCapsuleBounds?.let { replyGlass.snapTo(it) }
                replyGlassShown = true
                // Springs out of the capsule once; after that the glass stays exactly on the composer
                // as the keyboard slides, the text grows or it goes full screen, which animates the
                // composer itself
                var opening: Job? = null
                snapshotFlow { composerBounds }.filterNotNull().collect { target ->
                    if (opening == null) {
                        opening = launch { replyGlass.animateTo(target, glassSpring) }
                    } else if (opening?.isActive == true) {
                        opening = launch { replyGlass.animateTo(target, glassSpring) }
                    } else {
                        replyGlass.snapTo(target)
                    }
                }
            } else if (replyGlassShown) {
                // The capsule takes over once the glass is nearly its size, so its words fade in
                // while the glass settles instead of after
                replyCapsuleBounds?.let { target ->
                    launch { replyGlass.animateTo(target, settle) }
                    snapshotFlow { replyGlass.value.height }.first { it <= target.height * HandBackRatio }
                }
                replyGlassShown = false
            }
        }
        LaunchedEffect(menuOpen) {
            if (menuOpen) {
                actionsBounds?.let { menuGlass.snapTo(it) }
                menuGlassShown = true
                snapshotFlow { menuBounds }.filterNotNull().collect { target ->
                    launch { menuGlass.animateTo(target, glassSpring) }
                }
            } else if (menuGlassShown) {
                actionsBounds?.let { target ->
                    launch { menuGlass.animateTo(target, settle) }
                    snapshotFlow { menuGlass.value.height }.first { it <= target.height * HandBackRatio }
                }
                menuGlassShown = false
            }
        }

        // Behind a full-screen composer or the menu, the page dims; tapping it closes them
        val scrim by animateFloatAsState(if (menuOpen || full) 1f else 0f, tween(300), label = "scrim")
        if (scrim > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = scrim }
                    .background(Color.Black.copy(alpha = if (colors.isNightMode) 0.5f else 0.28f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { if (full) onExitFullScreen() else onDismiss() },
                    )
            )
        }

        // Tapping the page beside a composer that is not full screen puts it away, keeping the draft
        if (composing && !full) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    )
            )
        }

        // The capsules at rest
        val capsuleShown = surface == ChromeSurface.None && !replyGlassShown && !menuGlassShown
        val restAlpha by animateFloatAsState(if (capsuleShown) 1f else 0f, tween(if (capsuleShown) 220 else 60), label = "rest")
        val dissolve by animateFloatAsState(if (surface == ChromeSurface.None) 0f else 1f, spring(stiffness = 500f), label = "dissolve")
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(bars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = SideMargin, vertical = BottomMargin)
                .graphicsLayer {
                    translationY = (ReplyChromeBarHeight + 24.dp).toPx() * hiddenFraction()
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (replyCapsule != null) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(ReplyCapsuleHeight)
                        .onGloballyPositioned { replyCapsuleBounds = it.boundsIn(chrome) }
                        .graphicsLayer {
                            alpha = if (composing || replyGlassShown) 0f else 1f - dissolve * 0.999f
                            scaleX = 1f - 0.3f * if (menuOpen) dissolve else 0f
                            scaleY = scaleX
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            if (menuOpen) renderEffect = blur(dissolve * 6.dp.toPx())
                        }
                        .floatingGlass(CapsuleShape)
                        .clickable(onClick = onOpenComposer)
                        .padding(start = 10.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    content = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { alpha = restAlpha },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            content = replyCapsule,
                        )
                    },
                )
            } else {
                Box(modifier = Modifier.weight(1f))
            }
            if (actions != null) {
                Row(
                    modifier = Modifier
                        .height(ReplyCapsuleHeight)
                        .onGloballyPositioned { actionsBounds = it.boundsIn(chrome) }
                        .graphicsLayer {
                            alpha = if (menuOpen || menuGlassShown) 0f else 1f - dissolve * 0.999f
                            scaleX = 1f - 0.3f * if (composing) dissolve else 0f
                            scaleY = scaleX
                            transformOrigin = TransformOrigin(1f, 0.5f)
                            if (composing) renderEffect = blur(dissolve * 6.dp.toPx())
                        }
                        .floatingGlass(CapsuleShape)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }

        // The glass of an open surface, springing between the capsule and the surface
        if (replyGlassShown || replyGlass.isRunning) {
            val radius by animateDpAsState(
                when {
                    !composing -> ReplyCapsuleHeight / 2
                    full -> 0.dp
                    else -> ComposerRadius
                },
                spring(stiffness = 420f),
                label = "composerRadius",
            )
            MorphingGlass(replyGlass.value, radius, opaque = full || docked, squareBottom = docked)
        }
        if (menuGlassShown || menuGlass.isRunning) {
            val radius by animateDpAsState(
                if (menuOpen) ComposerRadius else ReplyCapsuleHeight / 2,
                spring(stiffness = 420f),
                label = "menuRadius",
            )
            MorphingGlass(menuGlass.value, radius, opaque = false, squareBottom = false)
        }

        // What an open surface holds, condensing in once the glass has room for it
        val composerContent by animateFloatAsState(
            if (composing) 1f else 0f,
            if (composing) tween(220, delayMillis = 40) else tween(120),
            label = "composerContent",
        )
        if (composing || composerContent > 0f) {
            var dragHeight by remember { mutableStateOf<Float?>(null) }
            // How tall the composer is when neither dragged nor full, the point drags are judged from
            var compactHeight by remember { mutableStateOf(0f) }
            val bottom = if (full || docked) keyboardTop.toFloat() else keyboardTop + with(density) { ComposerMargin.toPx() }
            val fullHeight = maxHeightPx - statusTop - keyboardTop
            val dragState = rememberDraggableState { delta ->
                val start = dragHeight ?: composerBounds?.height ?: return@rememberDraggableState
                dragHeight = (start - delta).coerceIn(with(density) { 120.dp.toPx() }, fullHeight)
            }
            val handle: @Composable () -> Unit = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (full) 28.dp else 18.dp)
                        // Tapping the handle goes to full screen and back; dragging it resizes
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = stringResource(
                                id = if (full) R.string.reply_exit_full_screen else R.string.reply_full_screen
                            ),
                        ) { if (full) onExitFullScreen() else onEnterFullScreen() }
                        .draggable(
                            state = dragState,
                            orientation = Orientation.Vertical,
                            onDragStarted = { dragHeight = composerBounds?.height },
                            onDragStopped = {
                                val released = dragHeight
                                dragHeight = null
                                val compact = compactHeight
                                when {
                                    released == null -> Unit
                                    !full && released < compact - with(density) { 60.dp.toPx() } -> onDismiss()
                                    released > compact + (fullHeight - compact) * 0.35f -> onEnterFullScreen()
                                    else -> onExitFullScreen()
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 34.dp, height = 4.dp)
                            .background(colors.textDisabled.copy(alpha = 0.5f), CapsuleShape)
                    )
                }
            }
            // Going full screen and back, the composer grows and shrinks on a spring; the glass,
            // following it exactly, goes with it
            val fullProgress by animateFloatAsState(
                if (full) 1f else 0f,
                spring(dampingRatio = 0.82f, stiffness = 380f),
                label = "fullScreen",
            )
            val heightModifier = when {
                dragHeight != null -> Modifier.height(with(density) { dragHeight!!.toDp() })
                fullProgress > 0f && compactHeight > 0f -> Modifier.height(
                    with(density) { (compactHeight + (fullHeight - compactHeight) * fullProgress).coerceAtLeast(0f).toDp() }
                )
                else -> Modifier
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset { IntOffset(0, -bottom.roundToInt()) }
                    .windowInsetsPadding(bars.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = if (full || docked) 0.dp else ComposerMargin)
                    .fillMaxWidth()
                    .then(heightModifier)
                    .onGloballyPositioned {
                        if (!composing) return@onGloballyPositioned
                        val bounds = it.boundsIn(chrome)
                        composerBounds = bounds
                        if (!full && fullProgress == 0f && dragHeight == null) compactHeight = bounds.height
                    }
                    .clipToGlass(glass = { replyGlass.value }, own = { composerBounds }, radius = { ComposerRadius.toPx() })
                    .graphicsLayer {
                        alpha = composerContent
                        scaleX = 0.96f + 0.04f * composerContent
                        scaleY = scaleX
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        if (composerContent < 1f) renderEffect = blur((1f - composerContent) * 8.dp.toPx())
                    },
            ) {
                composer(handle)
            }
        }

        if (menu != null && (menuOpen || menuGlassShown)) {
            val menuContent by animateFloatAsState(
                if (menuOpen) 1f else 0f,
                if (menuOpen) tween(220, delayMillis = 40) else tween(120),
                label = "menuContent",
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(bars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(horizontal = SideMargin, vertical = BottomMargin)
                    .width(MenuWidth.coerceAtMost(maxWidth - SideMargin * 2))
                    .onGloballyPositioned { if (menuOpen) menuBounds = it.boundsIn(chrome) }
                    // The menu shows only as far as its glass has grown, so it opens out of the capsule
                    .clipToGlass(glass = { menuGlass.value }, own = { menuBounds }, radius = { ComposerRadius.toPx() }),
            ) {
                Column(
                    modifier = Modifier.graphicsLayer {
                        alpha = menuContent
                        scaleX = 0.9f + 0.1f * menuContent
                        scaleY = scaleX
                        transformOrigin = TransformOrigin(1f, 1f)
                        if (menuContent < 1f) renderEffect = blur((1f - menuContent) * 8.dp.toPx())
                    },
                    content = menu,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ReplyCapsuleHeight)
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = menuFooter,
                )
            }
        }

        // In place of the keyboard
        if (panelUp) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(with(density) { keyboardTop.toDp() })
                    .background(colors.windowBackground)
                    .windowInsetsPadding(bars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
            ) {
                panel()
            }
        }
    }
}

/** How close to the capsule's height the glass gets, closing, before the capsule takes over. */
private const val HandBackRatio = 1.6f

/**
 * Draws this surface's content only inside its glass, [glass] and [own] both in the chrome's
 * pixels, so while the glass grows out of a capsule or back into it the content is revealed or
 * covered with it rather than floating outside.
 */
private fun Modifier.clipToGlass(glass: () -> Rect, own: () -> Rect?, radius: Density.() -> Float): Modifier =
    drawWithContent {
        val bounds = own()
        val g = glass()
        if (bounds == null || g.isEmpty || g == bounds) {
            drawContent()
            return@drawWithContent
        }
        val local = Rect(g.left - bounds.left, g.top - bounds.top, g.right - bounds.left, g.bottom - bounds.top)
        val r = radius().coerceAtMost(minOf(local.width, local.height) / 2f)
        val path = Path().apply { addRoundRect(RoundRect(local, CornerRadius(r))) }
        clipPath(path) { this@drawWithContent.drawContent() }
    }

/** How long a panel waits for a keyboard asked for in its place before it gives way anyway. */
private const val KeyboardWaitMillis = 600L

/** The menu's width on a wide window; on a phone it spans the window. */
private val MenuWidth = 420.dp

@Composable
private fun MorphingGlass(bounds: Rect, radius: Dp, opaque: Boolean, squareBottom: Boolean) {
    val density = LocalDensity.current
    val colors = ExtendedTheme.colors
    if (bounds.isEmpty) return
    val shape = if (squareBottom) RoundedCornerShape(topStart = radius, topEnd = radius) else RoundedCornerShape(radius)
    Box(
        modifier = Modifier
            .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
            .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
            // Grown into a surface, the glass reads as thicker: a deeper shadow under it
            .shadow(elevation = 18.dp, shape = shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.22f))
            .floatingGlass(shape)
            .then(if (opaque) Modifier.background(colors.windowBackground, shape) else Modifier)
    )
}

/** These bounds in [container]'s pixels, where the chrome places its glass. */
private fun LayoutCoordinates.boundsIn(container: LayoutCoordinates?): Rect =
    if (container == null || !container.isAttached || !isAttached) Rect.Zero
    else container.localBoundingBoxOf(this, clipBounds = false)

private fun blur(radius: Float) =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && radius > 0.5f) {
        BlurEffect(radius, radius)
    } else null

/**
 * The keyboard's height the last time it showed, so a panel in its place matches it. Taken only
 * once the keyboard has settled, not while it slides in or out.
 */
@Composable
private fun rememberImeHeight(imeBottom: Int, imeTarget: Int): Int {
    val context = LocalContext.current
    val preferences = remember { context.appPreferences }
    var height by remember { mutableStateOf(preferences.imeHeight) }
    LaunchedEffect(imeBottom, imeTarget) {
        if (imeBottom > 0 && imeBottom == imeTarget && imeBottom != height) {
            height = imeBottom
            preferences.imeHeight = imeBottom
        }
    }
    return height.coerceAtLeast(1)
}
