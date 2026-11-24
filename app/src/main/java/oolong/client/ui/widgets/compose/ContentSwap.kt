package oolong.client.ui.widgets.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oolong.client.ui.common.theme.compose.Motion

private val EnterBlur = 8.dp
private const val EnterScale = 0.96f

/** Opacity runs ahead of the spring, which starts from rest and would keep it faint at first. */
private const val EnterFadeFaster = 2.5f

/**
 * Shows [targetState]'s content in a region, such as loading, results or a page's body, and
 * replaces it as a whole when the state changes (design-system.md §7.2): what was there fades
 * away while the new content condenses from blurred and a little smaller into focus. With motion
 * reduced it only fades. The first state shows at once. For a line of text use [Refocus].
 *
 * @param contentKey what makes two states different content; states with the same key update in
 * place, as an account's counts do, without a swap
 */
@Composable
fun <T> ContentSwap(
    targetState: T,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    contentKey: (T) -> Any? = { it },
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        contentAlignment = contentAlignment,
        contentKey = contentKey,
        transitionSpec = { EnterTransition.None togetherWith fadeOut(Motion.fadeOut()) using null },
        label = "ContentSwap",
    ) { shown ->
        val focus = transition.animateFloat(
            transitionSpec = { Motion.settle(Motion.ProgressThreshold) },
            label = "ContentSwapFocus",
        ) { if (it == EnterExitState.PreEnter) 0f else 1f }
        Box(
            modifier = Modifier.condense({ focus.value }, blur = EnterBlur, scale = EnterScale, fadeFaster = EnterFadeFaster),
            contentAlignment = contentAlignment,
        ) {
            content(shown)
        }
    }
}
