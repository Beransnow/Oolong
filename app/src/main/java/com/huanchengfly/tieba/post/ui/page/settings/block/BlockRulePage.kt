package com.huanchengfly.tieba.post.ui.page.settings.block

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
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
import com.huanchengfly.tieba.post.ui.widgets.compose.ConfirmDialog
import com.huanchengfly.tieba.post.ui.widgets.compose.ContextMenuItem
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBar
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBarTitle
import com.huanchengfly.tieba.post.ui.widgets.compose.LocalContextMenus
import com.huanchengfly.tieba.post.ui.widgets.compose.SegmentedControl
import com.huanchengfly.tieba.post.ui.widgets.compose.Switch
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberDialogState
import kotlinx.collections.immutable.persistentListOf

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
 * A rule: what it blocks, then the rule as one sentence whose underlined parts open their choices
 * in a menu, the conditions that narrow it, and whether it is on.
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
    val deleteDialog = rememberDialogState()
    ConfirmDialog(
        dialogState = deleteDialog,
        onConfirm = onDelete,
        confirmText = stringResource(id = R.string.title_delete),
        title = { Text(text = stringResource(id = R.string.title_delete_rule)) },
    )
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
                .padding(bottom = Spacing.xxl),
        ) {
            if (state.isNew) KindSelector(draft.match, onKind)
            GroupTitle(stringResource(id = if (draft.match is Match.Level) R.string.rule_kind_level else R.string.rule_field_what))
            MatchField(draft.match, onChange = { match -> onEdit { it.copy(match = match) } })
            GroupTitle(stringResource(id = R.string.rule_field_rule))
            SentenceCard(draft, state.now, onEdit, onExpiry)
            Conditions(draft, onEdit)
            Spacer(modifier = Modifier.height(Spacing.xl))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = Spacing.md)
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
                // A destructive action at the end of its block, red, asking first
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = Spacing.md)
                        .padding(top = Spacing.md)
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(Corners.md)
                        .background(colors.chip)
                        .clickable { deleteDialog.show() }
                        .padding(horizontal = Spacing.md),
                ) {
                    Text(text = stringResource(id = R.string.rule_delete), style = TextStyles.bodyCompact, color = MaterialTheme.colors.error)
                }
            }
        }
    }
}

/** A group's small title, as the settings pages have them. */
@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text,
        style = TextStyles.caption,
        color = ExtendedTheme.colors.textSecondary,
        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = Spacing.lg, bottom = Spacing.xs),
    )
}

@Composable
private fun KindSelector(match: Match, onKind: (Match) -> Unit) {
    val kinds = listOf(
        R.string.rule_kind_keyword to Match.Keyword(emptyList()),
        R.string.rule_kind_user to Match.User(0, ""),
        R.string.rule_kind_forum to Match.Forum(""),
        R.string.rule_kind_level to Match.Level(3),
    )
    val labels = kinds.map { stringResource(id = it.first) }
    SegmentedControl(
        options = persistentListOf(*labels.toTypedArray()),
        selected = kinds.indexOfFirst { it.second::class == match::class },
        onSelect = { onKind(kinds[it].second) },
        modifier = Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.sm).fillMaxWidth(),
    )
}

@Composable
private fun MatchField(match: Match, onChange: (Match) -> Unit) {
    val colors = ExtendedTheme.colors
    val menus = LocalContextMenus.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val levelLabels = LevelChoices.associateWith { stringResource(id = R.string.rule_level, it) }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = Spacing.md)
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(Corners.md)
                .background(colors.chip)
                .onGloballyPositioned { bounds = it.boundsInWindow() }
                .then(
                    if (match is Match.Level && menus != null) {
                        Modifier.clickable {
                            menus.show(
                                items = levelLabels.map { (level, label) ->
                                    ContextMenuItem(if (level == match.level) Icons.Rounded.Check else Icons.Rounded.BarChart, label) {
                                        onChange(Match.Level(level))
                                    }
                                },
                                anchor = bounds,
                            )
                        }
                    } else {
                        Modifier
                    },
                )
                .padding(start = Spacing.md, end = Spacing.xs),
        ) {
            when (match) {
                // The settings rows' way of choosing a value: the value, and ⇕ for its menu
                is Match.Level -> {
                    Text(text = stringResource(id = R.string.settings_block_low_level_below), style = TextStyles.bodyCompact, color = colors.text, modifier = Modifier.weight(1f))
                    Text(text = stringResource(id = R.string.rule_level, match.level), style = TextStyles.bodyCompact, color = colors.textSecondary)
                    Icon(Icons.Rounded.UnfoldMore, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.padding(start = 2.dp, end = Spacing.xs).size(18.dp))
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
                    val current = text
                    // A tag: the inverted colours when on
                    Text(
                        text = stringResource(id = R.string.rule_regex),
                        style = TextStyles.footnote,
                        fontWeight = FontWeight.Medium,
                        color = if (match.regex) colors.onInvertChip else colors.onChip,
                        modifier = Modifier
                            .clip(Corners.full)
                            .background(if (match.regex) colors.invertChip else colors.inset)
                            .clickable {
                                onChange(
                                    if (match.regex) {
                                        Match.Keyword(current.split(' ', '　'), regex = false)
                                    } else {
                                        Match.Keyword(listOf(current), regex = true)
                                    },
                                )
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
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

private val LevelChoices = (2..10).toList()

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
    Box(modifier = modifier.padding(vertical = 13.dp)) {
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

/** The rule as one sentence; its scope, action and expiry open their choices where they are. */
@Composable
private fun SentenceCard(rule: BlockRule, now: Long, onEdit: ((BlockRule) -> BlockRule) -> Unit, onExpiry: (Int?) -> Unit) {
    val colors = ExtendedTheme.colors
    val menus = LocalContextMenus.current
    val runs = ruleSentence(rule, now, full = true)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var origin by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val scopes = BlockRule.Scope.entries.associateWith { scopeName(it) }
    val actions = BlockRule.Action.entries.associateWith {
        if (it == BlockRule.Action.Default) stringResource(id = R.string.rule_action_default_hint) else actionName(it)
    }
    val expiries = listOf(
        null to stringResource(id = R.string.rule_forever),
        7 to stringResource(id = R.string.rule_expiry_7),
        30 to stringResource(id = R.string.rule_expiry_30),
    )

    fun open(part: RulePart, start: Int, end: Int) {
        menus ?: return
        // The menu grows out of the words tapped
        val box = layout?.getPathForRange(start, end)?.getBounds() ?: Rect.Zero
        val at = origin?.positionInWindow() ?: Offset.Zero
        val anchor = box.translate(at)
        val items = when (part) {
            RulePart.Scope -> scopes.map { (scope, label) ->
                ContextMenuItem(if (rule.scope == scope) Icons.Rounded.Check else scope.icon, label) { onEdit { it.copy(scope = scope) } }
            }

            RulePart.Action -> actions.map { (action, label) ->
                ContextMenuItem(if (rule.action == action) Icons.Rounded.Check else action.icon, label) { onEdit { it.copy(action = action) } }
            }

            RulePart.Expiry -> expiries.map { (days, label) ->
                val chosen = days == null && rule.expiresAt == null
                ContextMenuItem(if (chosen) Icons.Rounded.Check else expiryIcon(days), label) { onExpiry(days) }
            }

            RulePart.Value -> return
        }
        menus.show(items = items, anchor = anchor)
    }

    val text = buildAnnotatedString {
        runs.forEach { run ->
            val part = run.part
            when {
                part == null -> append(run.text)
                part == RulePart.Value -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(run.text) }
                else -> {
                    val style = SpanStyle(
                        color = if (run.isDefault) colors.textSecondary else colors.accent,
                        fontWeight = if (run.isDefault) FontWeight.Normal else FontWeight.SemiBold,
                        textDecoration = TextDecoration.Underline,
                    )
                    val start = length
                    val end = start + run.text.length
                    withLink(LinkAnnotation.Clickable(part.name, TextLinkStyles(style)) { open(part, start, end) }) { append(run.text) }
                }
            }
        }
    }
    Box(
        modifier = Modifier
            .padding(horizontal = Spacing.md)
            .fillMaxWidth()
            .clip(Corners.md)
            .background(colors.chip)
            .padding(horizontal = Spacing.md, vertical = 14.dp),
    ) {
        Text(
            text = text,
            style = TextStyles.body.copy(fontSize = 17.sp, lineHeight = 32.sp),
            color = colors.text,
            onTextLayout = { layout = it },
            modifier = Modifier.onGloballyPositioned { origin = it },
        )
    }
}

private val BlockRule.Scope.icon: ImageVector
    get() = when (this) {
        BlockRule.Scope.All -> Icons.Rounded.Public
        BlockRule.Scope.Feeds -> Icons.Rounded.Explore
        BlockRule.Scope.Forum -> Icons.Rounded.Forum
        BlockRule.Scope.Thread -> Icons.Rounded.Article
    }

private val BlockRule.Action.icon: ImageVector
    get() = when (this) {
        BlockRule.Action.Default -> Icons.Rounded.Tune
        BlockRule.Action.Fold -> Icons.Rounded.UnfoldLess
        BlockRule.Action.Hide -> Icons.Rounded.VisibilityOff
        BlockRule.Action.Allow -> Icons.Rounded.Visibility
    }

private fun expiryIcon(days: Int?): ImageVector = when (days) {
    null -> Icons.Rounded.AllInclusive
    7 -> Icons.Rounded.Event
    else -> Icons.Rounded.CalendarMonth
}

/** The "and" conditions, each a capsule that can be taken off, and adding another from a menu. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Conditions(rule: BlockRule, onEdit: ((BlockRule) -> BlockRule) -> Unit) {
    val colors = ExtendedTheme.colors
    val menus = LocalContextMenus.current
    // A condition being typed, kept even while still empty
    var forumShown by remember { mutableStateOf(rule.inForum != null) }
    var addBounds by remember { mutableStateOf(Rect.Zero) }
    val canForum = !forumShown && rule.inForum == null && rule.match !is Match.Forum
    val canTitle = rule.match is Match.Keyword && !rule.titleOnly
    val inForumLabel = stringResource(id = R.string.rule_condition_in_forum)
    val titleOnlyLabel = stringResource(id = R.string.rule_condition_title_only)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.sm),
    ) {
        if (forumShown || rule.inForum != null) {
            Condition(onRemove = {
                forumShown = false
                onEdit { it.copy(inForum = null) }
            }) {
                Text(text = inForumLabel, style = TextStyles.label, color = colors.text)
                Spacer(modifier = Modifier.width(Spacing.xs))
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .clip(Corners.xs)
                        .background(colors.inset)
                        .padding(horizontal = Spacing.xs, vertical = 3.dp),
                ) {
                    if (rule.inForum.isNullOrEmpty()) {
                        Text(text = stringResource(id = R.string.rule_condition_forum_hint), style = TextStyles.label, color = colors.textSecondary)
                    }
                    BasicTextField(
                        value = rule.inForum.orEmpty(),
                        onValueChange = { name -> onEdit { it.copy(inForum = name) } },
                        singleLine = true,
                        textStyle = TextStyles.label.copy(color = colors.text),
                        cursorBrush = SolidColor(colors.accent),
                    )
                }
            }
        }
        if (rule.titleOnly) {
            Condition(onRemove = { onEdit { it.copy(titleOnly = false) } }) {
                Text(text = titleOnlyLabel, style = TextStyles.label, color = colors.text)
            }
        }
        if ((canForum || canTitle) && menus != null) {
            // A secondary button: it opens the conditions that can still be added
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .clip(Corners.full)
                    .background(colors.chip)
                    .onGloballyPositioned { addBounds = it.boundsInWindow() }
                    .clickable {
                        menus.show(
                            items = listOfNotNull(
                                ContextMenuItem(Icons.Rounded.Forum, inForumLabel) { forumShown = true }.takeIf { canForum },
                                ContextMenuItem(Icons.Rounded.Title, titleOnlyLabel) { onEdit { it.copy(titleOnly = true) } }.takeIf { canTitle },
                            ),
                            anchor = addBounds,
                            icon = Icons.Rounded.Add,
                        )
                    }
                    .padding(start = Spacing.sm, end = Spacing.md),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.text, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(Spacing.xxs))
                Text(text = stringResource(id = R.string.rule_add_condition), style = TextStyles.label, color = colors.text)
            }
        }
    }
}

@Composable
private fun Condition(onRemove: () -> Unit, content: @Composable () -> Unit) {
    val colors = ExtendedTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(Corners.full)
            .background(colors.chip)
            .padding(start = Spacing.md, end = Spacing.xxs),
    ) {
        content()
        Icon(
            Icons.Rounded.Close,
            contentDescription = stringResource(id = R.string.rule_condition_remove),
            tint = colors.textSecondary,
            modifier = Modifier
                .padding(start = Spacing.xxs)
                .size(28.dp)
                .clip(Corners.full)
                .clickable(onClick = onRemove)
                .padding(6.dp),
        )
    }
}
