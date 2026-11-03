package oolong.client.ui.page.main

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.repository.Release
import oolong.client.repository.UpdateRepository
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Motion
import oolong.client.ui.common.theme.compose.Spacing
import oolong.client.ui.common.theme.compose.TextStyles
import oolong.client.ui.widgets.compose.SecondaryButton
import oolong.client.ui.widgets.compose.condense
import oolong.client.ui.widgets.compose.floatingGlass
import javax.inject.Inject

/**
 * Checks for an update once a day as the app opens, and offers a newer release once: shown here or
 * seen on the 软件更新 page, it is never offered again, whether it was opened or not.
 */
@HiltViewModel
class UpdateHintViewModel @Inject constructor(
    private val repository: UpdateRepository,
) : ViewModel() {
    private val _hint = MutableStateFlow<Release?>(null)
    val hint: StateFlow<Release?> = _hint.asStateFlow()

    init {
        repository.checkIfDue()
    }

    /** Waits for a release not yet offered, and offers it. */
    suspend fun await() {
        val release = repository.available.filterNotNull().first { !repository.hinted(it) }
        repository.markHinted(release)
        _hint.value = release
    }

    fun dismiss() {
        _hint.value = null
    }
}

/**
 * A new version, said once in a toast (design-system.md §9.5), for a scaffold's snackbar host,
 * which sets it above the bottom bar; tapping it opens [onOpen], the 软件更新 page. Gone by itself
 * after a while, and while [hidden].
 */
@Composable
fun UpdateHint(hidden: Boolean, onOpen: () -> Unit, viewModel: UpdateHintViewModel = hiltViewModel()) {
    val hint by viewModel.hint.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // Only while the page shows, so a release seen on the update page meanwhile isn't offered again
    LifecycleResumeEffect(viewModel) {
        val waiting = scope.launch { viewModel.await() }
        onPauseOrDispose { waiting.cancel() }
    }
    LaunchedEffect(hint) {
        if (hint != null) {
            delay(HintMillis)
            viewModel.dismiss()
        }
    }
    // Kept through the exit, when the hint is gone already
    val shown = remember { mutableStateOf(hint) }.apply { if (hint != null) value = hint }.value
    val visible = hint != null && !hidden
    // Condenses where it stands, and comes apart there again (design-system.md §7.2)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        progress.animateTo(
            targetValue = if (visible) 1f else 0f,
            animationSpec = if (visible) Motion.morph(Motion.ProgressThreshold) else Motion.dismiss(Motion.ProgressThreshold),
        )
    }
    if (shown != null && (visible || progress.value > 0f)) {
        HintToast(
            release = shown,
            onOpen = {
                viewModel.dismiss()
                onOpen()
            },
            modifier = Modifier.condense({ progress.value }, blur = HintBlur, rise = HintRise, scale = HintScale, fadeFaster = 3f),
        )
    }
}

@Composable
private fun HintToast(release: Release, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ExtendedTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(horizontal = Spacing.md)
            .padding(bottom = HintGap)
            .widthIn(max = 480.dp)
            .heightIn(min = 48.dp)
            .floatingGlass(HintShape, interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen)
            .padding(start = Spacing.lg, end = Spacing.xxs),
    ) {
        Text(
            text = stringResource(id = R.string.update_hint, release.versionName),
            style = TextStyles.label,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        SecondaryButton(onClick = onOpen) {
            Text(text = stringResource(id = R.string.update_hint_view), style = TextStyles.label, color = colors.accent)
        }
    }
}

private val HintShape = RoundedCornerShape(percent = 50)

/** Between the hint and the bottom bar the scaffold sets it on. */
private val HintGap = 12.dp

private val HintBlur = 8.dp
private val HintRise = 12.dp
private const val HintScale = 0.9f

private const val HintMillis = 6_000L
