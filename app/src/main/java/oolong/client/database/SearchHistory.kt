package oolong.client.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** A keyword searched for across Tieba. */
@Entity(tableName = "search_history", indices = [Index(value = ["content"], unique = true)])
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val timestamp: Long,
)

/** A keyword searched for inside a forum. */
@Entity(
    tableName = "forum_search_history",
    indices = [Index(value = ["forum_name", "content"], unique = true)],
)
data class ForumSearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    @ColumnInfo(name = "forum_name") val forumName: String,
    val timestamp: Long,
)

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC")
    suspend fun all(): List<SearchHistoryEntity>

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC")
    fun allFlow(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(history: SearchHistoryEntity): Long

    @Query("UPDATE search_history SET timestamp = :timestamp WHERE content = :content")
    suspend fun touch(content: String, timestamp: Long)

    /** Adds the keyword, or moves it to the top if it was searched for before. */
    @Transaction
    suspend fun add(content: String, timestamp: Long) {
        if (insert(SearchHistoryEntity(content = content, timestamp = timestamp)) == -1L) touch(content, timestamp)
    }

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM search_history")
    suspend fun clear()

    @Query("SELECT * FROM forum_search_history ORDER BY timestamp DESC")
    suspend fun allInForums(): List<ForumSearchHistoryEntity>

    @Query("SELECT * FROM forum_search_history ORDER BY timestamp DESC")
    fun allInForumsFlow(): Flow<List<ForumSearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInForum(history: ForumSearchHistoryEntity): Long

    @Query("UPDATE forum_search_history SET timestamp = :timestamp WHERE forum_name = :forumName AND content = :content")
    suspend fun touchInForum(content: String, forumName: String, timestamp: Long)

    /** Adds the keyword searched for in [forumName], or moves it to the top. */
    @Transaction
    suspend fun addInForum(content: String, forumName: String, timestamp: Long) {
        val history = ForumSearchHistoryEntity(content = content, forumName = forumName, timestamp = timestamp)
        if (insertInForum(history) == -1L) touchInForum(content, forumName, timestamp)
    }

    @Query("DELETE FROM forum_search_history WHERE id = :id")
    suspend fun deleteInForum(id: Long)

    @Query("DELETE FROM forum_search_history")
    suspend fun clearInForums()
}
