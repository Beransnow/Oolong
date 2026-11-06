package oolong.client.repository

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import oolong.client.database.SearchHistoryDao
import oolong.client.model.ForumSearchOrder
import oolong.client.model.QuotedPost
import oolong.client.model.SearchAuthor
import oolong.client.model.SearchOrder
import oolong.client.model.SearchedForum
import oolong.core.network.search.SearchForumsResponse
import oolong.core.network.search.SearchRemoteDataSource
import oolong.core.network.search.SearchSort
import oolong.core.network.search.SearchThreadsResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchRepositoryTest {
    private val remote = mockk<SearchRemoteDataSource>()
    private val repository = SearchRepository(
        remote,
        mockk<SearchHistoryDao>(relaxed = true),
        object : HistoryOwner {
            override val uid = MutableStateFlow("")
        },
        Dispatchers.Unconfined,
    )

    @Test
    fun `forums without a name are left out and the shown name falls back to the name`() = runTest {
        coEvery { remote.forums("k") } returns SearchForumsResponse.Data(
            exactMatch = SearchForumsResponse.Forum(forumId = 52, forumName = "测试", forumNameShow = "", slogan = "标语"),
            fuzzyMatch = JsonArray(
                listOf(
                    buildJsonObject {
                        put("forum_id", 1)
                        put("forum_name", "a")
                    },
                    buildJsonObject { put("forum_id", 2) },
                )
            ),
        )

        val matches = repository.forums("k")

        assertEquals(SearchedForum(52, "测试", "测试", "", "标语"), matches.exact)
        assertEquals(listOf("a"), matches.fuzzy.map { it.name })
    }

    @Test
    fun `maps a matching reply with the thread and post it refers to`() = runTest {
        coEvery { remote.threads("k", 1, SearchSort.OLDEST) } returns SearchThreadsResponse.Data(
            hasMore = 1,
            postList = listOf(
                SearchThreadsResponse.Post(
                    tid = "9", pid = "10", cid = "11", title = "t", content = "c", time = "1700000000",
                    postNum = "1.2万", likeNum = "3", shareNum = "4W", forumName = "测试",
                    user = SearchThreadsResponse.User(userName = "u", showNickname = "昵称", userId = "7", portrait = "p"),
                    forumInfo = SearchThreadsResponse.ForumInfo("测试", "avatar"),
                    media = listOf(
                        SearchThreadsResponse.Media(type = "pic", bigPic = "big", smallPic = "small"),
                        SearchThreadsResponse.Media(type = "pic", smallPic = "small2"),
                        SearchThreadsResponse.Media(type = "flash"),
                    ),
                    mainPost = SearchThreadsResponse.MainPost(title = "主题", tid = 9, user = SearchThreadsResponse.User(userName = "op")),
                    postInfo = SearchThreadsResponse.PostInfo(tid = 9, pid = 5, content = "引用", user = SearchThreadsResponse.User(userId = "8")),
                ),
            ),
        )

        val page = repository.threads("k", SearchOrder.Oldest, page = 1)

        val post = page.items.single()
        assertEquals(2, page.next)
        assertEquals(11L, post.subPostId)
        assertEquals(1_700_000_000L, post.time)
        assertEquals(SearchAuthor(7, "u", "昵称", "p"), post.author)
        assertEquals(12_000, post.replyCount)
        assertEquals(40_000L, post.shareCount)
        assertEquals(listOf("big", "small2"), post.pictures)
        assertEquals(3, post.mediaCount)
        assertEquals(QuotedPost(9, 0, "主题", "", SearchAuthor(0, "op", "", "")), post.thread)
        assertEquals(5L, post.quoted?.postId)
    }

    @Test
    fun `a page without more ends the list`() = runTest {
        coEvery { remote.threadsInForum("k", "测试", 52, 3, SearchSort.IN_FORUM_RELEVANT, true) } returns
            SearchThreadsResponse.Data(hasMore = 0)

        val page = repository.threadsInForum("k", "测试", 52, ForumSearchOrder.Relevant, onlyThreads = true, page = 3)

        assertNull(page.next)
    }

    @Test
    fun `counts may be abbreviated`() {
        assertEquals(10_000L, "1W".parseCount())
        assertEquals(12_000L, "1.2万".parseCount())
        assertEquals(1_500L, "1.5k".parseCount())
        assertEquals(42L, " 42 ".parseCount())
        assertEquals(0L, "".parseCount())
    }
}
