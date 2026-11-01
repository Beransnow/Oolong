package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.core.network.thread.ThreadRemoteDataSource
import com.huanchengfly.tieba.core.network.thread.ThreadStoreResponse
import com.huanchengfly.tieba.post.arch.IoDispatcher
import com.huanchengfly.tieba.post.arch.Page
import com.huanchengfly.tieba.post.model.StoredThread
import com.huanchengfly.tieba.post.model.User
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** The threads the user stored to read later. */
class ThreadStoreRepository @Inject constructor(
    private val remote: ThreadRemoteDataSource,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** A page (from 0) of the stored threads; the list ends at the first empty page. */
    suspend fun page(page: Int): Page<Int, StoredThread> = withContext(io) {
        val threads = remote.threadStore(page).storeThread.mapNotNull { it.toStoredThread() }
        Page(threads, next = (page + 1).takeIf { threads.isNotEmpty() })
    }

    suspend fun remove(threadId: Long) = withContext(io) { remote.removeStore(threadId, forumId = null) }
}

private fun ThreadStoreResponse.StoredThread.toStoredThread(): StoredThread? {
    val id = threadId.toLongOrNull() ?: return null
    return StoredThread(
        threadId = id,
        title = title,
        author = author.lzUid?.toLongOrNull()?.let { uid ->
            User(id = uid, name = author.name.orEmpty(), nameShow = author.nameShow.orEmpty(), portrait = author.userPortrait.orEmpty())
        },
        deleted = isDeleted == "1",
        markPostId = markPid.toLongOrNull() ?: 0,
        maxPostId = maxPid.toLongOrNull() ?: 0,
        latestFloor = postNo.toIntOrNull() ?: 0,
        newPostCount = count.toIntOrNull() ?: 0,
    )
}
