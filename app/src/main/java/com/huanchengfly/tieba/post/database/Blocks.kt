package com.huanchengfly.tieba.post.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.huanchengfly.tieba.post.model.BlockRule
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

/**
 * A block rule: its [condition] as [BlockConditionJson] writes it, and the rest as in
 * [BlockRule], [action] counting [BlockRule.Action] in order.
 */
@Entity(tableName = "block_rules")
data class BlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val condition: String,
    @ColumnInfo(defaultValue = "0") val scope: Int = 0,
    @ColumnInfo(defaultValue = "0") val action: Int = 0,
    @ColumnInfo(name = "expires_at") val expiresAt: Long? = null,
    @ColumnInfo(defaultValue = "1") val enabled: Boolean = true,
    @ColumnInfo(defaultValue = "0") val hits: Long = 0,
    @ColumnInfo(name = "last_hit_at") val lastHitAt: Long? = null,
    @ColumnInfo(name = "created_at", defaultValue = "0") val createdAt: Long = 0,
)

/** How rule conditions are written, in the database and in backups. */
val BlockConditionJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

fun BlockRule.Condition.toJson(): String = BlockConditionJson.encodeToString(BlockRule.Condition.serializer(), this)

/** The condition [json] describes, or null when it describes none this version knows. */
fun blockConditionOf(json: String): BlockRule.Condition? =
    runCatching { BlockConditionJson.decodeFromString(BlockRule.Condition.serializer(), json) }.getOrNull()

@Dao
interface BlockDao {
    @Query("SELECT * FROM block_rules ORDER BY id")
    fun all(): Flow<List<BlockEntity>>

    @Insert
    suspend fun insert(block: BlockEntity): Long

    @Insert
    suspend fun insertAll(blocks: List<BlockEntity>)

    @Update
    suspend fun update(block: BlockEntity)

    @Query("DELETE FROM block_rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM block_rules WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)

    @Query("UPDATE block_rules SET hits = hits + :count, last_hit_at = :at WHERE id = :id")
    suspend fun addHits(id: Long, count: Long, at: Long)
}

/**
 * Rules become one condition each, which may join any tests, in place of a single match with
 * fixed conditions; exceptions are marked by their action instead of a list.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `block_rules_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `condition` TEXT NOT NULL, " +
                "`scope` INTEGER NOT NULL DEFAULT 0, `action` INTEGER NOT NULL DEFAULT 0, `expires_at` INTEGER, " +
                "`enabled` INTEGER NOT NULL DEFAULT 1, `hits` INTEGER NOT NULL DEFAULT 0, `last_hit_at` INTEGER, " +
                "`created_at` INTEGER NOT NULL DEFAULT 0)",
        )
        db.query(
            "SELECT id, category, type, keywords, username, uid, forum_name, display_name, level, regex, in_forum, title_only, " +
                "scope, action, expires_at, enabled, hits, last_hit_at, created_at FROM block_rules",
        ).use { rows ->
            while (rows.moveToNext()) {
                fun text(i: Int) = if (rows.isNull(i)) null else rows.getString(i)
                fun long(i: Int) = if (rows.isNull(i)) null else rows.getLong(i)
                val condition = legacyCondition(
                    type = rows.getInt(2),
                    keywords = text(3),
                    username = text(4),
                    uid = text(5),
                    forumName = text(6),
                    displayName = text(7),
                    level = long(8)?.toInt(),
                    regex = rows.getInt(9) != 0,
                    inForum = text(10),
                    titleOnly = rows.getInt(11) != 0,
                )
                val values = ContentValues().apply {
                    put("id", rows.getLong(0))
                    put("condition", condition.toJson())
                    put("scope", rows.getInt(12))
                    put("action", if (rows.getInt(1) == LegacyAllowList) BlockRule.Action.Allow.ordinal else rows.getInt(13))
                    put("expires_at", long(14))
                    put("enabled", rows.getInt(15))
                    put("hits", rows.getLong(16))
                    put("last_hit_at", long(17))
                    put("created_at", rows.getLong(18))
                }
                db.insert("block_rules_new", SQLiteDatabase.CONFLICT_ABORT, values)
            }
        }
        db.execSQL("DROP TABLE `block_rules`")
        db.execSQL("ALTER TABLE `block_rules_new` RENAME TO `block_rules`")
    }
}

/** The old list exceptions were kept on. */
internal const val LegacyAllowList = 11

/**
 * What a rule from before conditions matched: keywords (a JSON array, one pattern when [regex]),
 * a user, a forum or users below a level, maybe only in [inForum] or only in titles. A rule that
 * made no sense becomes one that matches nothing.
 */
internal fun legacyCondition(
    type: Int,
    keywords: String?,
    username: String?,
    uid: String?,
    forumName: String? = null,
    displayName: String? = null,
    level: Int? = null,
    regex: Boolean = false,
    inForum: String? = null,
    titleOnly: Boolean = false,
): BlockRule.Condition {
    val match = when (type) {
        1 -> BlockRule.Condition.User(uid?.toLongOrNull() ?: 0L, username.orEmpty(), displayName.orEmpty())
        2 -> BlockRule.Condition.Forum(forumName.orEmpty())
        3 -> BlockRule.Condition.LevelBelow(level ?: 0)
        else -> {
            val words = keywords?.let { runCatching { Json.decodeFromString<List<String>>(it) }.getOrNull() }.orEmpty()
            BlockRule.Condition.Text(
                pattern = if (regex) BlockRule.Pattern.Regex(words.firstOrNull().orEmpty()) else BlockRule.Pattern.Words(words),
                field = if (titleOnly) BlockRule.TextField.Title else BlockRule.TextField.Both,
            )
        }
    }
    val forum = inForum?.takeIf { it.isNotBlank() } ?: return match
    return BlockRule.Condition.AllOf(listOf(BlockRule.Condition.Forum(forum), match))
}

/** User rules keep how the user was shown when blocked. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `block_rules` ADD COLUMN `display_name` TEXT")
    }
}

/** Rules gain conditions, where they apply, what they do, an expiry, a switch and hit counts. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf(
            "`forum_name` TEXT",
            "`level` INTEGER",
            "`regex` INTEGER NOT NULL DEFAULT 0",
            "`in_forum` TEXT",
            "`title_only` INTEGER NOT NULL DEFAULT 0",
            "`scope` INTEGER NOT NULL DEFAULT 0",
            "`action` INTEGER NOT NULL DEFAULT 0",
            "`expires_at` INTEGER",
            "`enabled` INTEGER NOT NULL DEFAULT 1",
            "`hits` INTEGER NOT NULL DEFAULT 0",
            "`last_hit_at` INTEGER",
            "`created_at` INTEGER NOT NULL DEFAULT 0",
        ).forEach { db.execSQL("ALTER TABLE `block_rules` ADD COLUMN $it") }
    }
}
