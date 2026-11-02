package com.huanchengfly.tieba.post.ui.widgets.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.model.BlockRule
import com.huanchengfly.tieba.post.repository.BlockVerdict
import com.huanchengfly.tieba.post.ui.common.theme.compose.Corners
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.TextStyles

/**
 * One line in place of blocked content: [label], then why, and a way to show it.
 *
 * @param boxed drawn on a tinted block; plain text where it sits among replies
 */
@Composable
fun BlockTip(
    reason: String,
    onShow: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(id = R.string.block_tip_label),
    boxed: Boolean = true,
) {
    val colors = ExtendedTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(Corners.sm)
            .then(if (boxed) Modifier.background(colors.textSecondary.copy(alpha = 0.08f)) else Modifier)
            .clickable(onClick = onShow)
            .padding(vertical = 8.dp, horizontal = if (boxed) 12.dp else 0.dp),
    ) {
        Text(
            text = stringResource(id = R.string.block_tip, label, reason),
            style = TextStyles.caption,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = stringResource(id = R.string.block_tip_show), style = TextStyles.caption, color = colors.accent)
    }
}

/** Why [rule] blocks something, as a tip says it. */
@Composable
fun blockReason(rule: BlockRule): String = when (val match = rule.match) {
    is BlockRule.Match.User -> stringResource(id = R.string.block_reason_user, match.shownName)
    is BlockRule.Match.Forum -> stringResource(id = R.string.block_reason_forum, match.name.removeSuffix("吧"))
    is BlockRule.Match.Level -> stringResource(id = R.string.block_reason_level, match.level)
    is BlockRule.Match.Keyword -> stringResource(
        id = if (match.regex) R.string.block_reason_regex else R.string.block_reason_keyword,
        match.words.joinToString(" "),
    )
}

/**
 * [content] as the block rules have it: as it is, or folded to a [tip] that shows it on a tap,
 * or left out when [block] hides it or [hideAll].
 */
@Composable
fun BlockableContent(
    block: BlockVerdict?,
    modifier: Modifier = Modifier,
    hideAll: Boolean = false,
    tip: @Composable (reason: String, onShow: () -> Unit) -> Unit = { reason, onShow -> BlockTip(reason, onShow) },
    content: @Composable () -> Unit,
) {
    var shown by rememberSaveable(block?.rule?.id) { mutableStateOf(false) }
    when {
        block == null || shown -> content()
        block.hidden || hideAll -> Unit
        else -> Box(modifier = modifier) { tip(blockReason(block.rule)) { shown = true } }
    }
}
