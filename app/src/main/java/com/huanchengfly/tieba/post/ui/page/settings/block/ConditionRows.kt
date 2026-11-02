package com.huanchengfly.tieba.post.ui.page.settings.block

import com.huanchengfly.tieba.post.model.BlockRule
import com.huanchengfly.tieba.post.model.BlockRule.Condition
import com.huanchengfly.tieba.post.model.BlockRule.Pattern
import com.huanchengfly.tieba.post.model.BlockRule.TextField
import com.huanchengfly.tieba.post.model.ContentKind

/** What a condition row looks at, its first choice. */
enum class ConditionField { TitleOrBody, Title, Body, Author, Level, ThreadAuthor, Ip, Forum, Content, Length }

/** How a condition row judges what it looks at, its second choice, each with an opposite. */
enum class ConditionOp { Contains, Matches, Is, Below, IsThreadAuthor, Has, Only }

/** The judgements [this] offers, the first for a new row. */
val ConditionField.ops: List<ConditionOp>
    get() = when (this) {
        ConditionField.TitleOrBody, ConditionField.Title, ConditionField.Body -> listOf(ConditionOp.Contains, ConditionOp.Matches)
        ConditionField.Author, ConditionField.Forum -> listOf(ConditionOp.Is, ConditionOp.Contains, ConditionOp.Matches)
        ConditionField.Level, ConditionField.Length -> listOf(ConditionOp.Below)
        ConditionField.ThreadAuthor -> listOf(ConditionOp.IsThreadAuthor)
        ConditionField.Ip -> listOf(ConditionOp.Is)
        ConditionField.Content -> listOf(ConditionOp.Has, ConditionOp.Only)
    }

/** Whether [this] judgement has a value typed in as text. */
val ConditionOp.typed: Boolean get() = this == ConditionOp.Contains || this == ConditionOp.Matches || this == ConditionOp.Is

val Condition.field: ConditionField
    get() = when (this) {
        is Condition.Text -> when (field) {
            TextField.Both -> ConditionField.TitleOrBody
            TextField.Title -> ConditionField.Title
            TextField.Body -> ConditionField.Body
        }
        is Condition.User, is Condition.AuthorName -> ConditionField.Author
        is Condition.LevelBelow -> ConditionField.Level
        Condition.ThreadAuthor -> ConditionField.ThreadAuthor
        is Condition.Ip -> ConditionField.Ip
        is Condition.Forum, is Condition.ForumName -> ConditionField.Forum
        is Condition.Has, is Condition.Only -> ConditionField.Content
        is Condition.ShorterThan -> ConditionField.Length
        // Joined tests are not rows
        is Condition.AllOf, is Condition.AnyOf, is Condition.Not -> ConditionField.TitleOrBody
    }

val Condition.op: ConditionOp
    get() = when (this) {
        is Condition.Text -> pattern.op
        is Condition.AuthorName -> pattern.op
        is Condition.ForumName -> pattern.op
        is Condition.User, is Condition.Forum, is Condition.Ip -> ConditionOp.Is
        is Condition.LevelBelow, is Condition.ShorterThan -> ConditionOp.Below
        Condition.ThreadAuthor -> ConditionOp.IsThreadAuthor
        is Condition.Has -> ConditionOp.Has
        is Condition.Only -> ConditionOp.Only
        is Condition.AllOf, is Condition.AnyOf, is Condition.Not -> ConditionOp.Contains
    }

private val Pattern.op get() = if (this is Pattern.Regex) ConditionOp.Matches else ConditionOp.Contains

/** The value typed into a row, for the judgements that take one. */
val Condition.typedValue: String
    get() = when (this) {
        is Condition.Text -> pattern.text
        is Condition.AuthorName -> pattern.text
        is Condition.ForumName -> pattern.text
        is Condition.User -> shownName.takeIf { uid != 0L || name.isNotEmpty() }.orEmpty()
        is Condition.Forum -> name
        is Condition.Ip -> region
        else -> ""
    }

private val Pattern.text
    get() = when (this) {
        is Pattern.Words -> words.joinToString(" ")
        is Pattern.Regex -> pattern
    }

private fun patternOf(op: ConditionOp, text: String): Pattern =
    if (op == ConditionOp.Matches) Pattern.Regex(text) else Pattern.Words(text.split(' ', '　'))

/** A new row looking at [field], judged by [op], keeping [value] where it still makes sense. */
fun conditionOf(field: ConditionField, op: ConditionOp = field.ops.first(), value: String = ""): Condition {
    val textField = when (field) {
        ConditionField.Title -> TextField.Title
        ConditionField.Body -> TextField.Body
        else -> TextField.Both
    }
    return when (field) {
        ConditionField.TitleOrBody, ConditionField.Title, ConditionField.Body -> Condition.Text(patternOf(op, value), textField)
        ConditionField.Author -> if (op == ConditionOp.Is) Condition.User(0, value) else Condition.AuthorName(patternOf(op, value))
        ConditionField.Forum -> if (op == ConditionOp.Is) Condition.Forum(value) else Condition.ForumName(patternOf(op, value))
        ConditionField.Ip -> Condition.Ip(value)
        ConditionField.Level -> Condition.LevelBelow(DefaultLevel)
        ConditionField.Length -> Condition.ShorterThan(DefaultLength)
        ConditionField.ThreadAuthor -> Condition.ThreadAuthor
        ConditionField.Content -> if (op == ConditionOp.Only) Condition.Only(ContentKind.Emoticon) else Condition.Has(ContentKind.Link)
    }
}

/** [this] row judged by [op] instead, keeping what it looks at and its value. */
fun Condition.withOp(op: ConditionOp): Condition = when (this) {
    is Condition.Has -> if (op == ConditionOp.Only) Condition.Only(kind) else this
    is Condition.Only -> if (op == ConditionOp.Has) Condition.Has(kind) else this
    else -> if (op == this.op) this else conditionOf(field, op, typedValue)
}

/** [this] row with [text] typed in as its value. */
fun Condition.withTypedValue(text: String): Condition = when (this) {
    // A name typed in is someone else than the user picked
    is Condition.User -> Condition.User(0, text)
    else -> conditionOf(field, op, text)
}

/** Whether [this] row tests anything, so the rule can be saved. */
val Condition.isComplete: Boolean
    get() = when (this) {
        is Condition.Text -> pattern.isComplete
        is Condition.AuthorName -> pattern.isComplete
        is Condition.ForumName -> pattern.isComplete
        is Condition.User -> uid != 0L || name.isNotBlank()
        is Condition.Forum -> name.isNotBlank()
        is Condition.Ip -> region.isNotBlank()
        is Condition.LevelBelow -> level > 0
        is Condition.ShorterThan -> length > 0
        is Condition.AllOf -> conditions.isNotEmpty() && conditions.all { it.isComplete }
        is Condition.AnyOf -> conditions.isNotEmpty() && conditions.all { it.isComplete }
        is Condition.Not -> condition.isComplete
        else -> true
    }

private val Pattern.isComplete
    get() = when (this) {
        is Pattern.Words -> words.any { it.isNotBlank() }
        is Pattern.Regex -> pattern.isNotEmpty() && isValid
    }

/** Whether [this] pattern can be used; words always can. */
val Pattern.isValid: Boolean
    get() = this !is Pattern.Regex || runCatching { Regex(pattern) }.isSuccess

/** [this] as saved: keywords without blanks, names trimmed, forum names without 吧. */
fun Condition.cleaned(): Condition = when (this) {
    is Condition.AllOf -> Condition.AllOf(conditions.map { it.cleaned() })
    is Condition.AnyOf -> Condition.AnyOf(conditions.map { it.cleaned() })
    is Condition.Not -> Condition.Not(condition.cleaned())
    is Condition.Text -> copy(pattern = pattern.cleaned())
    is Condition.AuthorName -> copy(pattern = pattern.cleaned())
    is Condition.ForumName -> copy(pattern = pattern.cleaned())
    is Condition.User -> copy(name = name.trim())
    is Condition.Forum -> copy(name = name.trim().removeSuffix("吧"))
    is Condition.Ip -> copy(region = region.trim())
    else -> this
}

private fun Pattern.cleaned(): Pattern = when (this) {
    is Pattern.Words -> Pattern.Words(words.filter { it.isNotBlank() })
    is Pattern.Regex -> this
}

/** Levels a row can be set to. */
val LevelRange = 2..18

const val DefaultLevel = 3
const val DefaultLength = 5

/** What [BlockRule.Condition.Has] and [BlockRule.Condition.Only] offer, in menu order. */
val ContentKinds = listOf(ContentKind.Link, ContentKind.Image, ContentKind.Video, ContentKind.Voice, ContentKind.Mention, ContentKind.Emoticon)
