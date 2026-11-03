package oolong.client.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import oolong.client.arch.IoDispatcher
import oolong.client.arch.Page
import oolong.client.model.StoredThread
import oolong.client.model.User
import oolong.core.network.thread.ThreadRemoteDataSource
import oolong.core.network.thread.ThreadStoreResponse
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
