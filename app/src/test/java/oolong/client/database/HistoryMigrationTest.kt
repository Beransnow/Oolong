package oolong.client.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class HistoryMigrationTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("accountData", Context.MODE_PRIVATE)
    private var database: AppDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        prefs.edit().clear().commit()
    }

    private fun migrate(vararg statements: String): AppDatabase {
        val file = folder.newFile("app.db").also { it.delete() }
        createDatabase(file, 4, *statements)
        return Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
            .addMigrations(migration4To5(prefs), migration5To6(prefs))
            .build()
            .also { database = it }
    }

    @Test
    fun `the history so far becomes the current account's`() = runBlocking {
        prefs.edit().putInt("now", 3).commit()
        val db = migrate(
            "INSERT INTO accounts (id, uid, name, bduss, tbs, portrait, stoken, cookie, load_success) VALUES (3, '42', 'n', 'b', 't', 'p', 's', 'c', 1)",
            "INSERT INTO history (id, type, data, title, timestamp, count) VALUES (1, 2, '9000', '帖子', 100, 1)",
        )

        assertEquals(listOf("帖子"), db.historyDao().page(owner = "42", type = 2, limit = 10, offset = 0).map { it.title })
    }

    @Test
    fun `logged out, the history so far stays the logged-out one`() = runBlocking {
        val db = migrate("INSERT INTO history (id, type, data, title, timestamp, count) VALUES (1, 2, '9000', '帖子', 100, 1)")

        assertEquals(listOf("帖子"), db.historyDao().page(owner = "", type = 2, limit = 10, offset = 0).map { it.title })
    }

    @Test
    fun `the searches so far become the current account's too`() = runBlocking {
        prefs.edit().putInt("now", 3).commit()
        val db = migrate(
            "INSERT INTO accounts (id, uid, name, bduss, tbs, portrait, stoken, cookie, load_success) VALUES (3, '42', 'n', 'b', 't', 'p', 's', 'c', 1)",
            "INSERT INTO search_history (id, content, timestamp) VALUES (1, '关键词', 10)",
            "INSERT INTO forum_search_history (id, content, forum_name, timestamp) VALUES (1, '吧内', '吧名', 20)",
        )

        assertEquals(listOf("关键词"), db.searchHistoryDao().all(owner = "42").map { it.content })
        assertEquals(listOf("42"), db.searchHistoryDao().allInForums().map { it.owner })
    }
}
