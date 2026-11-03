package oolong.client.ui.widgets.compose.picker

import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlin.math.abs

/**
 * A wheel of [items] to pick one from, [visible] rows tall, the picked one in the middle row:
 * flung, it comes to rest on a row, ticking as each row passes the middle. Rows further from the
 * middle are fainter, smaller and turned away, as on a drum. A [cyclic] wheel goes round.
 */
@Composable
fun <T> WheelPicker(
    items: List<T>,
    selectedItem: T,
    onItemChanged: (T) -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    itemHeight: Dp = WheelItemHeight,
    visible: Int = WheelVisibleRows,
    cyclic: Boolean = true,
    itemToString: (T) -> String = { it.toString() },
) {
    if (items.isEmpty()) return
    val haptic = LocalHapticFeedback.current
    val count = if (cyclic) Int.MAX_VALUE else items.size
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (if (cyclic) count / 2 - (count / 2) % items.size else 0) + items.indexOf(selectedItem).coerceAtLeast(0),
    )
    // The row whose middle is nearest the wheel's
    val centered by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - middle) }?.index
        }
    }
    val currentOnItemChanged by rememberUpdatedState(onItemChanged)
    LaunchedEffect(listState, items) {
        snapshotFlow { centered }.filterNotNull().drop(1).collect { index ->
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            currentOnItemChanged(items[index % items.size])
        }
    }

    Box(modifier = modifier.height(itemHeight * visible), contentAlignment = Alignment.Center) {
        LazyColumn(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState, SnapPosition.Center),
            contentPadding = PaddingValues(vertical = itemHeight * (visible / 2)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(count) { i ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .graphicsLayer {
                            val info = listState.layoutInfo
                            val item = info.visibleItemsInfo.firstOrNull { it.index == i } ?: return@graphicsLayer
                            val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                            // How many rows from the middle, signed
                            val rows = ((item.offset + item.size / 2f - middle) / item.size).coerceIn(-RowsToEdge, RowsToEdge)
                            val away = abs(rows) / RowsToEdge
                            alpha = 1f - away * (1f - EdgeAlpha)
                            val scale = 1f - away * (1f - EdgeScale)
                            scaleX = scale
                            scaleY = scale
                            rotationX = -rows * RowTilt
                        },
                ) {
                    Text(text = itemToString(items[i % items.size]), style = textStyle)
                }
            }
        }
    }
}

internal val WheelItemHeight = 40.dp
internal const val WheelVisibleRows = 5

/** How many rows from the middle a wheel's edge is, and how its rows look there. */
private const val RowsToEdge = 2.5f
private const val EdgeAlpha = 0.25f
private const val EdgeScale = 0.82f

/** How far each row turns away for each row it is from the middle, in degrees. */
private const val RowTilt = 16f
