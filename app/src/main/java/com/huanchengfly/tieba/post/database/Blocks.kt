package com.huanchengfly.tieba.post.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * A block rule. [type] says what it matches: [keywords] (a JSON array of strings, one pattern
 * when [regex]), a user by [uid] or [username], a forum by [forumName], or users below [level].
 * [category] marks an exception, shown always; the other columns are as in
 * [com.huanchengfly.tieba.post.repository.BlockRule].
 */
@Entity(tableName = "block_rules")
data class BlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: Int,
    val type: Int,
    val keywords: String? = null,
    val username: String? = null,
    val uid: String? = null,
    @ColumnInfo(name = "forum_name") val forumName: String? = null,
    val level: Int? = null,
    @ColumnInfo(defaultValue = "0") val regex: Boolean = false,
    @ColumnInfo(name = "in_forum") val inForum: String? = null,
    @ColumnInfo(name = "title_only", defaultValue = "0") val titleOnly: Boolean = false,
    @ColumnInfo(defaultValue = "0") val scope: Int = 0,
    @ColumnInfo(defaultValue = "0") val action: Int = 0,
    @ColumnInfo(name = "expires_at") val expiresAt: Long? = null,
    @ColumnInfo(defaultValue = "1") val enabled: Boolean = true,
    @ColumnInfo(defaultValue = "0") val hits: Long = 0,
    @ColumnInfo(name = "last_hit_at") val lastHitAt: Long? = null,
    @ColumnInfo(name = "created_at", defaultValue = "0") val createdAt: Long = 0,
)

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
