package oolong.client.database

import android.content.SharedPreferences
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** A keyword searched for across Tieba, by the account with uid [owner], empty when logged out. */
@Entity(tableName = "search_history", indices = [Index(value = ["owner", "content"], unique = true)])
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "") val owner: String = "",
    val content: String,
    val timestamp: Long,
)

/** A keyword searched for inside a forum, by the account with uid [owner], empty when logged out. */
@Entity(
    tableName = "forum_search_history",
    indices = [Index(value = ["owner", "forum_name", "content"], unique = true)],
)
data class ForumSearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "") val owner: String = "",
    val content: String,
    @ColumnInfo(name = "forum_name") val forumName: String,
    val timestamp: Long,
)

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history WHERE owner = :owner ORDER BY timestamp DESC")
    suspend fun all(owner: String): List<SearchHistoryEntity>

    @Query("SELECT * FROM search_history WHERE owner = :owner ORDER BY timestamp DESC")
    fun allFlow(owner: String): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(history: SearchHistoryEntity): Long

    @Query("UPDATE search_history SET timestamp = :timestamp WHERE owner = :owner AND content = :content")
    suspend fun touch(owner: String, content: String, timestamp: Long)

    /** Adds the keyword to [owner]'s, or moves it to the top if it was searched for before. */
    @Transaction
    suspend fun add(owner: String, content: String, timestamp: Long) {
        if (insert(SearchHistoryEntity(owner = owner, content = content, timestamp = timestamp)) == -1L) touch(owner, content, timestamp)
    }

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM search_history WHERE owner = :owner")
    suspend fun clear(owner: String)

    @Query("SELECT * FROM forum_search_history ORDER BY timestamp DESC")
    suspend fun allInForums(): List<ForumSearchHistoryEntity>

    /** [owner]'s keywords searched for in [forumName], latest first, followed as they change. */
    @Query("SELECT * FROM forum_search_history WHERE owner = :owner AND forum_name = :forumName ORDER BY timestamp DESC")
    fun inForumFlow(owner: String, forumName: String): Flow<List<ForumSearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInForum(history: ForumSearchHistoryEntity): Long

    @Query("UPDATE forum_search_history SET timestamp = :timestamp WHERE owner = :owner AND forum_name = :forumName AND content = :content")
    suspend fun touchInForum(owner: String, content: String, forumName: String, timestamp: Long)

    /** Adds the keyword searched for in [forumName] to [owner]'s, or moves it to the top. */
    @Transaction
    suspend fun addInForum(owner: String, content: String, forumName: String, timestamp: Long) {
        val history = ForumSearchHistoryEntity(owner = owner, content = content, forumName = forumName, timestamp = timestamp)
        if (insertInForum(history) == -1L) touchInForum(owner, content, forumName, timestamp)
    }

    @Query("DELETE FROM forum_search_history WHERE id = :id")
    suspend fun deleteInForum(id: Long)

    @Query("DELETE FROM forum_search_history WHERE owner = :owner AND forum_name = :forumName")
    suspend fun clearInForum(owner: String, forumName: String)
}

/** Gives the search history kept before it was per account to the account with row id [accountId], if there is one. */
internal fun SupportSQLiteDatabase.giveSearchHistoryTo(accountId: Int) {
    for (table in listOf("search_history", "forum_search_history")) {
        execSQL(
            "UPDATE $table SET owner = COALESCE((SELECT uid FROM accounts WHERE id = ?), '') WHERE owner = ''",
            arrayOf<Any>(accountId),
        )
    }
}

/** Search history per account, as browsing history: what was searched before belongs to the account that was current. */
fun migration5To6(accountPrefs: SharedPreferences) = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `search_history` ADD COLUMN `owner` TEXT NOT NULL DEFAULT ''")
        db.execSQL("DROP INDEX IF EXISTS `index_search_history_content`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_search_history_owner_content` ON `search_history` (`owner`, `content`)")
        db.execSQL("ALTER TABLE `forum_search_history` ADD COLUMN `owner` TEXT NOT NULL DEFAULT ''")
        db.execSQL("DROP INDEX IF EXISTS `index_forum_search_history_forum_name_content`")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_forum_search_history_owner_forum_name_content` " +
                "ON `forum_search_history` (`owner`, `forum_name`, `content`)",
        )
        db.giveSearchHistoryTo(accountPrefs.getInt(LegacyDatabaseImport.KEY_CURRENT_ACCOUNT, -1))
    }
}
