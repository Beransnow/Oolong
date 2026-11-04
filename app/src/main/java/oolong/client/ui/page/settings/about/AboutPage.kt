package oolong.client.ui.page.settings.about

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import oolong.client.BuildConfig
import oolong.client.R
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Motion
import oolong.client.ui.common.theme.compose.PressFeedback
import oolong.client.ui.common.theme.compose.SmoothCornerShape
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.common.theme.compose.TextStyles
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.navigation.UpdateKey
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.widgets.compose.Aurora
import oolong.client.ui.widgets.compose.AuroraState
import oolong.client.ui.widgets.compose.BackNavigationIcon
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.condense
import oolong.client.ui.widgets.compose.floatingGlass
import oolong.client.utils.launchUrl

/**
 * About the app, on the landing page's light: the mark, whose lens can be lifted off it, the name
 * and slogan, and below them the ways on, on glass.
 */
@Composable
fun AboutPage(
    navigator: Navigator = LocalNavigator.current,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val available by viewModel.available.collectAsStateWithLifecycle()
    val aurora = remember { AuroraState() }
    LaunchedEffect(Unit) { aurora.drift() }
    // A short version of the landing page's entrance: this page is opened again and again
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, Motion.settle()) }

    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = {},
                navigationIcon = {
                    BackNavigationIcon(onBackPressed = { navigator.navigateUp() })
                }
            )
        }
    ) { contentPaddings ->
        val lens = remember { MarkLens() }
        val page = rememberHazeState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                // A finger draws the light a little towards it, without taking the touch
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.firstOrNull()?.let { aurora.touch(it.position) }
                        }
                    }
                },
        ) {
            Box(modifier = Modifier.fillMaxSize().hazeSource(page)) {
                Aurora(aurora, Modifier.fillMaxSize())
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPaddings)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    AboutMark(lens = lens, size = MarkSize)
                    Text(
                        text = stringResource(id = R.string.app_name),
                        modifier = Modifier
                            .padding(top = 20.dp)
                            .condense({ shown.value }, blur = 12.dp, rise = 6.dp, fadeFaster = 1.15f),
                        style = Name.copy(color = ExtendedTheme.colors.text),
                    )
                    Text(
                        text = stringResource(id = R.string.landing_slogan),
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .condense({ shown.value }, blur = 8.dp, rise = 4.dp),
                        style = TextStyles.body.copy(color = ExtendedTheme.colors.textSecondary),
                    )
                    Text(
                        text = stringResource(id = R.string.tip_about, BuildConfig.VERSION_NAME),
                        modifier = Modifier.padding(top = 8.dp),
                        style = TextStyles.caption.copy(color = ExtendedTheme.colors.textSecondary),
                    )
                    Spacer(modifier = Modifier.weight(1.2f))
                    val context = LocalContext.current
                    Links {
                        LinkRow(
                            text = stringResource(id = R.string.title_software_update),
                            trailing = available?.let { release -> { NewVersion(release.versionName) } },
                        ) { navigator.navigate(UpdateKey) }
                        LinkRow(text = stringResource(id = R.string.source_code)) {
                            launchUrl(context, navigator, "https://github.com/HuanCheng65/TiebaLite")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            LensOverlay(lens = lens, page = page, markSize = MarkSize)
        }
    }
}

/** The ways on from the page, on one piece of glass. */
@Composable
private fun Links(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .floatingGlass(LinksShape),
        content = content,
    )
}

@Composable
private fun LinkRow(text: String, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(interactionSource = interactionSource, indication = PressFeedback.Overlay, onClick = onClick)
            .padding(start = 20.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = text, style = TextStyles.bodyCompact.copy(color = ExtendedTheme.colors.text), modifier = Modifier.weight(1f))
        trailing?.invoke()
        Icon(
            imageVector = Symbols.ChevronRight,
            contentDescription = null,
            tint = ExtendedTheme.colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** A newer version on the software update row: its name and a dot. */
@Composable
private fun NewVersion(name: String) {
    Text(text = name, style = TextStyles.caption.copy(color = ExtendedTheme.colors.textSecondary))
    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(NewDot))
}

private val NewDot = Color(0xFFE5484D)

private val MarkSize = 100.dp

private val LinksShape = SmoothCornerShape(26.dp)

/** The name as the landing page sets it, smaller. */
private val Name = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
