package com.huanchengfly.tieba.post.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A block or allow rule. [category] says which list it is on, [type] whether it matches
 * [keywords] (a JSON array of strings) or a user by [uid] or [username].
 */
@Entity(tableName = "block_rules")
data class BlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: Int,
    val type: Int,
    val keywords: String? = null,
    val username: String? = null,
    val uid: String? = null,
)

@Dao
interface BlockDao {
    @Query("SELECT * FROM block_rules ORDER BY id")
    fun all(): Flow<List<BlockEntity>>

    @Insert
    suspend fun insert(block: BlockEntity): Long

    @Query("DELETE FROM block_rules WHERE id = :id")
    suspend fun delete(id: Long)
}
