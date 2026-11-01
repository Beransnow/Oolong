package com.huanchengfly.tieba.post.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class LegacyDatabaseImportTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("accountData", Context.MODE_PRIVATE)
    private val legacyFile by lazy { File(folder.root, "tblite.db") }
    private var database: AppDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        prefs.edit().clear().commit()
    }

    /** The old database as the last LitePal models create it. */
    private fun legacy(vararg statements: String, schema: List<String> = CURRENT_SCHEMA) {
        SQLiteDatabase.openOrCreateDatabase(legacyFile, null).use { db ->
            (schema + statements).forEach { db.execSQL(it) }
        }
    }

    private fun open(): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(LegacyDatabaseImport(legacyFile, prefs))
            .build()
            .also { database = it }

    @Test
    fun `copies every table`() = runBlocking {
        legacy(
            """INSERT INTO account (id, uid, name, bduss, tbs, portrait, stoken, cookie, nameshow, loadsuccess, uuid, zid, fansnum)
               VALUES (3, '42', 'name', 'bduss', 'tbs', 'portrait', 'stoken', 'cookie', '昵称', 1, 'uuid', 'zid', '12')""",
            "INSERT INTO history (id, type, data, title, timestamp, count, extras, avatar, username) VALUES (5, 1, '9000', '帖子', 100, 2, '{}', 'a', 'u')",
            "INSERT INTO block (id, category, type, keywords, username, uid) VALUES (7, 10, 0, '[\"广告\"]', NULL, NULL)",
            "INSERT INTO draft (id, hash, content) VALUES (1, 'h', '草稿')",
            "INSERT INTO topforum (id, forumid) VALUES (1, '52')",
            "INSERT INTO searchhistory (id, content, timestamp) VALUES (1, '关键词', 10)",
            "INSERT INTO searchposthistory (id, content, forumname, timestamp) VALUES (1, '吧内', '吧名', 20)",
        )
        prefs.edit().putInt("now", 3).commit()
        val db = open()

        val account = db.accountDao().getAll().single()
        assertEquals(3, account.id)
        assertEquals("42", account.uid)
        assertEquals("bduss", account.bduss)
        assertEquals("stoken", account.stoken)
        assertEquals("昵称", account.nameShow)
        assertEquals("12", account.fansNum)
        assertTrue(account.loadSuccess)
        assertEquals("zid", account.zid)
        assertEquals(3, prefs.getInt("now", -1))

        assertEquals(
            HistoryEntity(5, type = 1, data = "9000", title = "帖子", timestamp = 100, count = 2, extras = "{}", avatar = "a", username = "u"),
            db.historyDao().page(type = 1, limit = 10, offset = 0).single(),
        )
        assertEquals(BlockEntity(7, category = 10, type = 0, keywords = "[\"广告\"]"), db.blockDao().all().first().single())
        assertEquals("草稿", db.draftDao().content("h"))
        assertEquals(listOf("52"), db.topForumDao().forumIds())
        assertEquals(listOf("关键词"), db.searchHistoryDao().all().map { it.content })
        assertEquals(listOf("吧名" to "吧内"), db.searchHistoryDao().allInForums().map { it.forumName to it.content })
    }

    @Test
    fun `an old schema with missing tables and columns is read as empty`() = runBlocking {
        legacy(
            "INSERT INTO account (id, uid, name, bduss) VALUES (1, '42', 'name', 'bduss')",
            schema = listOf("CREATE TABLE account (id integer primary key autoincrement, bduss text, name text, uid text)"),
        )
        val db = open()

        val account = db.accountDao().getAll().single()
        assertEquals("bduss", account.bduss)
        assertEquals("", account.stoken)
        assertNull(account.zid)
        assertTrue(db.historyDao().page(type = 1, limit = 10, offset = 0).isEmpty())
    }

    @Test
    fun `of accounts with the same uid the newest is kept and becomes current`() = runBlocking {
        legacy(
            "INSERT INTO account (id, uid, bduss) VALUES (1, '42', 'old')",
            "INSERT INTO account (id, uid, bduss) VALUES (2, '42', 'new')",
            "INSERT INTO account (id, uid, bduss) VALUES (3, '', 'no uid')",
        )
        prefs.edit().putInt("now", 1).commit()
        val db = open()

        assertEquals(listOf(2 to "new"), db.accountDao().getAll().map { it.id to it.bduss })
        assertEquals(2, prefs.getInt("now", -1))
    }

    @Test
    fun `history entries are told apart by type and the latest visit wins`() = runBlocking {
        legacy(
            "INSERT INTO history (id, type, data, title, timestamp) VALUES (1, 1, '123', '帖子', 100)",
            "INSERT INTO history (id, type, data, title, timestamp) VALUES (2, 2, '123', '吧', 200)",
            "INSERT INTO history (id, type, data, title, timestamp) VALUES (3, 1, '123', '帖子新标题', 300)",
            "INSERT INTO history (id, type, data, title, timestamp) VALUES (4, 1, NULL, '坏的', 400)",
        )
        val db = open()

        assertEquals(listOf("帖子新标题"), db.historyDao().page(type = 1, limit = 10, offset = 0).map { it.title })
        assertEquals(listOf("吧"), db.historyDao().page(type = 2, limit = 10, offset = 0).map { it.title })
    }

    @Test
    fun `without an old database it starts empty`() = runBlocking {
        val db = open()

        assertTrue(db.accountDao().getAll().isEmpty())
    }

    private companion object {
        val CURRENT_SCHEMA = listOf(
            "CREATE TABLE account (id integer primary key autoincrement,age text, bduss text, birthdayshowstatus text, birthdaytime text, concernnum text, constellation text, cookie text, fansnum text, intro text, loadsuccess integer, name text, nameshow text, portrait text, postnum text, stoken text, sex text, tbage text, tbs text, threadnum text, tiebauid text, uid text, uuid text, zid text)",
            "CREATE TABLE draft (id integer primary key autoincrement,content text, hash text)",
            "CREATE TABLE history (id integer primary key autoincrement,avatar text, count integer, data text, extras text, timestamp integer, title text, type integer, username text)",
            "CREATE TABLE block (id integer primary key autoincrement,category integer, keywords text, type integer, uid text, username text)",
            "CREATE TABLE topforum (id integer primary key autoincrement,forumid text)",
            "CREATE TABLE searchhistory (id integer primary key autoincrement,content text, timestamp integer)",
            "CREATE TABLE searchposthistory (id integer primary key autoincrement,content text, forumname text, timestamp integer)",
        )
    }
}
