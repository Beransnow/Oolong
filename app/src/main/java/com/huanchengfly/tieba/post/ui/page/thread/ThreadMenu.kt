package com.huanchengfly.tieba.post.ui.page.thread

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ChromeReaderMode
import androidx.compose.material.icons.automirrored.rounded.ChromeReaderMode
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Face6
import androidx.compose.material.icons.rounded.FaceRetouchingOff
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.invertChipBackground
import com.huanchengfly.tieba.post.ui.common.theme.compose.invertChipContent
import com.huanchengfly.tieba.post.ui.widgets.compose.ListMenuItem
import com.huanchengfly.tieba.post.ui.widgets.compose.TextWithMinWidth
import com.huanchengfly.tieba.post.ui.widgets.compose.VerticalGrid

/** The thread menu: four toggles, then actions on the whole thread. */
@Composable
internal fun ThreadMenu(
    seeLz: Boolean,
    stored: Boolean,
    immersive: Boolean,
    newestFirst: Boolean,
    canDelete: Boolean,
    onEvent: (ThreadEvent) -> Unit,
    onJumpToPage: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .height(4.dp)
                .fillMaxWidth(0.25f)
                .clip(RoundedCornerShape(100))
                .background(ExtendedTheme.colors.chip)
        )
        VerticalGrid(
            column = 2,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            rowModifier = Modifier.height(IntrinsicSize.Min),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            item {
                MenuToggle(
                    text = stringResource(id = R.string.title_see_lz),
                    icon = if (seeLz) Icons.Rounded.Face6 else Icons.Rounded.FaceRetouchingOff,
                    checked = seeLz,
                    onClick = { onEvent(ThreadEvent.SetSeeLz(!seeLz)) },
                )
            }
            item {
                MenuToggle(
                    text = stringResource(id = if (stored) R.string.title_collected else R.string.title_uncollected),
                    icon = if (stored) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    checked = stored,
                    onClick = { onEvent(if (stored) ThreadEvent.RemoveStore else ThreadEvent.StoreAtLastRead) },
                )
            }
            item {
                MenuToggle(
                    text = stringResource(id = R.string.title_pure_read),
                    icon = if (immersive) Icons.AutoMirrored.Rounded.ChromeReaderMode else Icons.AutoMirrored.Outlined.ChromeReaderMode,
                    checked = immersive,
                    onClick = { onEvent(ThreadEvent.ToggleImmersive) },
                )
            }
            item {
                MenuToggle(
                    text = stringResource(id = R.string.title_sort),
                    icon = Icons.AutoMirrored.Rounded.Sort,
                    checked = newestFirst,
                    onClick = { onEvent(ThreadEvent.ToggleSort) },
                )
            }
        }
        Column {
            if (!newestFirst) {
                MenuItem(Icons.Rounded.RocketLaunch, stringResource(id = R.string.title_jump_page), onJumpToPage)
            }
            MenuItem(Icons.Rounded.Share, stringResource(id = R.string.title_share)) { onEvent(ThreadEvent.Share) }
            MenuItem(Icons.Rounded.ContentCopy, stringResource(id = R.string.title_copy_link)) { onEvent(ThreadEvent.CopyLink) }
            MenuItem(Icons.Rounded.Report, stringResource(id = R.string.title_report)) { onEvent(ThreadEvent.ReportThread) }
            if (canDelete) {
                MenuItem(Icons.Rounded.Delete, stringResource(id = R.string.title_delete), onDelete)
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    ListMenuItem(
        icon = icon,
        text = text,
        iconColor = ExtendedTheme.colors.text,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun MenuToggle(
    text: String,
    icon: ImageVector,
    checked: Boolean,
    onClick: () -> Unit,
    backgroundColor: Color = ExtendedTheme.colors.chip,
    contentColor: Color = ExtendedTheme.colors.text,
    selectedBackgroundColor: Color = ExtendedTheme.colors.invertChipBackground,
    selectedContentColor: Color = ExtendedTheme.colors.invertChipContent,
) {
    val animatedColor by animateColorAsState(
        if (checked) selectedContentColor else contentColor,
        label = "toggleColor"
    )
    val animatedBackgroundColor by animateColorAsState(
        if (checked) selectedBackgroundColor else backgroundColor,
        label = "toggleBackgroundColor"
    )
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(6.dp),
        color = animatedBackgroundColor,
        contentColor = animatedColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(imageVector = icon, contentDescription = null)
                ProvideTextStyle(
                    value = MaterialTheme.typography.subtitle1.copy(fontSize = 14.sp, textAlign = TextAlign.Center)
                ) {
                    TextWithMinWidth(text = text, minLength = 4)
                }
            }
        }
    }
}
