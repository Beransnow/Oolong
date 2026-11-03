package oolong.client.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One of the user's block rules: the [condition] content must meet, where the rule applies, what
 * it does, and until when.
 */
@Immutable
data class BlockRule(
    val id: Long = 0,
    val condition: Condition,
    val scope: Scope = Scope.All,
    val action: Action = Action.Default,
    /** Milliseconds since the epoch after which the rule no longer applies; null for never. */
    val expiresAt: Long? = null,
    val enabled: Boolean = true,
    val hits: Long = 0,
    val lastHitAt: Long? = null,
    val createdAt: Long = 0,
) {
    /**
     * What content must be for a rule to match it: one test of it, or tests joined by and, or or,
     * or turned around. The editor shows one level of joining; stored rules may have more.
     */
    @Immutable
    @Serializable
    sealed interface Condition {
        /** Every one of [conditions]; none means nothing matches. */
        @Serializable
        @SerialName("all")
        data class AllOf(val conditions: List<Condition>) : Condition

        /** Any of [conditions]; none means nothing matches. */
        @Serializable
        @SerialName("any")
        data class AnyOf(val conditions: List<Condition>) : Condition

        @Serializable
        @SerialName("not")
        data class Not(val condition: Condition) : Condition

        /** The title, the body, or either has [pattern]. */
        @Serializable
        @SerialName("text")
        data class Text(val pattern: Pattern, val field: TextField = TextField.Both) : Condition

        /**
         * A user, by id, or by name where the id is unknown. [displayName] is how the user was
         * shown when blocked, for showing the rule.
         */
        @Serializable
        @SerialName("user")
        data class User(val uid: Long, val name: String, val displayName: String = "") : Condition {
            val shownName: String get() = displayName.ifBlank { name }.ifBlank { uid.toString() }
        }

        /** The author's account or display name has [pattern]. */
        @Serializable
        @SerialName("author_name")
        data class AuthorName(val pattern: Pattern) : Condition

        /** The author's level in the forum is known and below [level]. */
        @Serializable
        @SerialName("level")
        data class LevelBelow(val level: Int) : Condition

        /** Whoever started the thread wrote it. */
        @Serializable
        @SerialName("thread_author")
        data object ThreadAuthor : Condition

        /** The author posted from [region], as shown under their name. */
        @Serializable
        @SerialName("ip")
        data class Ip(val region: String) : Condition

        /** It is in the forum named [name], without 吧. */
        @Serializable
        @SerialName("forum")
        data class Forum(val name: String) : Condition

        /** The name of the forum it is in has [pattern]. */
        @Serializable
        @SerialName("forum_name")
        data class ForumName(val pattern: Pattern) : Condition

        /** It holds some [kind] of content. */
        @Serializable
        @SerialName("has")
        data class Has(val kind: ContentKind) : Condition

        /** It holds [kind] of content and nothing else, words included. */
        @Serializable
        @SerialName("only")
        data class Only(val kind: ContentKind) : Condition

        /** Its own words have fewer than [length] letters and digits. */
        @Serializable
        @SerialName("shorter")
        data class ShorterThan(val length: Int) : Condition
    }

    /** What a text test looks for. */
    @Immutable
    @Serializable
    sealed interface Pattern {
        /** Every one of [words], ignoring case, width and what is put between their characters. */
        @Serializable
        @SerialName("words")
        data class Words(val words: List<String>) : Pattern

        @Serializable
        @SerialName("regex")
        data class Regex(val pattern: String) : Pattern
    }

    enum class TextField { Both, Title, Body }

    /** Where a rule applies. */
    enum class Scope { All, Feeds, Forum, Thread }

    /** What a rule does to what it matches. */
    enum class Action {
        /** As the user's setting says: fold or hide. */
        Default,
        Fold,
        Hide,

        /** Shown even when another rule matches: an exception. */
        Allow,
    }

    fun isActive(now: Long): Boolean = enabled && (expiresAt == null || expiresAt > now)
}

/** What content holds besides plain words. */
enum class ContentKind { Link, Image, Video, Voice, Mention, Emoticon }

/**
 * A condition as the editor shows it: [tests] joined all by and when [all], all by or otherwise,
 * each of them maybe turned around.
 */
@Immutable
data class FlatCondition(val all: Boolean, val tests: List<Test>) {
    @Immutable
    data class Test(val condition: BlockRule.Condition, val negated: Boolean = false)

    fun toCondition(): BlockRule.Condition {
        val conditions = tests.map { if (it.negated) BlockRule.Condition.Not(it.condition) else it.condition }
        return when {
            conditions.size == 1 -> conditions.single()
            all -> BlockRule.Condition.AllOf(conditions)
            else -> BlockRule.Condition.AnyOf(conditions)
        }
    }
}

/** [this] as one level of tests, or null when it joins or turns around more than that. */
fun BlockRule.Condition.flat(): FlatCondition? {
    fun test(condition: BlockRule.Condition): FlatCondition.Test? = when (condition) {
        is BlockRule.Condition.AllOf, is BlockRule.Condition.AnyOf -> null
        is BlockRule.Condition.Not -> condition.condition.takeIf { it.isTest }?.let { FlatCondition.Test(it, negated = true) }
        else -> FlatCondition.Test(condition)
    }
    return when (this) {
        is BlockRule.Condition.AllOf -> conditions.map { test(it) ?: return null }.let { FlatCondition(true, it) }
        is BlockRule.Condition.AnyOf -> conditions.map { test(it) ?: return null }.let { FlatCondition(false, it) }
        else -> test(this)?.let { FlatCondition(true, listOf(it)) }
    }
}

/** Whether [this] is a single test, not a joining or turning around of others. */
val BlockRule.Condition.isTest: Boolean
    get() = this !is BlockRule.Condition.AllOf && this !is BlockRule.Condition.AnyOf && this !is BlockRule.Condition.Not

/** Every single test in [this], however deep. */
val BlockRule.Condition.tests: List<BlockRule.Condition>
    get() = when (this) {
        is BlockRule.Condition.AllOf -> conditions.flatMap { it.tests }
        is BlockRule.Condition.AnyOf -> conditions.flatMap { it.tests }
        is BlockRule.Condition.Not -> condition.tests
        else -> listOf(this)
    }
