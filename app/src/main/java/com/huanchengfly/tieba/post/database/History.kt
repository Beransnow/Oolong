package com.huanchengfly.tieba.post.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * A visited thread or forum. [data] is the thread id or the forum name, depending on [type];
 * [count] is how many times it was visited, [timestamp] when it was last.
 */
@Entity(
    tableName = "history",
    indices = [Index(value = ["type", "data"], unique = true), Index(value = ["timestamp"])],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: Int,
    val data: String,
    val title: String = "",
    val timestamp: Long = 0,
    val count: Int = 0,
    val extras: String? = null,
    val avatar: String? = null,
    val username: String? = null,
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history WHERE type = :type ORDER BY timestamp DESC, count DESC LIMIT :limit OFFSET :offset")
    fun page(type: Int, limit: Int, offset: Int): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY timestamp DESC, count DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE type = :type AND data = :data")
    suspend fun find(type: Int, data: String): HistoryEntity?

    @Insert
    suspend fun insert(history: HistoryEntity): Long

    @Query(
        """UPDATE history SET title = :title, extras = :extras, avatar = :avatar, username = :username,
        timestamp = :timestamp, count = count + 1 WHERE id = :id"""
    )
    suspend fun revisit(id: Long, title: String, extras: String?, avatar: String?, username: String?, timestamp: Long)

    /** Records a visit at [timestamp]: a new entry, or one more visit to the existing one. */
    @Transaction
    suspend fun visit(history: HistoryEntity, timestamp: Long) {
        val existing = find(history.type, history.data)
        if (existing == null) {
            insert(history.copy(id = 0, timestamp = timestamp, count = 1))
        } else {
            revisit(existing.id, history.title, history.extras, history.avatar, history.username, timestamp)
        }
    }

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history")
    suspend fun clear()
}
