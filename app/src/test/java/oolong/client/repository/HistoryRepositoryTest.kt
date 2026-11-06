package oolong.client.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import oolong.client.database.AppDatabase
import oolong.client.database.HistoryEntity
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class HistoryRepositoryTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val owner = MutableStateFlow("1")
    private val repository = HistoryRepository(
        database.historyDao(),
        object : HistoryOwner {
            override val uid = owner
        },
        Dispatchers.Unconfined,
    )

    @After
    fun tearDown() = database.close()

    private suspend fun visit(id: Long, title: String, author: String, forum: String, owner: String = "1") =
        database.historyDao().visit(
            HistoryEntity(
                owner = owner,
                type = HistoryEntity.TYPE_THREAD,
                data = "$id",
                title = title,
                username = author,
                extras = """{"forumName":"$forum","thumbnail":"https://x/明日"}""",
            ),
            timestamp = id,
        )

    @Test
    fun `a search matches the title, the author or the forum, not the rest`() = runBlocking {
        visit(1, "干员强度", "甲", "其他")
        visit(2, "闲聊", "干员研究员", "其他")
        visit(3, "闲聊", "乙", "明日方舟")
        visit(4, "闲聊", "丙", "其他")

        assertEquals(listOf(2L, 1L), repository.threads(limit = 10, query = "干员").first().map { it.threadId })
        assertEquals(listOf(3L), repository.threads(limit = 10, query = "方舟").first().map { it.threadId })
    }

    @Test
    fun `only the current account's visits show`() = runBlocking {
        visit(1, "一号的", "甲", "吧")
        visit(2, "二号的", "甲", "吧", owner = "2")

        assertEquals(listOf("一号的"), repository.threads(limit = 10).first().map { it.title })
        owner.value = "2"
        assertEquals(listOf("二号的"), repository.threads(limit = 10).first().map { it.title })
    }
}
