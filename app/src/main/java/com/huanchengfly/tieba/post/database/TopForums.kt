package com.huanchengfly.tieba.post.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A followed forum the user pinned to the top of the home page. */
@Entity(tableName = "top_forums")
data class TopForumEntity(
    @PrimaryKey @ColumnInfo(name = "forum_id") val forumId: String,
)

@Dao
interface TopForumDao {
    @Query("SELECT forum_id FROM top_forums")
    suspend fun forumIds(): List<String>

    @Query("SELECT forum_id FROM top_forums")
    fun forumIdsFlow(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(forum: TopForumEntity)

    /** Returns how many rows were deleted. */
    @Query("DELETE FROM top_forums WHERE forum_id = :forumId")
    suspend fun delete(forumId: String): Int
}
