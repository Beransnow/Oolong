package oolong.client.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import oolong.client.database.AppDatabase
import oolong.client.database.HistoryEntity
import oolong.client.model.FollowedForum
import oolong.client.model.VisitedForum
import oolong.core.network.forum.ForumGuideResponse
import oolong.core.network.forum.LikeRemoteDataSource
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class HomeRepositoryTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        AppDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val likeRemote = mockk<LikeRemoteDataSource>(relaxed = true)
    private val repository = HomeRepository(
        likeRemote,
        database.topForumDao(),
        database.historyDao(),
        object : HistoryOwner {
            override val uid = MutableStateFlow("")
        },
        Dispatchers.Unconfined,
    )

    @After
    fun tearDown() = database.close()

    private fun forum(id: Long, signed: Boolean = false) =
        ForumGuideResponse.LikeForum(forumId = "$id", forumName = "吧$id", levelId = "3", isSign = if (signed) "1" else "0", avatar = "a$id")

    @Test
    fun `loads every page of the followed forums`() = runBlocking {
        coEvery { likeRemote.forumGuide(1, any()) } returns ForumGuideResponse(listOf(forum(1, signed = true), forum(2)), "true")
        coEvery { likeRemote.forumGuide(2, any()) } returns ForumGuideResponse(listOf(forum(2), forum(3)), "false")

        val forums = repository.followedForums()

        assertEquals(listOf(1L, 2L, 3L), forums.map { it.id })
        assertEquals(FollowedForum(1, "吧1", "a1", level = 3, signedIn = true), forums.first())
    }

    @Test
    fun `pinning follows the pinned ids`() = runBlocking {
        repository.pin(52)
        repository.pin(53)
        repository.unpin(52)

        assertEquals(setOf(53L), repository.pinnedForumIds.first())
    }

    @Test
    fun `unfollowing a forum also unpins it`() = runBlocking {
        val forum = FollowedForum(52, "吧名", "", level = 1, signedIn = false)
        repository.pin(52)

        repository.unfollow(forum)

        coVerify { likeRemote.unfavolike(52, "吧名", null) }
        assertEquals(emptySet(), repository.pinnedForumIds.first())
    }

    @Test
    fun `visited forums come from the forum history, latest first`() = runBlocking {
        database.historyDao().visit(HistoryEntity(type = HistoryEntity.TYPE_FORUM, data = "旧吧", title = "旧吧吧"), timestamp = 1)
        database.historyDao().visit(HistoryEntity(type = HistoryEntity.TYPE_THREAD, data = "9000", title = "帖子"), timestamp = 2)
        database.historyDao().visit(HistoryEntity(type = HistoryEntity.TYPE_FORUM, data = "新吧", title = "新吧吧", avatar = "a"), timestamp = 3)

        assertEquals(
            listOf(VisitedForum("新吧", "新吧吧", "a"), VisitedForum("旧吧", "旧吧吧", "")),
            repository.visitedForums.first(),
        )
    }
}
