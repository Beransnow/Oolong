package com.huanchengfly.tieba.post.ui.page.settings.block

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.model.BlockRule

/** A part of a rule's sentence that the editor lets the user change. */
enum class RulePart { Scope, Action, Value, Expiry }

/** A run of a rule's sentence: plain words, or a [part] of the rule, marked when left at its [isDefault]. */
data class SentenceRun(val text: String, val part: RulePart? = null, val isDefault: Boolean = false)

/**
 * [rule] as one sentence, such as "在 推荐和热榜 屏蔽 「抽卡吧」 的帖子 · 7 天后失效". With [full], parts
 * left at their defaults are spelled out too, for the editor; otherwise they are left out.
 */
@Composable
fun ruleSentence(rule: BlockRule, now: Long, full: Boolean = false): List<SentenceRun> = buildList {
    val scopeDefault = rule.scope == BlockRule.Scope.All
    if (full || !scopeDefault) {
        add(SentenceRun(stringResource(R.string.rule_in)))
        add(SentenceRun(scopeName(rule.scope), RulePart.Scope, scopeDefault))
        add(SentenceRun(" "))
    }
    add(SentenceRun(actionName(rule.action), RulePart.Action, rule.action == BlockRule.Action.Default))
    add(SentenceRun(" "))
    rule.inForum?.takeIf { it.isNotBlank() }?.let { add(SentenceRun(stringResource(R.string.rule_in_forum, it.removeSuffix("吧")))) }
    when (val match = rule.match) {
        is BlockRule.Match.User -> {
            add(SentenceRun(stringResource(R.string.rule_user)))
            add(SentenceRun(match.shownName, RulePart.Value))
            add(SentenceRun(stringResource(R.string.rule_whose_content)))
        }

        is BlockRule.Match.Forum -> {
            add(SentenceRun(stringResource(R.string.rule_forum, match.name.removeSuffix("吧")), RulePart.Value))
            add(SentenceRun(stringResource(R.string.rule_whose_threads)))
        }

        is BlockRule.Match.Level -> {
            add(SentenceRun(stringResource(R.string.rule_level_below)))
            add(SentenceRun(stringResource(R.string.rule_level, match.level), RulePart.Value))
            add(SentenceRun(stringResource(R.string.rule_whose_users)))
        }

        is BlockRule.Match.Keyword -> {
            if (rule.titleOnly) add(SentenceRun(stringResource(R.string.rule_title)))
            add(SentenceRun(stringResource(if (match.regex) R.string.rule_matching else R.string.rule_containing)))
            val words = match.words.joinToString(" ")
            add(SentenceRun(if (match.regex) "/$words/" else "「$words」", RulePart.Value))
            add(SentenceRun(stringResource(R.string.rule_whose_content)))
        }
    }
    val expiresAt = rule.expiresAt
    if (full || expiresAt != null) {
        add(SentenceRun(" · "))
        val text = when {
            expiresAt == null -> stringResource(R.string.rule_forever)
            expiresAt <= now -> stringResource(R.string.rule_expired)
            else -> stringResource(R.string.rule_expires_in, ((expiresAt - now + DayMillis - 1) / DayMillis).toInt())
        }
        add(SentenceRun(text, RulePart.Expiry, expiresAt == null))
    }
}

@Composable
fun scopeName(scope: BlockRule.Scope): String = stringResource(
    when (scope) {
        BlockRule.Scope.All -> R.string.rule_scope_all
        BlockRule.Scope.Feeds -> R.string.rule_scope_feeds
        BlockRule.Scope.Forum -> R.string.rule_scope_forum
        BlockRule.Scope.Thread -> R.string.rule_scope_thread
    },
)

@Composable
fun actionName(action: BlockRule.Action): String = stringResource(
    when (action) {
        BlockRule.Action.Default -> R.string.rule_action_default
        BlockRule.Action.Fold -> R.string.rule_action_fold
        BlockRule.Action.Hide -> R.string.rule_action_hide
        BlockRule.Action.Allow -> R.string.rule_action_allow
    },
)

const val DayMillis = 24 * 60 * 60 * 1000L
