package oolong.client.ui.widgets.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tracks whether chrome should get out of the way: hidden after the content has been scrolled
 * forward by more than a threshold, shown again after scrolling back by the same amount or
 * pulling past the start of the content. Attach [nestedScrollConnection] to a parent of the
 * scrolling content.
 */
@Stable
class HideOnScrollState(private val thresholdPx: Float) {
    var hidden by mutableStateOf(false)
        private set

    private var accumulated = 0f

    fun show() {
        hidden = false
        accumulated = 0f
    }

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            val delta = consumed.y
            if (delta == 0f) {
                if (available.y > 0f) show()
                return Offset.Zero
            }
            if ((delta > 0f) != (accumulated > 0f)) accumulated = 0f
            accumulated += delta
            if (accumulated < -thresholdPx) {
                hidden = true
            } else if (accumulated > thresholdPx) {
                hidden = false
            }
            return Offset.Zero
        }
    }
}

@Composable
fun rememberHideOnScrollState(threshold: Dp = 24.dp): HideOnScrollState {
    val thresholdPx = with(LocalDensity.current) { threshold.toPx() }
    return remember(thresholdPx) { HideOnScrollState(thresholdPx) }
}

/** 0 while shown, 1 while hidden, animated in between. */
@Composable
fun HideOnScrollState.animateHiddenFraction(): State<Float> =
    animateFloatAsState(targetValue = if (hidden) 1f else 0f, label = "HideOnScroll")
