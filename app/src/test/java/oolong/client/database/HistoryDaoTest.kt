package oolong.client.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class HistoryDaoTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        AppDatabase::class.java,
    ).build()
    private val dao = database.historyDao()

    @After
    fun tearDown() = database.close()

    @Test
    fun `visiting again updates the entry and counts the visit`() = runBlocking {
        dao.visit(HistoryEntity(type = 2, data = "9000", title = "旧标题", extras = "a"), timestamp = 100)
        dao.visit(HistoryEntity(type = 2, data = "9000", title = "新标题", extras = "b"), timestamp = 200)

        val entry = dao.page(type = 2, limit = 10, offset = 0).single()
        assertEquals("新标题", entry.title)
        assertEquals("b", entry.extras)
        assertEquals(200, entry.timestamp)
        assertEquals(2, entry.count)
    }

    @Test
    fun `a forum and a thread with the same data are separate entries`() = runBlocking {
        dao.visit(HistoryEntity(type = 1, data = "123", title = "吧"), timestamp = 100)
        dao.visit(HistoryEntity(type = 2, data = "123", title = "帖子"), timestamp = 200)

        assertEquals(listOf("吧"), dao.page(type = 1, limit = 10, offset = 0).map { it.title })
        assertEquals(listOf("帖子"), dao.page(type = 2, limit = 10, offset = 0).map { it.title })
    }

    @Test
    fun `pages are most recent first`() = runBlocking {
        (1..5).forEach { dao.visit(HistoryEntity(type = 2, data = "$it"), timestamp = it.toLong()) }

        assertEquals(listOf("5", "4"), dao.page(type = 2, limit = 2, offset = 0).map { it.data })
        assertEquals(listOf("3", "2"), dao.page(type = 2, limit = 2, offset = 2).map { it.data })
    }
}
