package oolong.client.ui.page.settings.block

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import oolong.client.R
import oolong.client.model.BlockRule
import oolong.client.model.BlockRule.Condition
import oolong.client.model.BlockRule.Pattern
import oolong.client.model.BlockRule.TextField
import oolong.client.model.ContentKind
import oolong.client.model.flat

/** A part of a rule's sentence that the editor lets the user change. */
enum class RulePart { Scope, Action, Join, Value, Expiry }

/** A run of a rule's sentence: plain words, or a [part] of the rule, marked when left at its [isDefault]. */
data class SentenceRun(val text: String, val part: RulePart? = null, val isDefault: Boolean = false)

/**
 * [rule] as one sentence, such as "在 推荐和热榜 屏蔽 「抽卡吧」里 且 含有「剧透」 的内容 · 7 天后失效".
 * With [full], for the editor, parts left at their defaults are spelled out too, and tests that
 * the editor lists one by one are summed up as "满足以下全部条件"; otherwise defaults are left out.
 */
@Composable
fun ruleSentence(rule: BlockRule, now: Long, full: Boolean = false): List<SentenceRun> = buildList {
    val scopeDefault = rule.scope == BlockRule.Scope.All
    if (full || !scopeDefault) {
        add(SentenceRun(stringResource(R.string.rule_in)))
        add(SentenceRun(scopeName(rule.scope), RulePart.Scope, scopeDefault))
        add(SentenceRun(stringResource(R.string.rule_after_scope)))
    }
    val action = actionName(rule.action)
    add(SentenceRun(if (isEmpty()) action else action.midSentence(), RulePart.Action, rule.action == BlockRule.Action.Default))
    add(SentenceRun(" "))
    add(SentenceRun(stringResource(R.string.rule_content_lead)))
    val flat = rule.condition.flat()
    if (full && flat != null && flat.tests.size > 1) {
        add(SentenceRun(stringResource(R.string.rule_meeting)))
        add(SentenceRun(stringResource(if (flat.all) R.string.rule_all else R.string.rule_any).midSentence(), RulePart.Join, flat.all))
        add(SentenceRun(stringResource(R.string.rule_conditions)))
    } else {
        addAll(conditionSentence(rule.condition))
        add(SentenceRun(stringResource(R.string.rule_whose_content)))
    }
    val expiresAt = rule.expiresAt
    if (full || expiresAt != null) {
        add(SentenceRun(" · "))
        val text = when {
            expiresAt == null -> stringResource(R.string.rule_forever)
            expiresAt <= now -> stringResource(R.string.rule_expired)
            else -> ((expiresAt - now + DayMillis - 1) / DayMillis).toInt().let { pluralStringResource(R.plurals.rule_expires_in, it, it) }
        }
        add(SentenceRun(text, RulePart.Expiry, expiresAt == null))
    }
}

/** [condition] in words: its tests joined by 且 or 或, those joined deeper in brackets. */
@Composable
fun conditionSentence(condition: Condition, nested: Boolean = false): List<SentenceRun> = buildList {
    when (condition) {
        is Condition.AllOf, is Condition.AnyOf -> {
            val parts = if (condition is Condition.AllOf) condition.conditions else (condition as Condition.AnyOf).conditions
            val join = stringResource(if (condition is Condition.AllOf) R.string.rule_and else R.string.rule_or)
            if (nested && parts.size > 1) add(SentenceRun(stringResource(R.string.rule_group_open)))
            parts.forEachIndexed { index, part ->
                if (index > 0) add(SentenceRun(join))
                addAll(conditionSentence(part, nested = true))
            }
            if (nested && parts.size > 1) add(SentenceRun(stringResource(R.string.rule_group_close)))
        }

        is Condition.Not -> if (condition.condition is Condition.AllOf || condition.condition is Condition.AnyOf) {
            add(SentenceRun(stringResource(R.string.rule_not)))
            addAll(conditionSentence(condition.condition, nested = true))
        } else {
            addAll(testSentence(condition.condition, negated = true))
        }

        else -> addAll(testSentence(condition, negated = false))
    }
}

/** One test in words, its value marked, such as "标题不含有「剧透」". */
@Composable
fun testSentence(test: Condition, negated: Boolean): List<SentenceRun> {
    fun value(text: String) = SentenceRun(text, RulePart.Value)

    // A value not typed yet reads as an ellipsis between its marks
    fun typed(text: String) = text.ifBlank { "…" }
    fun plain(text: String) = SentenceRun(text)
    val quote = stringResource(R.string.rule_quoted)
    fun quoted(text: String) = value(quote.format(typed(text)))

    // A template's words around its value, which alone is marked
    fun around(template: String, text: String): List<SentenceRun> {
        val at = template.indexOf("%1\$s")
        return listOf(plain(template.take(at)), value(text), plain(template.drop(at + 4))).filter { it.text.isNotEmpty() }
    }

    @Composable
    fun pattern(subject: String, pattern: Pattern): List<SentenceRun> = when (pattern) {
        is Pattern.Words -> listOf(
            plain(subject + stringResource(if (negated) R.string.rule_not_containing else R.string.rule_containing)),
            quoted(pattern.words.joinToString(" ")),
        )
        is Pattern.Regex -> listOf(
            plain(subject + stringResource(if (negated) R.string.rule_not_matching else R.string.rule_matching)),
            value("/${typed(pattern.pattern)}/"),
        )
    }
    return when (test) {
        is Condition.Text -> pattern(
            when (test.field) {
                TextField.Both -> ""
                TextField.Title -> stringResource(R.string.rule_title)
                TextField.Body -> stringResource(R.string.rule_body)
            },
            test.pattern,
        )

        is Condition.User -> if (negated) {
            listOf(plain(stringResource(R.string.rule_author_is_not)), quoted(test.shownName))
        } else {
            listOf(plain(stringResource(R.string.rule_user)), value(typed(test.shownName)))
        }

        is Condition.AuthorName -> pattern(stringResource(R.string.rule_author_name), test.pattern)
        is Condition.LevelBelow -> listOf(
            plain(stringResource(if (negated) R.string.rule_level_not_below else R.string.rule_level_below)),
            value(stringResource(R.string.rule_level, test.level)),
        )

        Condition.ThreadAuthor -> listOf(plain(stringResource(if (negated) R.string.rule_not_thread_author else R.string.rule_thread_author)))
        is Condition.Ip -> listOf(plain(stringResource(if (negated) R.string.rule_ip_is_not else R.string.rule_ip_is)), quoted(test.region))
        is Condition.Forum -> around(
            stringResource(if (negated) R.string.rule_not_in_forum else R.string.rule_in_forum),
            stringResource(R.string.rule_forum, typed(test.name.removeSuffix("吧"))),
        )

        is Condition.ForumName -> pattern(stringResource(R.string.rule_forum_name), test.pattern)
        is Condition.Has -> listOf(plain(stringResource(if (negated) R.string.rule_has_not else R.string.rule_has)), value(kindName(test.kind).midSentence()))
        is Condition.Only -> listOf(plain(stringResource(if (negated) R.string.rule_not_only else R.string.rule_only)), value(kindName(test.kind).midSentence()))
        is Condition.ShorterThan -> listOf(
            plain(stringResource(if (negated) R.string.rule_length_not_below else R.string.rule_length_below)),
            value(pluralStringResource(R.plurals.rule_length, test.length, test.length)),
        )

        is Condition.AllOf, is Condition.AnyOf, is Condition.Not -> conditionSentence(test, nested = true)
    }
}

@Composable
fun kindName(kind: ContentKind): String = stringResource(
    when (kind) {
        ContentKind.Link -> R.string.rule_kind_link
        ContentKind.Image -> R.string.rule_kind_image
        ContentKind.Video -> R.string.rule_kind_video
        ContentKind.Voice -> R.string.rule_kind_voice
        ContentKind.Mention -> R.string.rule_kind_mention
        ContentKind.Emoticon -> R.string.rule_kind_emoticon
    },
)

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

/**
 * A name that also heads a menu, lowered to run on inside a sentence in languages that capitalise
 * only its start; acronyms such as "IP" stay as they are.
 */
@Composable
private fun String.midSentence(): String {
    val locale = LocalConfiguration.current.locales[0]
    if (locale.language == "zh" || length < 2 || get(1).isUpperCase()) return this
    return replaceFirstChar { it.lowercase(locale) }
}

const val DayMillis = 24 * 60 * 60 * 1000L
