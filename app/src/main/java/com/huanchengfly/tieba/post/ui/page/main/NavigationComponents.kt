package com.huanchengfly.tieba.post.ui.page.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedColors
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.White
import com.huanchengfly.tieba.post.ui.widgets.compose.Avatar
import com.huanchengfly.tieba.post.ui.widgets.compose.ContextMenuItem
import com.huanchengfly.tieba.post.ui.widgets.compose.LocalContextMenus
import com.huanchengfly.tieba.post.ui.widgets.compose.floatingGlass
import com.huanchengfly.tieba.post.ui.widgets.compose.glassLens
import com.huanchengfly.tieba.post.ui.widgets.compose.lensTarget
import com.huanchengfly.tieba.post.ui.widgets.compose.longPressMenu
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.util.lerp as lerpFloat

/**
 * The main navigation on wider windows: the floating pill stood upright beside the panes. It
 * stays while pages are open, highlighting the tab they were opened from; choosing a tab goes
 * back to the main page on that tab, and choosing the shown one again refreshes it. Labels go
 * where the window is too short for them.
 */
@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
fun NavigationRail(
    currentPosition: Int,
    onSelect: (position: Int) -> Unit,
    navigationItems: ImmutableList<NavigationItem>,
    showLabels: Boolean,
    modifier: Modifier = Modifier,
    themeColors: ExtendedColors = ExtendedTheme.colors,
) {
    val itemWidth = if (showLabels) NavItemWidth else NavItemCollapsedWidth
    val itemHeight = if (showLabels) NavItemHeight else NavItemCollapsedWidth
    val indicator = remember { Animatable(currentPosition.toFloat()) }
    LaunchedEffect(currentPosition) { indicator.animateTo(currentPosition.toFloat(), IndicatorSpring) }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .windowInsetsPadding(
                WindowInsets.systemBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)
            )
            .padding(horizontal = NavPillMargin),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .floatingGlass(NavPillShape)
                .padding(NavPillPadding),
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, (indicator.value * itemHeight.toPx()).roundToInt()) }
                    .size(width = itemWidth, height = itemHeight)
                    .background(ExtendedTheme.colors.emphasis.copy(alpha = 0.12f), NavPillShape)
            )
            Column {
                navigationItems.fastForEachIndexed { index, navigationItem ->
                    val selected = index == currentPosition
                    val title = navigationItem.title(selected)
                    val contentColor =
                        if (selected) ExtendedTheme.colors.emphasis else themeColors.textSecondary
                    val menu = navigationItem.menu
                    Column(
                        modifier = Modifier
                            .size(width = itemWidth, height = itemHeight)
                            .clip(NavPillShape)
                            .then(
                                if (menu == null) {
                                    Modifier.selectable(
                                        selected = selected,
                                        role = Role.Tab,
                                        onClick = { onSelect(index) },
                                    )
                                } else {
                                    Modifier
                                        .semantics {
                                            role = Role.Tab
                                            this.selected = selected
                                        }
                                        .longPressMenu(items = menu, onClick = { onSelect(index) })
                                }
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        NavItemIcon(navigationItem, selected, title, contentColor)
                        if (showLabels) {
                            Text(
                                text = title,
                                color = contentColor,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** An item's icon, animating to its selected form, with the item's badge on its corner. */
@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
private fun NavItemIcon(
    navigationItem: NavigationItem,
    selected: Boolean,
    title: String,
    tint: Color,
) {
    Box {
        if (navigationItem.avatar != null) {
            // Ringed in the selected colour when chosen, as the icons are tinted
            val ring by animateDpAsState(if (selected) AvatarRing else 0.dp, label = "ring")
            Avatar(
                data = navigationItem.avatar,
                contentDescription = title,
                modifier = Modifier
                    .size(24.dp)
                    .border(ring, tint, CircleShape)
                    .padding(ring * 2),
            )
        } else {
            Icon(
                painter = rememberAnimatedVectorPainter(
                    animatedImageVector = navigationItem.icon(),
                    atEnd = selected
                ),
                contentDescription = title,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
        if (navigationItem.badge) {
            Text(
                textAlign = TextAlign.Center,
                fontSize = 10.sp,
                color = White,
                text = navigationItem.badgeText ?: "",
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .align(Alignment.TopEnd)
                    .background(
                        color = ExtendedTheme.colors.emphasis,
                        shape = CircleShape
                    ),
            )
        }
    }
}

private val AvatarRing = 1.5.dp

private val NavPillShape = RoundedCornerShape(percent = 50)
private val NavPillPadding = 6.dp
private val NavItemWidth = 68.dp
private val NavItemCollapsedWidth = 52.dp
private val NavItemHeight = 52.dp
private val NavItemCollapsedHeight = 40.dp
private val NavPillMargin = 12.dp

/** Height the pill and its margins take above the navigation bar, expanded. */
val FloatingNavBarHeight = NavItemHeight + NavPillPadding * 2 + NavPillMargin

/**
 * The main navigation as a pill floating over the content, above the system navigation bar.
 * Each item shows its icon and a short label; while [collapsedFraction] goes to 1 (the content
 * is being scrolled forward) the labels fade out and the pill shrinks to its icons.
 *
 * The selection indicator slides between items. While the pill is pressed the indicator lifts
 * off as a clear glass lens, larger than the pill, that magnifies the item under it; dragging
 * moves the lens with the finger and releasing selects the item it ends on. Held still on an item
 * with a menu, the lens settles back and the menu opens under the finger, which can slide onto
 * an item of it and lift to pick it.
 *
 * The bar always measures at its expanded height so content padding does not change while it
 * collapses.
 */
@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
fun FloatingNavBar(
    currentPosition: Int,
    onChangePosition: (position: Int) -> Unit,
    onReselected: (position: Int) -> Unit,
    navigationItems: ImmutableList<NavigationItem>,
    collapsedFraction: () -> Float,
    themeColors: ExtendedColors = ExtendedTheme.colors,
) {
    val coroutineScope = rememberCoroutineScope()
    val indicator = remember { Animatable(currentPosition.toFloat()) }
    val press = remember { Animatable(0f) }
    var indicatorDragged by remember { mutableStateOf(false) }
    LaunchedEffect(currentPosition) {
        if (!indicatorDragged) indicator.animateTo(currentPosition.toFloat(), IndicatorSpring)
    }
    val menus = LocalContextMenus.current
    val haptic = LocalHapticFeedback.current
    val items by rememberUpdatedState(navigationItems)
    var pill by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val itemBounds = remember { mutableMapOf<Int, Rect>() }
    val select by rememberUpdatedState { index: Int ->
        if (index == currentPosition) {
            onReselected(index)
        } else {
            onChangePosition(index)
        }
        navigationItems[index].onClick?.invoke()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.navigationBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
            )
            .height(FloatingNavBarHeight),
        contentAlignment = Alignment.TopCenter,
    ) {
        val fraction = collapsedFraction()
        val itemWidth = lerp(NavItemWidth, NavItemCollapsedWidth, fraction)
        val itemHeight = lerp(NavItemHeight, NavItemCollapsedHeight, fraction)
        val density = LocalDensity.current
        val itemWidthPx = with(density) { itemWidth.toPx() }
        // The item under the indicator, so colours follow it while it is dragged.
        val highlighted = indicator.value.roundToInt()
        Box(modifier = Modifier.padding(top = (NavItemHeight - itemHeight))) {
            Box(
                modifier = Modifier
                    .lensTarget()
                    .floatingGlass(NavPillShape)
                    .padding(NavPillPadding)
                    .onGloballyPositioned { pill = it }
                    .pointerInput(navigationItems.size, itemWidthPx, menus) {
                        val last = navigationItems.size - 1
                        fun positionAt(x: Float) = (x / itemWidthPx - 0.5f).coerceIn(0f, last.toFloat())
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            coroutineScope.launch { press.animateTo(1f, PressSpring) }
                            coroutineScope.launch {
                                indicator.animateTo(
                                    positionAt(down.position.x).roundToInt().toFloat(),
                                    IndicatorSpring
                                )
                            }
                            var dragging = false
                            var lastX = down.position.x
                            var released = false
                            var lost = false
                            fun follow(event: PointerEvent) {
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) {
                                    lost = true
                                    return
                                }
                                if (!change.pressed) {
                                    released = true
                                    return
                                }
                                lastX = change.position.x
                                if (!dragging &&
                                    abs(lastX - down.position.x) > viewConfiguration.touchSlop
                                ) {
                                    dragging = true
                                    indicatorDragged = true
                                }
                                if (dragging) {
                                    change.consume()
                                    coroutineScope.launch { indicator.snapTo(positionAt(lastX)) }
                                }
                            }
                            val menu = items[(down.position.x / itemWidthPx).toInt().coerceIn(0, last)].menu
                            // Until the finger moves off or lifts, a menu waits for the press to last
                            val held = menu != null && menus != null &&
                                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                    while (!dragging && !released && !lost) follow(awaitPointerEvent())
                                } == null
                            if (held && menu != null && menus != null) {
                                coroutineScope.launch { press.animateTo(0f, PressSpring) }
                                coroutineScope.launch {
                                    indicator.animateTo(currentPosition.toFloat(), IndicatorSpring)
                                }
                                val coordinates = pill ?: return@awaitEachGesture
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menus.show(menu(), anchor = Rect(coordinates.localToWindow(down.position), Size.Zero))
                                while (true) {
                                    val change = awaitPointerEvent().changes
                                        .firstOrNull { it.id == down.id } ?: break
                                    change.consume()
                                    val position = coordinates.localToWindow(change.position)
                                    if (!change.pressed) {
                                        menus.liftFinger(position)
                                        break
                                    }
                                    menus.moveFinger(position)
                                }
                                return@awaitEachGesture
                            }
                            while (!released && !lost) follow(awaitPointerEvent())
                            coroutineScope.launch { press.animateTo(0f, PressSpring) }
                            indicatorDragged = false
                            if (released) {
                                val target = if (dragging) {
                                    positionAt(lastX).roundToInt()
                                } else {
                                    (down.position.x / itemWidthPx).toInt().coerceIn(0, last)
                                }
                                coroutineScope.launch {
                                    indicator.animateTo(target.toFloat(), IndicatorSpring)
                                }
                                select(target)
                            } else {
                                coroutineScope.launch {
                                    indicator.animateTo(currentPosition.toFloat(), IndicatorSpring)
                                }
                            }
                        }
                    },
            ) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset((indicator.value * itemWidthPx).roundToInt(), 0) }
                        .size(width = itemWidth, height = itemHeight)
                        .graphicsLayer { alpha = 1f - press.value }
                        .background(ExtendedTheme.colors.emphasis.copy(alpha = 0.12f), NavPillShape)
                )
                Row {
                    navigationItems.fastForEachIndexed { index, navigationItem ->
                        val selected = index == currentPosition
                        val title = navigationItem.title(selected)
                        val contentColor =
                            if (index == highlighted) ExtendedTheme.colors.emphasis
                            else themeColors.textSecondary
                        Column(
                            modifier = Modifier
                                .size(width = itemWidth, height = itemHeight)
                                .onGloballyPositioned { itemBounds[index] = it.boundsInWindow() }
                                .semantics(mergeDescendants = true) {
                                    role = Role.Tab
                                    this.selected = selected
                                    onClick(label = title) {
                                        select(index)
                                        true
                                    }
                                    val menu = navigationItem.menu
                                    if (menu != null && menus != null) {
                                        onLongClick(label = navigationItem.menuLabel) {
                                            val bounds = itemBounds[index] ?: return@onLongClick false
                                            menus.show(menu(), anchor = bounds)
                                            true
                                        }
                                    }
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            NavItemIcon(navigationItem, selected, title, contentColor)
                            if (fraction < 1f) {
                                Text(
                                    text = title,
                                    color = contentColor,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .height(14.dp * (1f - fraction))
                                        .graphicsLayer { alpha = 1f - fraction * 2f },
                                )
                            }
                        }
                    }
                }
            }
            // Lifts off the pill while it is pressed: drawn above it, so it magnifies the pill too.
            // Grows by layout rather than a layer scale so the glass samples the area it covers.
            val lensScale = lerpFloat(1f, LensScale, press.value)
            val lensWidth = itemWidth * lensScale
            val lensHeight = itemHeight * lensScale
            Box(
                modifier = Modifier
                    .offset {
                        val padding = NavPillPadding.roundToPx()
                        val grownX = ((lensWidth - itemWidth) / 2).roundToPx()
                        val grownY = ((lensHeight - itemHeight) / 2).roundToPx()
                        IntOffset(
                            (indicator.value * itemWidthPx).roundToInt() + padding - grownX,
                            padding - grownY,
                        )
                    }
                    .size(width = lensWidth, height = lensHeight)
                    .graphicsLayer { alpha = press.value }
                    .glassLens(NavPillShape)
            )
        }
    }
}

private val IndicatorSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 380f)
private val PressSpring = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)

/** How much the lens outgrows the item it lifts off from. */
private const val LensScale = 1.4f

@Immutable
data class NavigationItem @OptIn(ExperimentalAnimationGraphicsApi::class) constructor(
    val id: String,
    val icon: @Composable () -> AnimatedImageVector,
    val title: @Composable (selected: Boolean) -> String,
    val badge: Boolean = false,
    val badgeText: String? = null,
    val onClick: (() -> Unit)? = null,
    /** Shown in place of the icon, such as the signed-in account's avatar. */
    val avatar: String? = null,
    /** What a long press on it offers, labelled [menuLabel] for accessibility; null for nothing. */
    val menu: (() -> List<ContextMenuItem>)? = null,
    val menuLabel: String? = null,
)
