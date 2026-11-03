package oolong.client.database

import android.content.ContentValues
import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import oolong.client.model.BlockRule
import java.io.File

/**
 * Copies what the old LitePal database holds into the Room database when that is created, so it
 * happens once and inside the transaction creating it.
 *
 * The old database is only read and stays where it is. Its schema may be older than the last
 * LitePal models, so missing tables and columns are read as empty. A row that can't be copied is
 * skipped rather than failing the whole import, which would leave the app without a database.
 *
 * The current account is remembered by its row id in [accountPrefs]; ids are kept, and when two
 * rows had the same uid the pointer moves to the one that is kept.
 */
class LegacyDatabaseImport(
    private val legacyFile: File,
    private val accountPrefs: SharedPreferences,
) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        if (!legacyFile.exists()) return
        val legacy = try {
            SQLiteDatabase.openDatabase(legacyFile.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            Log.w(TAG, "Can't open the old database", e)
            return
        }
        legacy.use { import(it, db) }
    }

    private fun import(legacy: SQLiteDatabase, db: SupportSQLiteDatabase) {
        copy(legacy, db, "account", "accounts", "id", replace = true) { row ->
            val uid = row.string("uid")
            if (uid.isNullOrBlank()) return@copy null
            ContentValues().apply {
                put("id", row.long("id"))
                put("uid", uid)
                put("name", row.string("name").orEmpty())
                put("bduss", row.string("bduss").orEmpty())
                put("tbs", row.string("tbs").orEmpty())
                put("portrait", row.string("portrait").orEmpty())
                put("stoken", row.string("stoken").orEmpty())
                put("cookie", row.string("cookie").orEmpty())
                put("name_show", row.string("nameshow"))
                put("intro", row.string("intro"))
                put("sex", row.string("sex"))
                put("fans_num", row.string("fansnum"))
                put("post_num", row.string("postnum"))
                put("thread_num", row.string("threadnum"))
                put("concern_num", row.string("concernnum"))
                put("tb_age", row.string("tbage"))
                put("age", row.string("age"))
                put("birthday_show_status", row.string("birthdayshowstatus"))
                put("birthday_time", row.string("birthdaytime"))
                put("constellation", row.string("constellation"))
                put("tieba_uid", row.string("tiebauid"))
                put("load_success", (row.long("loadsuccess") ?: 0L) != 0L)
                put("uuid", row.string("uuid"))
                put("zid", row.string("zid"))
            }
        }
        repointCurrentAccount(legacy, db)

        // Ordered by time, so of entries that are now the same one the latest is kept
        copy(legacy, db, "history", "history", "timestamp, id", replace = true) { row ->
            val data = row.string("data") ?: return@copy null
            ContentValues().apply {
                put("id", row.long("id"))
                put("type", row.long("type") ?: 0L)
                put("data", data)
                put("title", row.string("title").orEmpty())
                put("timestamp", row.long("timestamp") ?: 0L)
                put("count", row.long("count") ?: 0L)
                put("extras", row.string("extras"))
                put("avatar", row.string("avatar"))
                put("username", row.string("username"))
            }
        }
        copy(legacy, db, "block", "block_rules", "id", replace = false) { row ->
            val condition = legacyCondition(
                type = (row.long("type") ?: 0L).toInt(),
                keywords = row.string("keywords"),
                username = row.string("username"),
                uid = row.string("uid"),
            )
            ContentValues().apply {
                put("id", row.long("id"))
                put("condition", condition.toJson())
                put("action", if (row.long("category") == LegacyAllowList.toLong()) BlockRule.Action.Allow.ordinal else 0)
            }
        }
        copy(legacy, db, "draft", "drafts", "id", replace = true) { row ->
            val hash = row.string("hash") ?: return@copy null
            ContentValues().apply {
                put("hash", hash)
                put("content", row.string("content").orEmpty())
            }
        }
        copy(legacy, db, "topforum", "top_forums", "id", replace = true) { row ->
            val forumId = row.string("forumid") ?: return@copy null
            ContentValues().apply { put("forum_id", forumId) }
        }
        copy(legacy, db, "searchhistory", "search_history", "timestamp, id", replace = true) { row ->
            val content = row.string("content") ?: return@copy null
            ContentValues().apply {
                put("id", row.long("id"))
                put("content", content)
                put("timestamp", row.long("timestamp") ?: 0L)
            }
        }
        copy(legacy, db, "searchposthistory", "forum_search_history", "timestamp, id", replace = true) { row ->
            val content = row.string("content") ?: return@copy null
            val forumName = row.string("forumname") ?: return@copy null
            ContentValues().apply {
                put("id", row.long("id"))
                put("content", content)
                put("forum_name", forumName)
                put("timestamp", row.long("timestamp") ?: 0L)
            }
        }
    }

    /**
     * Copies each row of [from] into [to], in [orderBy] order. With [replace], a row conflicting
     * with one copied before replaces it; otherwise it is skipped.
     */
    private fun copy(
        legacy: SQLiteDatabase,
        db: SupportSQLiteDatabase,
        from: String,
        to: String,
        orderBy: String,
        replace: Boolean,
        values: (Cursor) -> ContentValues?,
    ) {
        if (!legacy.hasTable(from)) return
        val conflict = if (replace) SQLiteDatabase.CONFLICT_REPLACE else SQLiteDatabase.CONFLICT_IGNORE
        var skipped = 0
        try {
            legacy.rawQuery("SELECT * FROM $from ORDER BY $orderBy", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val row = runCatching { values(cursor) }.getOrNull()
                    if (row == null || runCatching { db.insert(to, conflict, row) }.isFailure) skipped++
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Can't read $from", e)
        }
        if (skipped > 0) Log.w(TAG, "Skipped $skipped rows of $from")
    }

    private fun repointCurrentAccount(legacy: SQLiteDatabase, db: SupportSQLiteDatabase) {
        val current = accountPrefs.getInt(KEY_CURRENT_ACCOUNT, -1).takeIf { it != -1 } ?: return
        val kept = db.query("SELECT id FROM accounts WHERE id = ?", arrayOf(current)).use { it.moveToFirst() }
        if (kept) return
        val uid = runCatching {
            legacy.rawQuery("SELECT uid FROM account WHERE id = ?", arrayOf(current.toString())).use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: return
        val id = db.query("SELECT id FROM accounts WHERE uid = ?", arrayOf(uid)).use {
            if (it.moveToFirst()) it.getInt(0) else null
        } ?: return
        accountPrefs.edit().putInt(KEY_CURRENT_ACCOUNT, id).apply()
    }

    private fun SQLiteDatabase.hasTable(name: String): Boolean =
        rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name)).use { it.moveToFirst() }

    private fun Cursor.string(column: String): String? =
        getColumnIndex(column).takeIf { it != -1 && !isNull(it) }?.let { getString(it) }

    private fun Cursor.long(column: String): Long? =
        getColumnIndex(column).takeIf { it != -1 && !isNull(it) }?.let { getLong(it) }

    companion object {
        private const val TAG = "LegacyDatabaseImport"

        /** The file LitePal kept its tables in. */
        const val LEGACY_NAME = "tblite.db"

        /** The preference holding the current account's row id, in the `accountData` preferences. */
        const val KEY_CURRENT_ACCOUNT = "now"
    }
}
