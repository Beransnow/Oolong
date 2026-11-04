package oolong.client.ui.widgets.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Motion
import oolong.client.ui.common.theme.compose.SmoothCornerShape
import oolong.client.ui.common.theme.compose.Spacing
import oolong.client.ui.common.theme.compose.TextStyles

/**
 * A toast (design-system.md §9.3): a glass capsule of [text] and an [action] button. While
 * [visible] it condenses where it stands, from blurred and small, and when not it comes apart
 * there again (§7.2), so it never crosses the bar it stands over. Tapping it anywhere else runs
 * [onClick], when given. The caller keeps [text] while it leaves.
 */
@Composable
fun GlassToast(
    visible: Boolean,
    text: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        progress.animateTo(
            targetValue = if (visible) 1f else 0f,
            animationSpec = if (visible) Motion.morph(Motion.ProgressThreshold) else Motion.dismiss(Motion.ProgressThreshold),
        )
    }
    if (!visible && progress.value == 0f) return
    val colors = ExtendedTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .condense({ progress.value }, blur = ToastBlur, rise = ToastRise, scale = ToastScale, fadeFaster = 3f)
            .padding(horizontal = Spacing.md)
            .widthIn(max = 480.dp)
            .heightIn(min = 48.dp)
            .floatingGlass(ToastShape, interactionSource = if (onClick != null) interactionSource else null)
            .then(if (onClick != null) Modifier.clickable(interactionSource, indication = null, onClick = onClick) else Modifier)
            .padding(start = Spacing.lg, end = Spacing.xxs),
    ) {
        Text(
            text = text,
            style = TextStyles.label,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        SecondaryButton(onClick = onAction) {
            Text(text = action, style = TextStyles.label, color = colors.accent)
        }
    }
}

private val ToastShape = SmoothCornerShape.Capsule
private val ToastBlur = 8.dp
private val ToastRise = 12.dp
private const val ToastScale = 0.9f
