package com.huanchengfly.tieba.post.ui.page.settings.block

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.model.BlockRule
import com.huanchengfly.tieba.post.model.BlockRule.Match
import com.huanchengfly.tieba.post.ui.common.theme.compose.Corners
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.Spacing
import com.huanchengfly.tieba.post.ui.common.theme.compose.TextStyles
import com.huanchengfly.tieba.post.ui.navigation.BlockRuleKey
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.Button
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBar
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBarTitle
import com.huanchengfly.tieba.post.ui.widgets.compose.Switch

@Composable
fun BlockRulePage(
    key: BlockRuleKey,
    navigator: Navigator = LocalNavigator.current,
    viewModel: BlockRuleViewModel = hiltViewModel<BlockRuleViewModel, BlockRuleViewModel.Factory> { it.create(key) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.closed.collect { navigator.navigateUp() } }
    BlockRuleScreen(
        state = state,
        onBack = navigator::navigateUp,
        onEdit = viewModel::edit,
        onKind = viewModel::setKind,
        onExpiry = viewModel::setExpiry,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
    )
}

/**
 * A rule: what it blocks, then the rule as one sentence whose underlined parts open their choices,
 * the conditions that narrow it, and whether it is on.
 */
@Composable
fun BlockRuleScreen(
    state: BlockRuleState,
    onBack: () -> Unit,
    onEdit: ((BlockRule) -> BlockRule) -> Unit,
    onKind: (Match) -> Unit,
    onExpiry: (Int?) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = ExtendedTheme.colors
    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = if (state.isNew) R.string.title_new_rule else R.string.title_edit_rule)) },
                navigationIcon = { BackNavigationIcon(onBackPressed = onBack) },
                actions = {
                    Button(onClick = onSave, enabled = state.canSave, modifier = Modifier.padding(end = Spacing.xs)) {
                        Text(text = stringResource(id = R.string.button_save), style = TextStyles.label)
                    }
                },
            )
        },
    ) { padding ->
        val draft = state.draft ?: return@GlassScaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(bottom = Spacing.xl),
        ) {
            if (state.isNew) KindSelector(draft.match, onKind)
            Label(stringResource(id = if (draft.match is Match.Level) R.string.rule_kind_level else R.string.rule_field_what))
            MatchField(draft.match, onChange = { match -> onEdit { it.copy(match = match) } })
            Label(stringResource(id = R.string.rule_field_rule))
            SentenceCard(draft, state.now, onEdit, onExpiry)
            Conditions(draft, onEdit)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = Spacing.md)
                    .padding(top = Spacing.xl)
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(Corners.md)
                    .background(colors.chip)
                    .clickable { onEdit { it.copy(enabled = !it.enabled) } }
                    .padding(horizontal = Spacing.md),
            ) {
                Text(text = stringResource(id = R.string.rule_enabled), style = TextStyles.bodyCompact, color = colors.text, modifier = Modifier.weight(1f))
                Switch(checked = draft.enabled, onCheckedChange = { checked -> onEdit { it.copy(enabled = checked) } })
            }
            if (!state.isNew) {
                Text(
                    text = stringResource(id = R.string.rule_delete),
                    style = TextStyles.bodyCompact,
                    color = MaterialTheme.colors.error,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = Spacing.lg)
                        .clip(Corners.full)
                        .clickable(onClick = onDelete)
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = TextStyles.caption,
        color = ExtendedTheme.colors.textSecondary,
        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = Spacing.md, bottom = 6.dp),
    )
}

@Composable
private fun KindSelector(match: Match, onKind: (Match) -> Unit) {
    val colors = ExtendedTheme.colors
    val kinds = listOf(
        R.string.rule_kind_keyword to Match.Keyword(emptyList()),
        R.string.rule_kind_user to Match.User(0, ""),
        R.string.rule_kind_forum to Match.Forum(""),
        R.string.rule_kind_level to Match.Level(3),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .padding(horizontal = Spacing.md)
            .padding(top = Spacing.sm)
            .fillMaxWidth()
            .clip(Corners.sm)
            .background(colors.inset)
            .padding(3.dp),
    ) {
        kinds.forEach { (label, kind) ->
            val selected = kind::class == match::class
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(Corners.xs)
                    .background(if (selected) colors.card else Color.Transparent)
                    .clickable { if (!selected) onKind(kind) }
                    .padding(vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(id = label),
                    style = TextStyles.caption,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) colors.text else colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun MatchField(match: Match, onChange: (Match) -> Unit) {
    val colors = ExtendedTheme.colors
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = Spacing.md)
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .clip(Corners.md)
                .background(colors.chip)
                .padding(start = 14.dp, end = Spacing.xs),
        ) {
            when (match) {
                is Match.Level -> {
                    Text(text = stringResource(id = R.string.settings_block_low_level_below), style = TextStyles.bodyCompact, color = colors.text, modifier = Modifier.weight(1f))
                    Stepper(match.level, range = 2..18) { onChange(Match.Level(it)) }
                }

                is Match.Keyword -> {
                    // The words as typed, so spaces stay while typing
                    var text by remember(match.regex) { mutableStateOf(match.words.joinToString(" ")) }
                    TextInput(
                        value = text,
                        hint = stringResource(id = if (match.regex) R.string.rule_hint_regex else R.string.rule_hint_keyword),
                        monospace = match.regex,
                        modifier = Modifier.weight(1f),
                    ) {
                        text = it
                        onChange(match.copy(words = if (match.regex) listOf(it) else it.split(' ', '　')))
                    }
                    val regexText = text
                    Text(
                        text = stringResource(id = R.string.rule_regex),
                        style = TextStyles.micro.copy(fontSize = 12.sp),
                        color = if (match.regex) colors.onEmphasis else colors.textSecondary,
                        modifier = Modifier
                            .clip(Corners.full)
                            .background(if (match.regex) colors.emphasis else colors.inset)
                            .clickable {
                                onChange(
                                    if (match.regex) {
                                        Match.Keyword(regexText.split(' ', '　'), regex = false)
                                    } else {
                                        Match.Keyword(listOf(regexText), regex = true)
                                    },
                                )
                            }
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }

                is Match.User -> TextInput(
                    value = match.name,
                    hint = stringResource(id = R.string.rule_hint_user),
                    modifier = Modifier.weight(1f),
                ) { onChange(Match.User(uid = 0, name = it)) }

                is Match.Forum -> TextInput(
                    value = match.name,
                    hint = stringResource(id = R.string.rule_hint_forum),
                    modifier = Modifier.weight(1f),
                ) { onChange(Match.Forum(it)) }
            }
        }
        if (match is Match.Keyword && match.regex && match.words.firstOrNull().orEmpty().isNotEmpty() &&
            runCatching { Regex(match.words.first()) }.isFailure
        ) {
            Text(
                text = stringResource(id = R.string.rule_regex_invalid),
                style = TextStyles.footnote,
                color = MaterialTheme.colors.error,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun TextInput(
    value: String,
    hint: String,
    modifier: Modifier = Modifier,
    monospace: Boolean = false,
    onChange: (String) -> Unit,
) {
    val colors = ExtendedTheme.colors
    val style = TextStyles.body.copy(color = colors.text, fontFamily = if (monospace) FontFamily.Monospace else null)
    Box(modifier = modifier.padding(vertical = 12.dp)) {
        if (value.isEmpty()) Text(text = hint, style = style.copy(color = colors.textSecondary))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Stepper(value: Int, range: IntRange, onChange: (Int) -> Unit) {
    val colors = ExtendedTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(Corners.full).background(colors.inset),
    ) {
        Text(
            text = "−",
            color = colors.textSecondary,
            modifier = Modifier.clickable(enabled = value > range.first) { onChange(value - 1) }.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        Text(text = stringResource(id = R.string.rule_level, value), style = TextStyles.label, color = colors.text)
        Text(
            text = "+",
            color = colors.textSecondary,
            modifier = Modifier.clickable(enabled = value < range.last) { onChange(value + 1) }.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/** The rule as one sentence; its parts other than what it matches open their choices. */
@Composable
private fun SentenceCard(rule: BlockRule, now: Long, onEdit: ((BlockRule) -> BlockRule) -> Unit, onExpiry: (Int?) -> Unit) {
    val colors = ExtendedTheme.colors
    var open by remember { mutableStateOf<RulePart?>(null) }
    val runs = ruleSentence(rule, now, full = true)
    val text = buildAnnotatedString {
        runs.forEach { run ->
            val part = run.part
            when {
                part == null -> append(run.text)
                part == RulePart.Value -> withStyleBold { append(run.text) }
                else -> {
                    val style = SpanStyle(
                        color = if (run.isDefault) colors.textSecondary else colors.accent,
                        fontWeight = if (run.isDefault) FontWeight.Normal else FontWeight.SemiBold,
                        textDecoration = TextDecoration.Underline,
                    )
                    withLink(LinkAnnotation.Clickable(part.name, TextLinkStyles(style)) { open = part }) { append(run.text) }
                }
            }
        }
    }
    Box(modifier = Modifier.padding(horizontal = Spacing.md)) {
        Text(
            text = text,
            style = TextStyles.body.copy(fontSize = 17.sp, lineHeight = 32.sp),
            color = colors.text,
            modifier = Modifier
                .fillMaxWidth()
                .clip(Corners.md)
                .background(colors.chip)
                .padding(horizontal = Spacing.md, vertical = 14.dp),
        )
        DropdownMenu(expanded = open != null, onDismissRequest = { open = null }) {
            when (open) {
                RulePart.Scope -> BlockRule.Scope.entries.forEach { scope ->
                    Choice(scopeName(scope), selected = rule.scope == scope) {
                        onEdit { it.copy(scope = scope) }
                        open = null
                    }
                }

                RulePart.Action -> BlockRule.Action.entries.forEach { action ->
                    val label = if (action == BlockRule.Action.Default) {
                        stringResource(id = R.string.rule_action_default_hint)
                    } else {
                        actionName(action)
                    }
                    Choice(label, selected = rule.action == action) {
                        onEdit { it.copy(action = action) }
                        open = null
                    }
                }

                RulePart.Expiry -> listOf(null to R.string.rule_forever, 7 to R.string.rule_expiry_7, 30 to R.string.rule_expiry_30)
                    .forEach { (days, label) ->
                        Choice(stringResource(id = label), selected = (rule.expiresAt == null) == (days == null) && days == null) {
                            onExpiry(days)
                            open = null
                        }
                    }

                else -> Unit
            }
        }
    }
}

private inline fun androidx.compose.ui.text.AnnotatedString.Builder.withStyleBold(block: androidx.compose.ui.text.AnnotatedString.Builder.() -> Unit) {
    pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
    block()
    pop()
}

@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(onClick = onClick) {
        Text(text = text, modifier = Modifier.widthIn(min = 120.dp).weight(1f, fill = false))
        Spacer(modifier = Modifier.width(Spacing.md))
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = ExtendedTheme.colors.accent, modifier = Modifier.size(18.dp))
    }
}

/** The "and" conditions, each a chip that can be taken off, and adding another. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Conditions(rule: BlockRule, onEdit: ((BlockRule) -> BlockRule) -> Unit) {
    val colors = ExtendedTheme.colors
    var adding by remember { mutableStateOf(false) }
    // A condition being typed, kept even while still empty
    var forumShown by remember { mutableStateOf(rule.inForum != null) }
    val canTitle = rule.match is Match.Keyword && !rule.titleOnly
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.sm),
    ) {
        if (forumShown || rule.inForum != null) {
            Chip(onRemove = {
                forumShown = false
                onEdit { it.copy(inForum = null) }
            }) {
                Text(text = stringResource(id = R.string.rule_condition_in_forum), style = TextStyles.caption, color = colors.text)
                Spacer(modifier = Modifier.width(6.dp))
                Box(modifier = Modifier.width(88.dp).clip(Corners.xs).background(colors.card).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    BasicTextField(
                        value = rule.inForum.orEmpty(),
                        onValueChange = { name -> onEdit { it.copy(inForum = name) } },
                        singleLine = true,
                        textStyle = TextStyles.caption.copy(color = colors.text),
                        cursorBrush = SolidColor(colors.accent),
                    )
                }
            }
        }
        if (rule.titleOnly) {
            Chip(onRemove = { onEdit { it.copy(titleOnly = false) } }) {
                Text(text = stringResource(id = R.string.rule_condition_title_only), style = TextStyles.caption, color = colors.text)
            }
        }
        val canForum = !forumShown && rule.inForum == null && rule.match !is Match.Forum
        if (canForum || canTitle) {
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(Corners.full)
                        .border(1.dp, colors.textSecondary.copy(alpha = 0.4f), Corners.full)
                        .clickable { adding = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(id = R.string.rule_add_condition), style = TextStyles.caption, color = colors.textSecondary)
                }
                DropdownMenu(expanded = adding, onDismissRequest = { adding = false }) {
                    if (canForum) {
                        DropdownMenuItem(onClick = {
                            forumShown = true
                            adding = false
                        }) { Text(text = stringResource(id = R.string.rule_condition_in_forum)) }
                    }
                    if (canTitle) {
                        DropdownMenuItem(onClick = {
                            onEdit { it.copy(titleOnly = true) }
                            adding = false
                        }) { Text(text = stringResource(id = R.string.rule_condition_title_only)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(onRemove: () -> Unit, content: @Composable () -> Unit) {
    val colors = ExtendedTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(Corners.full)
            .background(colors.chip)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        content()
        Icon(
            Icons.Rounded.Close,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.padding(start = 4.dp).size(22.dp).clip(Corners.full).clickable(onClick = onRemove).padding(4.dp),
        )
    }
}
