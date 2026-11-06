package oolong.client.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class SearchHistoryDaoTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        AppDatabase::class.java,
    ).build()
    private val dao = database.searchHistoryDao()

    @After
    fun tearDown() = database.close()

    @Test
    fun `searching for a keyword again moves it to the top`() = runBlocking {
        dao.add(owner = "", content = "a", timestamp = 1)
        dao.add(owner = "", content = "b", timestamp = 2)
        dao.add(owner = "", content = "a", timestamp = 3)

        assertEquals(listOf("a", "b"), dao.all(owner = "").map { it.content })
    }

    @Test
    fun `the same keyword in two forums is kept for each`() = runBlocking {
        dao.addInForum(owner = "", content = "a", forumName = "吧一", timestamp = 1)
        dao.addInForum(owner = "", content = "a", forumName = "吧二", timestamp = 2)
        dao.addInForum(owner = "", content = "a", forumName = "吧一", timestamp = 3)

        assertEquals(listOf("吧一", "吧二"), dao.allInForums().map { it.forumName })
    }

    @Test
    fun `a forum shows and clears only its own keywords`() = runBlocking {
        dao.addInForum(owner = "", content = "a", forumName = "吧一", timestamp = 1)
        dao.addInForum(owner = "", content = "b", forumName = "吧二", timestamp = 2)

        assertEquals(listOf("a"), dao.inForumFlow(owner = "", forumName = "吧一").first().map { it.content })
        dao.clearInForum(owner = "", forumName = "吧一")
        assertEquals(listOf("吧二"), dao.allInForums().map { it.forumName })
    }

    @Test
    fun `each account has its own keywords`() = runBlocking {
        dao.add(owner = "1", content = "一号搜的", timestamp = 1)
        dao.add(owner = "2", content = "二号搜的", timestamp = 2)
        dao.addInForum(owner = "2", content = "吧内", forumName = "吧", timestamp = 3)

        assertEquals(listOf("一号搜的"), dao.all(owner = "1").map { it.content })
        assertEquals(emptyList(), dao.inForumFlow(owner = "1", forumName = "吧").first())
    }
}
