package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.post.model.BlockRule
import com.huanchengfly.tieba.post.model.BlockRule.Match
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Block rules as a file to keep or pass on: what each matches and how it applies, without its
 * hits or dates, which belong to the device it was used on.
 */
object BlockRuleBackup {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        prettyPrint = true
    }

    fun encode(rules: List<BlockRule>): String = json.encodeToString(Backup.serializer(), Backup(rules = rules.map { it.toEntry() }))

    /** The rules in [text]; entries that make no sense are left out. Throws if [text] is not a backup. */
    fun decode(text: String): List<BlockRule> =
        json.decodeFromString(Backup.serializer(), text).rules.mapNotNull { it.toRule() }

    @Serializable
    private data class Backup(val version: Int = 1, val rules: List<Entry>)

    @Serializable
    private data class Entry(
        val type: String,
        val words: List<String> = emptyList(),
        val regex: Boolean = false,
        val uid: Long = 0,
        val name: String = "",
        val displayName: String = "",
        val level: Int = 0,
        val inForum: String? = null,
        val titleOnly: Boolean = false,
        val scope: String = "All",
        val action: String = "Default",
        val expiresAt: Long? = null,
        val enabled: Boolean = true,
    )

    private fun BlockRule.toEntry(): Entry {
        val base = Entry(
            type = "",
            inForum = inForum,
            titleOnly = titleOnly,
            scope = scope.name,
            action = action.name,
            expiresAt = expiresAt,
            enabled = enabled,
        )
        return when (val match = match) {
            is Match.Keyword -> base.copy(type = "keyword", words = match.words, regex = match.regex)
            is Match.User -> base.copy(type = "user", uid = match.uid, name = match.name, displayName = match.displayName)
            is Match.Forum -> base.copy(type = "forum", name = match.name)
            is Match.Level -> base.copy(type = "level", level = match.level)
        }
    }

    private fun Entry.toRule(): BlockRule? {
        val match = when (type) {
            "keyword" -> Match.Keyword(words, regex).takeIf { words.any { it.isNotBlank() } }
            "user" -> Match.User(uid, name, displayName).takeIf { uid != 0L || name.isNotBlank() }
            "forum" -> Match.Forum(name).takeIf { name.isNotBlank() }
            "level" -> Match.Level(level).takeIf { level > 0 }
            else -> null
        } ?: return null
        return BlockRule(
            match = match,
            inForum = inForum,
            titleOnly = titleOnly,
            scope = BlockRule.Scope.entries.firstOrNull { it.name == scope } ?: BlockRule.Scope.All,
            action = BlockRule.Action.entries.firstOrNull { it.name == action } ?: BlockRule.Action.Default,
            expiresAt = expiresAt,
            enabled = enabled,
        )
    }
}

/** Whether two rules do the same thing, whatever their ids, hits and dates. */
fun BlockRule.sameAs(other: BlockRule): Boolean =
    copy(id = 0, hits = 0, lastHitAt = null, createdAt = 0) == other.copy(id = 0, hits = 0, lastHitAt = null, createdAt = 0)
