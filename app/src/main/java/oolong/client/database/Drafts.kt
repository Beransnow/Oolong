package oolong.client.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/** An unsent reply, keyed by what it replies to. */
@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val hash: String,
    val content: String,
)

@Dao
interface DraftDao {
    @Query("SELECT content FROM drafts WHERE hash = :hash")
    suspend fun content(hash: String): String?

    @Upsert
    suspend fun upsert(draft: DraftEntity)

    @Query("DELETE FROM drafts WHERE hash = :hash")
    suspend fun delete(hash: String)
}
