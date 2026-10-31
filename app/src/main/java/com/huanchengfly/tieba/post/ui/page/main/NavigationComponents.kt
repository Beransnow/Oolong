package com.huanchengfly.tieba.post.ui.page.main

import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material.MaterialTheme
import androidx.compose.material.NavigationRail
import androidx.compose.material.NavigationRailItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedColors
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.White
import com.huanchengfly.tieba.post.ui.utils.MainNavigationContentPosition
import com.huanchengfly.tieba.post.ui.widgets.compose.AccountNavIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.floatingGlass
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
fun NavigationRail(
    currentPosition: Int,
    onChangePosition: (position: Int) -> Unit,
    onReselected: (position: Int) -> Unit,
    navigationItems: ImmutableList<NavigationItem>,
    navigationContentPosition: MainNavigationContentPosition
) {
    NavigationRail(
        backgroundColor = ExtendedTheme.colors.bottomBar,
        contentColor = ExtendedTheme.colors.unselected,
        modifier = Modifier
            .fillMaxHeight()
            .windowInsetsPadding(
                WindowInsets.systemBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)
            ),
        elevation = 0.dp,
        header = {
            AccountNavIcon(spacer = false)
        }
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = if (navigationContentPosition == MainNavigationContentPosition.TOP) Arrangement.Top else Arrangement.Center
        ) {
            navigationItems.forEachIndexed { index, navigationItem ->
                NavigationRailItem(
                    selected = index == currentPosition,
                    onClick = {
                        if (index == currentPosition) {
                            onReselected(index)
                        } else {
                            onChangePosition(index)
                        }
                    },
                    icon = {
                        Box {
                            Icon(
                                painter = rememberAnimatedVectorPainter(
                                    animatedImageVector = navigationItem.icon(),
                                    atEnd = index == currentPosition
                                ),
                                contentDescription = navigationItem.title(index == currentPosition),
                                modifier = Modifier.size(16.dp),
                            )
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
                                            color = MaterialTheme.colors.secondary,
                                            shape = CircleShape
                                        ),
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun BottomNavigationDivider(
    themeColors: ExtendedColors = ExtendedTheme.colors
) {
    if (!themeColors.isNightMode) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .background(themeColors.divider)
        )
    }
}

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
        Row(
            modifier = Modifier
                .padding(top = (NavItemHeight - itemHeight))
                .floatingGlass(NavPillShape)
                .padding(NavPillPadding),
        ) {
            navigationItems.fastForEachIndexed { index, navigationItem ->
                val selected = index == currentPosition
                val title = navigationItem.title(selected)
                val contentColor =
                    if (selected) MaterialTheme.colors.secondary else themeColors.textSecondary
                Column(
                    modifier = Modifier
                        .size(width = itemWidth, height = itemHeight)
                        .clip(NavPillShape)
                        .background(
                            if (selected) MaterialTheme.colors.secondary.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = {
                                if (selected) {
                                    onReselected(index)
                                } else {
                                    onChangePosition(index)
                                }
                                navigationItem.onClick?.invoke()
                            },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box {
                        Icon(
                            painter = rememberAnimatedVectorPainter(
                                animatedImageVector = navigationItem.icon(),
                                atEnd = selected
                            ),
                            contentDescription = title,
                            tint = contentColor,
                            modifier = Modifier.size(24.dp),
                        )
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
                                        color = MaterialTheme.colors.secondary,
                                        shape = CircleShape
                                    ),
                            )
                        }
                    }
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
}

@Immutable
data class NavigationItem @OptIn(ExperimentalAnimationGraphicsApi::class) constructor(
    val id: String,
    val icon: @Composable () -> AnimatedImageVector,
    val title: @Composable (selected: Boolean) -> String,
    val badge: Boolean = false,
    val badgeText: String? = null,
    val onClick: (() -> Unit)? = null,
    val content: @Composable () -> Unit = {},
)