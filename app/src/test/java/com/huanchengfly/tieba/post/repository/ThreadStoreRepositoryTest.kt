package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.core.network.thread.ThreadRemoteDataSource
import com.huanchengfly.tieba.core.network.thread.ThreadStoreResponse
import com.huanchengfly.tieba.post.model.StoredThread
import com.huanchengfly.tieba.post.model.User
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThreadStoreRepositoryTest {
    private val remote = mockk<ThreadRemoteDataSource>(relaxed = true)
    private val repository = ThreadStoreRepository(remote, Dispatchers.Unconfined)

    @Test
    fun `maps stored threads and pages on until a page is empty`() = runTest {
        coEvery { remote.threadStore(0) } returns ThreadStoreResponse(
            storeThread = listOf(
                ThreadStoreResponse.StoredThread(
                    threadId = "9", title = "t", isDeleted = "1", maxPid = "100", markPid = "50", postNo = "30", count = "2",
                    author = ThreadStoreResponse.Author(lzUid = "7", name = "n", nameShow = "昵称", userPortrait = "p"),
                ),
                ThreadStoreResponse.StoredThread(threadId = "bad"),
            )
        )
        coEvery { remote.threadStore(1) } returns ThreadStoreResponse()

        val first = repository.page(0)
        val second = repository.page(1)

        assertEquals(
            listOf(StoredThread(9, "t", User(7, "n", "昵称", "p"), deleted = true, markPostId = 50, maxPostId = 100, latestFloor = 30, newPostCount = 2)),
            first.items,
        )
        assertEquals(1, first.next)
        assertNull(second.next)
    }

    @Test
    fun `removes a thread without its forum`() = runTest {
        repository.remove(9)

        coVerify { remote.removeStore(9, null) }
    }
}
