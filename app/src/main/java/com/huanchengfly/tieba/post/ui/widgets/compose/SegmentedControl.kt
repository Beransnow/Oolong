package com.huanchengfly.tieba.post.ui.widgets.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.Motion
import com.huanchengfly.tieba.post.ui.common.theme.compose.TextStyles
import kotlinx.collections.immutable.ImmutableList

/**
 * Choosing one of a few [options] side by side (design-system.md §9.1): a capsule track with the
 * chosen one on a pill that moves with `snap`.
 */
@Composable
fun SegmentedControl(
    options: ImmutableList<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ExtendedTheme.colors
    val position by animateFloatAsState(
        targetValue = selected.toFloat(),
        animationSpec = Motion.snap(Motion.ProgressThreshold),
        label = "segment",
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.chip)
            .drawBehind {
                val inset = TrackInset.toPx()
                val width = (size.width - inset * 2) / options.size
                val height = size.height - inset * 2
                drawRoundRect(
                    color = colors.background,
                    topLeft = Offset(inset + width * position, inset),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(height / 2),
                )
            }
            .padding(TrackInset),
    ) {
        options.forEachIndexed { index, option ->
            val chosen = index == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 32.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                    ) { if (!chosen) onSelect(index) }
                    .semantics { this.selected = chosen }
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    text = option,
                    style = TextStyles.label,
                    fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (chosen) colors.text else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val TrackInset = 3.dp
