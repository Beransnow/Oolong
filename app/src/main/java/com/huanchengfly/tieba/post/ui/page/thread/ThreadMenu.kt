package com.huanchengfly.tieba.post.ui.page.thread

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ChromeReaderMode
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.SwitchAccessShortcut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme

/**
 * The thread menu, as it opens out of the actions capsule: how to read the thread, the common
 * actions on it, then the rest. Its bottom row, with the like and the close button, belongs to
 * the capsule and is not part of it.
 */
@Composable
internal fun ColumnScope.ThreadMenu(
    seeLz: Boolean,
    stored: Boolean,
    immersive: Boolean,
    newestFirst: Boolean,
    canDelete: Boolean,
    currentPage: Int,
    totalPage: Int,
    onEvent: (ThreadEvent) -> Unit,
    onJumpToPage: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MenuToggle(
            text = stringResource(id = R.string.title_see_lz),
            icon = Icons.Rounded.Person,
            checked = seeLz,
            onClick = { onEvent(ThreadEvent.SetSeeLz(!seeLz)) },
        )
        MenuToggle(
            text = stringResource(id = R.string.title_sort),
            icon = Icons.AutoMirrored.Rounded.Sort,
            checked = newestFirst,
            onClick = { onEvent(ThreadEvent.ToggleSort) },
        )
        MenuToggle(
            text = stringResource(id = R.string.title_pure_read),
            icon = Icons.AutoMirrored.Rounded.ChromeReaderMode,
            checked = immersive,
            onClick = { onEvent(ThreadEvent.ToggleImmersive) },
        )
    }
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .fillMaxWidth(),
    ) {
        MenuAction(
            text = stringResource(id = if (stored) R.string.title_collected else R.string.title_collect),
            icon = if (stored) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            on = stored,
            onClick = { onEvent(if (stored) ThreadEvent.RemoveStore else ThreadEvent.StoreAtLastRead) },
        )
        MenuAction(stringResource(id = R.string.title_share), Icons.Rounded.Share) { onEvent(ThreadEvent.Share) }
        MenuAction(stringResource(id = R.string.title_copy_link), Icons.Rounded.Link) { onEvent(ThreadEvent.CopyLink) }
        MenuAction(stringResource(id = R.string.reply_official_client), Icons.AutoMirrored.Rounded.OpenInNew) {
            onEvent(ThreadEvent.OpenInOfficialClient)
        }
    }
    Divider(color = ExtendedTheme.colors.divider, modifier = Modifier.padding(horizontal = 16.dp))
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        if (!newestFirst) {
            MenuRow(
                text = stringResource(id = R.string.title_jump_page),
                icon = Icons.Rounded.SwitchAccessShortcut,
                trailing = stringResource(id = R.string.tip_jump_page, currentPage, totalPage),
                onClick = onJumpToPage,
            )
        }
        MenuRow(stringResource(id = R.string.title_report), Icons.Rounded.Flag) { onEvent(ThreadEvent.ReportThread) }
        if (canDelete) {
            MenuRow(
                text = stringResource(id = R.string.title_delete),
                icon = Icons.Rounded.Delete,
                danger = true,
                onClick = onDelete,
            )
        }
    }
}

/** A way of reading the thread, filled while it is on. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.MenuToggle(
    text: String,
    icon: ImageVector,
    checked: Boolean,
    onClick: () -> Unit,
) {
    val colors = ExtendedTheme.colors
    val background by animateColorAsState(if (checked) colors.text else colors.chip, label = "toggleBackground")
    val content by animateColorAsState(if (checked) colors.sheet else colors.textSecondary, label = "toggleContent")
    Column(
        modifier = Modifier
            .weight(1f)
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(role = Role.Switch, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = content)
        Text(text = text, fontSize = 12.5.sp, color = content, maxLines = 1)
    }
}

/** One of the common actions: a round icon over its name. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.MenuAction(
    text: String,
    icon: ImageVector,
    on: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = ExtendedTheme.colors
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (on) colors.text else colors.chip),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = if (on) colors.sheet else colors.text)
        }
        Text(text = text, fontSize = 12.sp, color = colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun MenuRow(
    text: String,
    icon: ImageVector,
    trailing: String? = null,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = ExtendedTheme.colors
    val color = if (danger) DangerColor else colors.text
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = if (danger) DangerColor else colors.textSecondary)
        Text(text = text, fontSize = 14.5.sp, color = color)
        Spacer(modifier = Modifier.weight(1f))
        if (trailing != null) Text(text = trailing, fontSize = 12.5.sp, color = colors.textSecondary)
    }
}

private val DangerColor = Color(0xFFD23B3B)
