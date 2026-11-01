package com.huanchengfly.tieba.post.ui.page.photoview

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.utils.ImageUtil
import com.huanchengfly.tieba.post.utils.TiebaUtil
import kotlinx.coroutines.launch
import kotlin.math.min

private class PeekAction(val icon: ImageVector, val label: String, val run: () -> Unit)

/**
 * The picture of [peek] held up over the blurred page, with what can be done with it below:
 * open it whole, save, share or copy its link. The finger that held it up can slide onto one and
 * lift to do it; lifted anywhere else, the picture stays up until something is tapped.
 */
@Composable
internal fun PhotoPeekOverlay(viewer: PhotoViewer, peek: PhotoPeek, origin: Offset) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val item = peek.data.picItems.getOrNull(peek.data.index) ?: return
    val url = item.originUrl

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val container = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val from = remember { peek.from.translate(-origin) }
        val menuWidth = with(density) { MenuWidth.toPx() }
        val menuHeight = with(density) { (ActionHeight * 4 + 12.dp).toPx() }
        val preview = remember(container) {
            previewRect(
                container = container,
                aspect = peek.aspect.takeIf { it > 0f } ?: (from.height / from.width),
                below = menuHeight + with(density) { Gap.toPx() },
                margin = with(density) { 24.dp.toPx() },
                maxWidth = with(density) { 420.dp.toPx() },
            )
        }

        val progress = remember { Animatable(0f) }
        var closing by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 480f)) }
        LaunchedEffect(Unit) { snapshotFlow { progress.value }.collect { viewer.backdrop = it.coerceIn(0f, 1f) } }

        fun dismiss() {
            if (closing) return
            closing = true
            scope.launch {
                progress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 600f, visibilityThreshold = 0.002f))
                viewer.backdrop = 0f
                viewer.closePeek()
            }
        }

        // The viewer takes over from where the picture is held up
        fun openWhole() {
            if (closing) return
            closing = true
            viewer.closePeek()
            viewer.open(peek.data, from = preview.translate(origin), thumbnail = peek.thumbnail, aspect = peek.aspect)
        }

        val actions = listOf(
            PeekAction(Icons.Rounded.OpenInFull, stringResource(id = R.string.photo_open_whole), ::openWhole),
            PeekAction(Icons.Rounded.Download, stringResource(id = R.string.desc_download_pic)) {
                ImageUtil.download(context, url)
                dismiss()
            },
            PeekAction(Icons.Rounded.Share, stringResource(id = R.string.title_share_pic)) {
                share(context, url)
                dismiss()
            },
            PeekAction(Icons.Rounded.Link, stringResource(id = R.string.title_copy_link)) {
                TiebaUtil.copyText(context, url)
                dismiss()
            },
        )
        val bounds = remember { mutableStateListOf(*Array(actions.size) { Rect.Zero }) }
        val hovered = viewer.peekFinger?.let { finger -> bounds.indexOfFirst { it.contains(finger) } }?.takeIf { it >= 0 }
        LaunchedEffect(hovered) {
            if (hovered != null) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        val liftsBefore = remember { viewer.peekLift?.first }
        LaunchedEffect(viewer.peekLift) {
            val (count, at) = viewer.peekLift ?: return@LaunchedEffect
            if (count == liftsBefore) return@LaunchedEffect
            bounds.indexOfFirst { it.contains(at) }.takeIf { it >= 0 }?.let { actions[it].run() }
        }

        BackHandler(enabled = !closing) { dismiss() }

        val p = progress.value
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = p.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = if (ExtendedTheme.colors.isNightMode) 0.45f else 0.2f))
                .pointerInput(Unit) { detectTapGestures { dismiss() } },
        )

        val rect = lerp(from, preview, p)
        Box(
            modifier = Modifier
                .offsetTo(rect)
                .graphicsLayer {
                    shape = RoundedCornerShape(lerp(8f, 18f, p.coerceIn(0f, 1f)).dp)
                    clip = true
                    shadowElevation = 24.dp.toPx() * p.coerceIn(0f, 1f)
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = ::openWhole),
        ) {
            AsyncImage(
                model = remember { request(context, url, peek.thumbnail) },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        val colors = ExtendedTheme.colors
        Column(
            modifier = Modifier
                .offsetTo(Rect(Offset((container.width - menuWidth) / 2, preview.bottom + with(density) { Gap.toPx() }), Size(menuWidth, menuHeight)))
                .graphicsLayer {
                    alpha = p.coerceIn(0f, 1f)
                    val scale = lerp(0.8f, 1f, p)
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    shape = RoundedCornerShape(18.dp)
                    clip = true
                }
                .background(colors.windowBackground)
                .padding(vertical = 6.dp),
        ) {
            actions.forEachIndexed { index, action ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ActionHeight)
                        .onGloballyPositioned { bounds[index] = it.boundsInWindow() }
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

/**
 * Where a picture of height over width [aspect] is held up: as large as fits above room for
 * [below], no wider than [maxWidth], the two centred together. Very tall or wide pictures are
 * cropped to a frame that still reads.
 */
private fun previewRect(container: Size, aspect: Float, below: Float, margin: Float, maxWidth: Float): Rect {
    val shape = aspect.coerceIn(0.5f, 1.4f)
    var width = min(container.width - margin * 2, maxWidth)
    var height = width * shape
    val maxHeight = container.height - below - margin * 2 - 48f
    if (height > maxHeight) {
        height = maxHeight
        width = height / shape
    }
    val top = ((container.height - height - below) / 2).coerceAtLeast(margin)
    return Rect(Offset((container.width - width) / 2, top), Size(width, height))
}

private val MenuWidth = 220.dp
private val ActionHeight = 46.dp
private val Gap = 12.dp
