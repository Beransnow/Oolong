package oolong.client.repository

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import oolong.client.model.BlockRule
import oolong.client.model.BlockRule.Condition
import oolong.client.model.BlockRule.Pattern

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

    fun encode(rules: List<BlockRule>): String = json.encodeToString(Backup.serializer(), Backup(version = Version, rules = rules.map { it.toEntry() }))

    /** The rules in [text]; entries that make no sense are left out. Throws if [text] is not a backup. */
    fun decode(text: String): List<BlockRule> =
        json.decodeFromString(Backup.serializer(), text).rules.mapNotNull { it.toRule() }

    /** Files from before conditions say no version. */
    @Serializable
    private data class Backup(val version: Int = 1, val rules: List<Entry>)

    private const val Version = 2

    /**
     * A rule: its [condition], or, in files from version 1, a [type] of match with its values and
     * the conditions there were then.
     */
    @Serializable
    private data class Entry(
        val condition: Condition? = null,
        val scope: String = "All",
        val action: String = "Default",
        val expiresAt: Long? = null,
        val enabled: Boolean = true,
        val type: String? = null,
        val words: List<String> = emptyList(),
        val regex: Boolean = false,
        val uid: Long = 0,
        val name: String = "",
        val displayName: String = "",
        val level: Int = 0,
        val inForum: String? = null,
        val titleOnly: Boolean = false,
    )

    private fun BlockRule.toEntry() = Entry(
        condition = condition,
        scope = scope.name,
        action = action.name,
        expiresAt = expiresAt,
        enabled = enabled,
    )

    private fun Entry.toRule(): BlockRule? = BlockRule(
        condition = condition ?: legacyCondition() ?: return null,
        scope = BlockRule.Scope.entries.firstOrNull { it.name == scope } ?: BlockRule.Scope.All,
        action = BlockRule.Action.entries.firstOrNull { it.name == action } ?: BlockRule.Action.Default,
        expiresAt = expiresAt,
        enabled = enabled,
    )

    private fun Entry.legacyCondition(): Condition? {
        val match = when (type) {
            "keyword" -> words.takeIf { it.any(String::isNotBlank) }?.let {
                Condition.Text(
                    pattern = if (regex) Pattern.Regex(it.first()) else Pattern.Words(it),
                    field = if (titleOnly) BlockRule.TextField.Title else BlockRule.TextField.Both,
                )
            }
            "user" -> Condition.User(uid, name, displayName).takeIf { uid != 0L || name.isNotBlank() }
            "forum" -> Condition.Forum(name).takeIf { name.isNotBlank() }
            "level" -> Condition.LevelBelow(level).takeIf { level > 0 }
            else -> null
        } ?: return null
        val forum = inForum?.takeIf { it.isNotBlank() } ?: return match
        return Condition.AllOf(listOf(Condition.Forum(forum), match))
    }
}

/** Whether two rules do the same thing, whatever their ids, hits and dates. */
fun BlockRule.sameAs(other: BlockRule): Boolean =
    copy(id = 0, hits = 0, lastHitAt = null, createdAt = 0) == other.copy(id = 0, hits = 0, lastHitAt = null, createdAt = 0)
