package oolong.client.ui.page.settings.update

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import oolong.client.BuildConfig
import oolong.client.R
import oolong.client.repository.Release
import oolong.client.repository.UpdateChannel
import oolong.client.repository.UpdateCheck
import oolong.client.repository.UpdateDownload
import oolong.client.repository.UpdateVerificationException
import oolong.client.ui.common.prefs.PrefsChoiceValue
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.PressFeedback
import oolong.client.ui.common.theme.compose.Radius
import oolong.client.ui.common.theme.compose.SmoothCornerShape
import oolong.client.ui.common.theme.compose.TextStyles
import oolong.client.ui.common.theme.compose.pressScale
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.page.settings.about.AboutMark
import oolong.client.ui.page.settings.about.LensOverlay
import oolong.client.ui.page.settings.about.MarkLens
import oolong.client.ui.widgets.compose.BackNavigationIcon
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.GlassTopBarTitle
import oolong.client.ui.widgets.compose.Refocus
import oolong.client.ui.widgets.compose.Switch
import oolong.client.ui.widgets.compose.TextButton
import oolong.client.ui.widgets.compose.choiceMenuItems
import oolong.client.ui.widgets.compose.clickMenu
import oolong.client.ui.widgets.compose.menuAnchor
import oolong.client.ui.widgets.compose.rememberMenuTriggerState
import java.util.Locale

/**
 * Software update, a page of its own (design-system.md §9.5): the app and where its version stands,
 * what the release offered changes, the update settings, and one button at the bottom that checks,
 * downloads (showing how far it has got) and installs.
 */
@Composable
fun UpdatePage(
    navigator: Navigator = LocalNavigator.current,
    viewModel: UpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = R.string.title_software_update)) },
                navigationIcon = { BackNavigationIcon(onBackPressed = { navigator.navigateUp() }) },
            )
        },
    ) { contentPaddings ->
        val lens = remember { MarkLens() }
        val page = rememberHazeState()
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(page)
                    .verticalScroll(rememberScrollState())
                    .padding(contentPaddings)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = DockRoom),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Hero(lens, state)
                val shown = state.offered ?: (state.check as? UpdateCheck.Done)?.latest
                if (shown != null) {
                    Notes(shown, isNew = state.offered != null)
                }
                val offered = state.offered
                val busy = state.download is UpdateDownload.Running || state.download is UpdateDownload.Ready
                if (offered != null && !offered.required && !busy) {
                    TextButton(onClick = { viewModel.skip(offered) }, color = ExtendedTheme.colors.textSecondary) {
                        Text(text = stringResource(id = R.string.update_skip), style = TextStyles.caption)
                    }
                }
                Settings(state, onAutoCheck = viewModel::setAutoCheck, onChannel = viewModel::setChannel)
            }
            LensOverlay(lens = lens, page = page, markSize = MarkSize)
            Dock(
                state = state,
                onCheck = viewModel::check,
                onDownload = viewModel::download,
                onInstall = viewModel::install,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** The mark, the name, the version and where it stands. */
@Composable
private fun Hero(lens: MarkLens, state: UpdateState) {
    val colors = ExtendedTheme.colors
    Column(
        modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AboutMark(lens = lens, size = MarkSize)
        Text(
            text = stringResource(id = R.string.app_name),
            modifier = Modifier.padding(top = 16.dp),
            style = Name.copy(color = colors.text),
        )
        Text(
            text = stringResource(id = R.string.tip_about, BuildConfig.VERSION_NAME),
            modifier = Modifier.padding(top = 2.dp),
            style = TextStyles.caption.copy(color = colors.textSecondary),
        )
        val status = when (val check = state.check) {
            UpdateCheck.Idle -> ""
            UpdateCheck.Checking -> stringResource(id = R.string.update_status_checking)
            is UpdateCheck.Failed -> stringResource(id = R.string.update_status_failed)
            is UpdateCheck.Done -> if (check.latest.isNewer) {
                stringResource(id = R.string.update_status_found, check.latest.versionName)
            } else {
                stringResource(id = R.string.update_status_latest)
            }
        }
        Refocus(targetState = status, modifier = Modifier.padding(top = 12.dp).heightIn(min = 20.dp)) {
            Text(
                text = it,
                style = TextStyles.caption.copy(
                    color = if (state.offered != null) colors.emphasis else colors.textSecondary,
                    fontWeight = if (state.offered != null) FontWeight.Medium else FontWeight.Normal,
                ),
            )
        }
    }
}

/** What [release] changes, grouped as its manifest groups it. */
@Composable
private fun Notes(release: Release, isNew: Boolean) {
    val colors = ExtendedTheme.colors
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.chip)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            Text(
                text = if (isNew) release.versionName else stringResource(id = R.string.update_this_version),
                style = TextStyles.caption.copy(color = colors.textSecondary),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${Formatter.formatShortFileSize(context, release.size)} · ${release.publishedAt}",
                style = TextStyles.caption.copy(color = colors.textSecondary),
            )
        }
        release.notesFor(Locale.getDefault()).forEach { (group, items) ->
            Text(text = group, style = TextStyles.label.copy(color = colors.textSecondary))
            Column(modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)) {
                items.forEach { item ->
                    Row {
                        Text(text = "·", style = TextStyles.bodyCompact.copy(color = colors.textSecondary), modifier = Modifier.padding(end = 8.dp))
                        Text(text = item, style = TextStyles.bodyCompact.copy(color = colors.text))
                    }
                }
            }
        }
    }
}

@Composable
private fun Settings(state: UpdateState, onAutoCheck: (Boolean) -> Unit, onChannel: (UpdateChannel) -> Unit) {
    val colors = ExtendedTheme.colors
    Text(
        text = stringResource(id = R.string.update_settings),
        style = TextStyles.caption.copy(color = colors.textSecondary),
        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
    Column(
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.chip),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(interactionSource = null, indication = PressFeedback.Overlay) { onAutoCheck(!state.autoCheck) }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(id = R.string.update_auto_check), style = TextStyles.bodyCompact.copy(color = colors.text), modifier = Modifier.weight(1f))
            Switch(checked = state.autoCheck, onCheckedChange = onAutoCheck)
        }
        // Chosen in a menu growing out of the value, as settings' choices are
        val menu = rememberMenuTriggerState()
        val channels = UpdateChannel.entries.map { it to stringResource(id = it.label) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickMenu(items = { choiceMenuItems(channels, state.channel, onChannel) }, indication = PressFeedback.Overlay, state = menu)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(id = R.string.update_channel), style = TextStyles.bodyCompact.copy(color = colors.text), modifier = Modifier.weight(1f))
            ProvideTextStyle(TextStyles.bodyCompact.copy(color = colors.textSecondary)) {
                PrefsChoiceValue(text = stringResource(id = state.channel.label), modifier = Modifier.menuAnchor(menu))
            }
        }
    }
}

/**
 * The button at the bottom, over the page faded out behind it: it checks, downloads and installs,
 * and is the download's progress bar meanwhile. Above it, why it couldn't go on, when it couldn't.
 */
@Composable
private fun Dock(
    state: UpdateState,
    onCheck: () -> Unit,
    onDownload: (Release) -> Unit,
    onInstall: (UpdateDownload.Ready) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ExtendedTheme.colors
    Box(modifier = modifier.fillMaxWidth()) {
        // The page fades out under the button, as it does under the bar at the top
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to colors.background.copy(alpha = 0.85f), 1f to colors.background)),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val download = state.download
            val note = when {
                download is UpdateDownload.Failed -> stringResource(
                    id = if (download.error is UpdateVerificationException) R.string.update_verify_failed else R.string.update_download_failed,
                )
                else -> null
            }
            if (note != null) {
                Text(text = note, style = TextStyles.caption.copy(color = colors.textSecondary), textAlign = TextAlign.Center)
            }
            val offered = state.offered
            when {
                download is UpdateDownload.Running -> ProgressButton(
                    text = stringResource(id = R.string.update_downloading, (download.progress * 100).toInt()),
                    progress = download.progress,
                    onClick = {},
                )
                download is UpdateDownload.Ready -> ProgressButton(text = stringResource(id = R.string.update_install), onClick = { onInstall(download) })
                download is UpdateDownload.Failed ->
                    ProgressButton(text = stringResource(id = R.string.button_retry), onClick = { onDownload(download.release) })
                offered != null -> ProgressButton(text = stringResource(id = R.string.update_download), onClick = { onDownload(offered) })
                state.check == UpdateCheck.Checking ->
                    ProgressButton(text = stringResource(id = R.string.update_status_checking), enabled = false, onClick = {})
                else -> ProgressButton(text = stringResource(id = R.string.update_check), onClick = onCheck)
            }
        }
    }
}

/**
 * The capsule button, filled with the emphasis colour; given a [progress], only that much of it is
 * filled, from the left, over a tint of the same colour, the label turning over where the fill is.
 */
@Composable
private fun ProgressButton(text: String, onClick: () -> Unit, progress: Float? = null, enabled: Boolean = true) {
    val colors = ExtendedTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val label = TextStyles.label.copy(fontSize = 16.sp)
    Box(
        modifier = Modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .height(52.dp)
            .pressScale(interactionSource)
            .clip(RoundedCornerShape(50))
            .background(if (progress != null) colors.emphasis.copy(alpha = 0.18f) else colors.emphasis)
            .clickable(interactionSource = interactionSource, indication = PressFeedback.Overlay, enabled = enabled && progress == null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (progress == null) {
            Text(text = text, style = label.copy(color = colors.onEmphasis))
        } else {
            Text(text = text, style = label.copy(color = colors.emphasis))
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithContent { clipRect(right = size.width * progress) { this@drawWithContent.drawContent() } }
                    .background(colors.emphasis),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = text, style = label.copy(color = colors.onEmphasis))
            }
        }
    }
}

private val UpdateChannel.label: Int
    get() = when (this) {
        UpdateChannel.Stable -> R.string.update_channel_stable
        UpdateChannel.Beta -> R.string.update_channel_beta
    }

private val MarkSize = 88.dp

/** The name as the About page sets it. */
private val Name = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)

/** As settings groups are drawn. */
private val CardShape = SmoothCornerShape(Radius.md)

/** Room left under the page for the button and the fade over it. */
private val DockRoom = 132.dp
