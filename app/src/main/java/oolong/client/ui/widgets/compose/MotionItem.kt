package oolong.client.ui.widgets.compose

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridItemScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import oolong.client.ui.common.theme.compose.Motion

/*
 * A list item that comes and goes with the list (design-system.md §7.2): a new item fades in, a
 * removed one fades out, and the rest make room on `settle`. Items need stable keys.
 *
 * An item also glides when one above it changes size. Where that size follows a finger, pass
 * [placement] false while it does, or the items below trail behind.
 */

/** Animates this item in a lazy list as items come, go and move. */
fun Modifier.motionItem(scope: LazyItemScope, placement: Boolean = true): Modifier = with(scope) {
    this@motionItem.animateItem(
        fadeInSpec = Motion.fadeIn(),
        placementSpec = if (placement) Motion.settle(IntOffset.VisibilityThreshold) else null,
        fadeOutSpec = Motion.fadeOut(),
    )
}

/** Animates this item in a lazy grid as items come, go and move. */
fun Modifier.motionItem(scope: LazyGridItemScope, placement: Boolean = true): Modifier = with(scope) {
    this@motionItem.animateItem(
        fadeInSpec = Motion.fadeIn(),
        placementSpec = if (placement) Motion.settle(IntOffset.VisibilityThreshold) else null,
        fadeOutSpec = Motion.fadeOut(),
    )
}

/** Animates this item in a staggered grid as items come, go and move. */
fun Modifier.motionItem(scope: LazyStaggeredGridItemScope, placement: Boolean = true): Modifier = with(scope) {
    this@motionItem.animateItem(
        fadeInSpec = Motion.fadeIn(),
        placementSpec = if (placement) Motion.settle(IntOffset.VisibilityThreshold) else null,
        fadeOutSpec = Motion.fadeOut(),
    )
}
