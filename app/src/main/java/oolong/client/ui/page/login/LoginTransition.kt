package oolong.client.ui.page.login

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import oolong.client.R
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Motion
import oolong.client.ui.common.theme.compose.SmoothCornerShape
import oolong.client.ui.common.theme.compose.TextStyles
import oolong.client.ui.widgets.compose.ArcFrame
import oolong.client.ui.widgets.compose.Avatar
import oolong.client.ui.widgets.compose.Button
import oolong.client.ui.widgets.compose.SecondaryButton
import oolong.client.ui.widgets.compose.condense
import oolong.client.ui.widgets.compose.drawWavyArc
import oolong.client.ui.widgets.compose.rememberLoaderTime
import oolong.client.ui.widgets.compose.spinningArc
import oolong.client.ui.widgets.compose.temporarySurface
import oolong.client.ui.widgets.compose.wavePhase
import kotlin.math.roundToInt

/** Where logging in stands once the web page has handed over the account's cookies. */
@Immutable
internal sealed interface LoginPhase {
    /** The web page has it: nothing to show over it. */
    data object Web : LoginPhase

    data object LoggingIn : LoginPhase

    data class Succeeded(val name: String, val avatar: String?) : LoginPhase

    data class Failed(val reason: String) : LoginPhase
}

/**
 * The card over the receded web page while the app logs the account in: a turning ring, closing
 * round into the account's avatar when it is in, or stopping with the reason and a way to try
 * again or go back to the page when not. It condenses in place, as a dialog does.
 */
@Composable
internal fun LoginCard(
    phase: LoginPhase,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = phase != LoginPhase.Web
    val enter = remember { Animatable(0f) }
    LaunchedEffect(shown) {
        enter.animateTo(
            targetValue = if (shown) 1f else 0f,
            animationSpec = if (shown) Motion.present(Motion.ProgressThreshold) else Motion.dismiss(Motion.ProgressThreshold),
        )
    }
    // Kept while it leaves, when the phase is the web page's again
    var last by remember { mutableStateOf(phase) }
    if (phase != LoginPhase.Web) last = phase
    if (!shown && enter.value == 0f) return
    // A failure shakes it once, side to side
    val shake = remember { Animatable(0f) }
    val shakePx = with(LocalDensity.current) { ShakeDistance.toPx() }
    LaunchedEffect(last) {
        if (last is LoginPhase.Failed) {
            shake.snapTo(shakePx)
            shake.animateTo(0f, Motion.release())
        }
    }
    CardBody(
        modifier = modifier
            .offset { IntOffset(shake.value.roundToInt(), 0) }
            .condense({ enter.value }, blur = CardBlur, scale = CardScale, fadeFaster = 3f),
        ring = { LoginRing(last) },
        title = when (val it = last) {
            is LoginPhase.Succeeded -> it.name
            is LoginPhase.Failed -> stringResource(id = R.string.login_failed_title)
            else -> stringResource(id = R.string.login_in_progress)
        },
        text = when (val it = last) {
            is LoginPhase.Succeeded -> stringResource(id = R.string.login_succeeded)
            is LoginPhase.Failed -> it.reason
            else -> null
        },
        actions = if (last is LoginPhase.Failed) {
            {
                SecondaryButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(id = R.string.login_back_to_page), maxLines = 1)
                }
                Button(onClick = onRetry, modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(id = R.string.button_retry), maxLines = 1)
                }
            }
        } else null,
    )
}

/** The card's frame and layout, shared with the notice it turns into, so the two match exactly. */
@Composable
internal fun CardBody(
    ring: @Composable () -> Unit,
    title: String,
    text: String?,
    modifier: Modifier = Modifier,
    actions: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .width(CardWidth)
            .temporarySurface(CardShape)
            .padding(top = CardPaddingTop, start = 22.dp, end = 22.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(RingSize), contentAlignment = Alignment.Center) { ring() }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            style = TextStyles.name,
            color = ExtendedTheme.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = text.orEmpty(),
            style = TextStyles.caption,
            color = ExtendedTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (actions != null) {
            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = actions,
            )
        }
    }
}

/**
 * The ring: the app's loader, larger, while logging in; it stops where it is on failure, and on
 * success runs round to a closed circle, its ripple settling, before the avatar comes up in it.
 */
@Composable
private fun LoginRing(phase: LoginPhase) {
    val time = rememberLoaderTime(running = phase == LoginPhase.LoggingIn || phase is LoginPhase.Succeeded)
    val close = remember { Animatable(0f) }
    val avatarIn = remember { Animatable(0f) }
    LaunchedEffect(phase is LoginPhase.Succeeded) {
        if (phase is LoginPhase.Succeeded) {
            close.animateTo(1f, Motion.settle(Motion.ProgressThreshold))
        } else {
            close.snapTo(0f)
            avatarIn.snapTo(0f)
        }
    }
    LaunchedEffect(close.value >= AvatarAfter) {
        if (close.value >= AvatarAfter) avatarIn.animateTo(1f, Motion.settle(Motion.ProgressThreshold))
    }
    val color = ExtendedTheme.colors.accent
    Box(
        modifier = Modifier
            .size(RingSize)
            .graphicsLayer { alpha = 1f - avatarIn.value }
            .drawWithCache {
                val path = Path()
                onDrawBehind {
                    val t = time.value
                    val spin = spinningArc(t)
                    val c = close.value
                    // Running round to the whole circle from wherever it was
                    val frame = ArcFrame(spin.start, spin.sweep + (1f - spin.sweep) * c, spin.rotation)
                    drawWavyArc(
                        frame = frame,
                        wave = 1f - c,
                        phase = wavePhase(t),
                        color = color,
                        path = path,
                        radius = RingRadius,
                        stroke = RingStroke,
                        amplitude = RingWave,
                    )
                }
            },
    )
    if (phase is LoginPhase.Succeeded) {
        Avatar(
            data = phase.avatar,
            size = CardAvatarSize,
            contentDescription = null,
            modifier = Modifier.graphicsLayer {
                alpha = avatarIn.value
                val s = 0.6f + 0.4f * avatarIn.value
                scaleX = s
                scaleY = s
            },
        )
    }
}

internal val CardWidth = 248.dp
internal val CardShape = SmoothCornerShape(30.dp)
internal val CardPaddingTop = 26.dp
internal val RingSize = 64.dp
internal val CardAvatarSize = 56.dp
private val RingRadius = 27.dp
private val RingStroke = 3.dp
private val RingWave = 1.4.dp
private val CardBlur = 8.dp
private const val CardScale = 0.9f
private val ShakeDistance = 8.dp

/** How far round the ring has run when the avatar starts to come up. */
private const val AvatarAfter = 0.6f
