package com.huanchengfly.tieba.post.ui.page.photoview

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.utils.ImageUtil
import com.huanchengfly.tieba.post.utils.TiebaUtil
import kotlinx.coroutines.launch

private class MenuAction(val icon: ImageVector, val label: String, val run: () -> Unit)

/**
 * What can be done with the picture of [menu], beside where the long press landed: save, share
 * or copy its link. The finger that pressed can slide onto one and lift to do it; lifted
 * anywhere else, the menu stays until something is tapped.
 */
@Composable
internal fun PhotoMenuOverlay(viewer: PhotoViewer, menu: PhotoMenu, origin: Offset) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val container = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val insets = WindowInsets.safeDrawing
        val top = insets.getTop(density).toFloat()
        val bottom = insets.getBottom(density).toFloat()
        val (size, gap, margin) = with(density) {
            Triple(Size(MenuWidth.toPx(), (ActionHeight * 3 + 12.dp).toPx()), 20.dp.toPx(), 12.dp.toPx())
        }
        val at = menu.at - origin
        // Beside the finger, below it where there is room and above it otherwise
        val below = at.y + gap + size.height <= container.height - bottom - margin
        val bounds = Rect(
            Offset(
                x = (at.x - size.width / 2).coerceIn(margin, (container.width - size.width - margin).coerceAtLeast(margin)),
                y = (if (below) at.y + gap else at.y - gap - size.height).coerceAtLeast(top + margin),
            ),
            size,
        )

        val progress = remember { Animatable(0f) }
        var closing by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { progress.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 520f)) }

        fun dismiss() {
            if (closing) return
            closing = true
            scope.launch {
                progress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 900f, visibilityThreshold = 0.002f))
                viewer.closeMenu()
            }
        }

        val actions = listOf(
            MenuAction(Icons.Rounded.Download, stringResource(id = R.string.desc_download_pic)) {
                ImageUtil.download(context, menu.url)
            },
            MenuAction(Icons.Rounded.Share, stringResource(id = R.string.title_share_pic)) { share(context, menu.url) },
            MenuAction(Icons.Rounded.Link, stringResource(id = R.string.title_copy_link)) {
                TiebaUtil.copyText(context, menu.url)
            },
        ).map { action ->
            MenuAction(action.icon, action.label) {
                action.run()
                dismiss()
            }
        }

        val actionBounds = remember { mutableStateListOf(*Array(actions.size) { Rect.Zero }) }
        val hovered = viewer.menuFinger?.let { finger -> actionBounds.indexOfFirst { it.contains(finger) } }?.takeIf { it >= 0 }
        LaunchedEffect(hovered) {
            if (hovered != null) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        val liftsBefore = remember { viewer.menuLift?.first }
        LaunchedEffect(viewer.menuLift) {
            val (count, lifted) = viewer.menuLift ?: return@LaunchedEffect
            if (count == liftsBefore) return@LaunchedEffect
            actionBounds.indexOfFirst { it.contains(lifted) }.takeIf { it >= 0 }?.let { actions[it].run() }
        }

        BackHandler(enabled = !closing) { dismiss() }

        val colors = ExtendedTheme.colors
        val p = progress.value
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = p.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = if (colors.isNightMode) 0.35f else 0.12f))
                .pointerInput(Unit) { detectTapGestures { dismiss() } },
        )
        Column(
            modifier = Modifier
                .offsetTo(bounds)
                .graphicsLayer {
                    alpha = p.coerceIn(0f, 1f)
                    val scale = lerp(0.6f, 1f, p)
                    scaleX = scale
                    scaleY = scale
                    // Grows out of the finger
                    transformOrigin = TransformOrigin(
                        ((at.x - bounds.left) / bounds.width).coerceIn(0f, 1f),
                        if (below) 0f else 1f,
                    )
                    shape = RoundedCornerShape(18.dp)
                    clip = true
                    shadowElevation = 16.dp.toPx() * p.coerceIn(0f, 1f)
                }
                .background(colors.windowBackground)
                .padding(vertical = 6.dp),
        ) {
            actions.forEachIndexed { index, action ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ActionHeight)
                        .onGloballyPositioned { actionBounds[index] = it.boundsInWindow() }
                        .padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (hovered == index) colors.chip else Color.Transparent)
                        .clickable(onClick = action.run)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(imageVector = action.icon, contentDescription = null, tint = colors.text, modifier = Modifier.size(20.dp))
                    Text(text = action.label, color = colors.text, fontSize = 15.sp)
                }
            }
        }
    }
}

private val MenuWidth = 200.dp
private val ActionHeight = 46.dp
