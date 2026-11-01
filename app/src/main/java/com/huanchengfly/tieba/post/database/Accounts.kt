package com.huanchengfly.tieba.post.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A logged-in account: its credentials, and the profile shown in the account list. */
@Entity(tableName = "accounts", indices = [Index(value = ["uid"], unique = true)])
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val uid: String,
    val name: String = "",
    val bduss: String = "",
    val tbs: String = "",
    val portrait: String = "",
    val stoken: String = "",
    val cookie: String = "",
    @ColumnInfo(name = "name_show") val nameShow: String? = null,
    val intro: String? = null,
    val sex: String? = null,
    @ColumnInfo(name = "fans_num") val fansNum: String? = null,
    @ColumnInfo(name = "post_num") val postNum: String? = null,
    @ColumnInfo(name = "thread_num") val threadNum: String? = null,
    @ColumnInfo(name = "concern_num") val concernNum: String? = null,
    @ColumnInfo(name = "tb_age") val tbAge: String? = null,
    val age: String? = null,
    @ColumnInfo(name = "birthday_show_status") val birthdayShowStatus: String? = null,
    @ColumnInfo(name = "birthday_time") val birthdayTime: String? = null,
    val constellation: String? = null,
    @ColumnInfo(name = "tieba_uid") val tiebaUid: String? = null,
    @ColumnInfo(name = "load_success") val loadSuccess: Boolean = false,
    val uuid: String? = null,
    val zid: String? = null,
)

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY id")
    fun all(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY id")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun byId(id: Int): AccountEntity?

    @Query("SELECT * FROM accounts WHERE uid = :uid")
    suspend fun byUid(uid: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE bduss = :bduss")
    suspend fun byBduss(bduss: String): AccountEntity?

    /** Inserts the account, or replaces the one with the same id; returns its id. */
    @Upsert
    suspend fun upsert(account: AccountEntity): Long

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: Int)
}
