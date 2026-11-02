package com.huanchengfly.tieba.post.model

import androidx.compose.runtime.Immutable

/**
 * One of the user's block rules: what it [match]es, further conditions that must all hold, where
 * it applies, what it does, and until when. There is no "or": that is two rules.
 */
@Immutable
data class BlockRule(
    val id: Long = 0,
    val match: Match,
    /** Only content in this forum, by name without 吧; null for any forum. */
    val inForum: String? = null,
    /** Keywords are looked for in titles only. */
    val titleOnly: Boolean = false,
    val scope: Scope = Scope.All,
    val action: Action = Action.Default,
    /** Milliseconds since the epoch after which the rule no longer applies; null for never. */
    val expiresAt: Long? = null,
    val enabled: Boolean = true,
    val hits: Long = 0,
    val lastHitAt: Long? = null,
    val createdAt: Long = 0,
) {
    @Immutable
    sealed interface Match {
        /**
         * A user, by id, or by account name where the id is unknown. [displayName] is how the user
         * was shown when blocked, for showing the rule.
         */
        data class User(val uid: Long, val name: String, val displayName: String = "") : Match {
            val shownName: String get() = displayName.ifBlank { name }.ifBlank { uid.toString() }
        }

        /** Content containing every one of [words], or matching [words]' only entry when [regex]. */
        data class Keyword(val words: List<String>, val regex: Boolean = false) : Match

        /** Threads in a forum, by name without 吧. */
        data class Forum(val name: String) : Match

        /** Users whose level in the forum is below [level]. */
        data class Level(val level: Int) : Match
    }

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
