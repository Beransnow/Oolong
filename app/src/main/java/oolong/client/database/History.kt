package oolong.client.database

import android.content.SharedPreferences
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * A visited thread or forum. [data] is the thread id or the forum name, depending on [type];
 * [count] is how many times it was visited, [timestamp] when it was last. [owner] is the uid of
 * the account it was visited as, empty when logged out; each account has its own history.
 */
@Entity(
    tableName = "history",
    indices = [Index(value = ["owner", "type", "data"], unique = true), Index(value = ["timestamp"])],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "") val owner: String = "",
    val type: Int,
    val data: String,
    val title: String = "",
    val timestamp: Long = 0,
    val count: Int = 0,
    val extras: String? = null,
    val avatar: String? = null,
    val username: String? = null,
) {
    companion object {
        const val TYPE_FORUM = 1
        const val TYPE_THREAD = 2
    }
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history WHERE owner = :owner AND type = :type ORDER BY timestamp DESC, count DESC LIMIT :limit OFFSET :offset")
    suspend fun page(owner: String, type: Int, limit: Int, offset: Int): List<HistoryEntity>

    /** [owner]'s latest [limit] entries of [type], followed as they change. */
    @Query("SELECT * FROM history WHERE owner = :owner AND type = :type ORDER BY timestamp DESC, count DESC LIMIT :limit")
    fun latest(owner: String, type: Int, limit: Int): Flow<List<HistoryEntity>>

    /** All of [owner]'s entries of [type], latest first, followed as they change. */
    @Query("SELECT * FROM history WHERE owner = :owner AND type = :type ORDER BY timestamp DESC, count DESC")
    fun all(owner: String, type: Int): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE owner = :owner AND type = :type AND data = :data")
    suspend fun find(owner: String, type: Int, data: String): HistoryEntity?

    @Insert
    suspend fun insert(history: HistoryEntity): Long

    @Query(
        """UPDATE history SET title = :title, extras = :extras, avatar = :avatar, username = :username,
        timestamp = :timestamp, count = count + 1 WHERE id = :id"""
    )
    suspend fun revisit(id: Long, title: String, extras: String?, avatar: String?, username: String?, timestamp: Long)

    /** Records a visit at [timestamp] for the entry's owner: a new entry, or one more visit to the existing one. */
    @Transaction
    suspend fun visit(history: HistoryEntity, timestamp: Long) {
        val existing = find(history.owner, history.type, history.data)
        if (existing == null) {
            insert(history.copy(id = 0, timestamp = timestamp, count = 1))
        } else {
            revisit(existing.id, history.title, history.extras, history.avatar, history.username, timestamp)
        }
    }

    /** Returns how many rows were deleted. */
    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM history WHERE owner = :owner")
    suspend fun clear(owner: String)
}

/** Gives the history kept before it was per account to the account with row id [accountId], if there is one. */
internal fun SupportSQLiteDatabase.giveHistoryTo(accountId: Int) {
    execSQL(
        "UPDATE history SET owner = COALESCE((SELECT uid FROM accounts WHERE id = ?), '') WHERE owner = ''",
        arrayOf<Any>(accountId),
    )
}

/**
 * History per account: what was visited before belongs to the account that was current, whose row
 * id [accountPrefs] holds.
 */
fun migration4To5(accountPrefs: SharedPreferences) = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `history` ADD COLUMN `owner` TEXT NOT NULL DEFAULT ''")
        db.execSQL("DROP INDEX IF EXISTS `index_history_type_data`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_history_owner_type_data` ON `history` (`owner`, `type`, `data`)")
        db.giveHistoryTo(accountPrefs.getInt(LegacyDatabaseImport.KEY_CURRENT_ACCOUNT, -1))
    }
}
