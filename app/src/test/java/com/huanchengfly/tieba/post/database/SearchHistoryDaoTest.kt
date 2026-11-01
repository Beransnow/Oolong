package com.huanchengfly.tieba.post.database

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
        dao.add("a", timestamp = 1)
        dao.add("b", timestamp = 2)
        dao.add("a", timestamp = 3)

        assertEquals(listOf("a", "b"), dao.all().map { it.content })
    }

    @Test
    fun `the same keyword in two forums is kept for each`() = runBlocking {
        dao.addInForum("a", "吧一", timestamp = 1)
        dao.addInForum("a", "吧二", timestamp = 2)
        dao.addInForum("a", "吧一", timestamp = 3)

        assertEquals(listOf("吧一", "吧二"), dao.allInForums().map { it.forumName })
    }
}
