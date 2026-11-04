package oolong.client.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import oolong.client.arch.IoDispatcher
import oolong.client.arch.Page
import oolong.client.model.DislikedForum
import oolong.core.network.explore.ExploreRemoteDataSource
import javax.inject.Inject

/** The forums the signed-in user keeps out of their recommendations. */
class DislikedForumRepository @Inject constructor(
    private val remote: ExploreRemoteDataSource,
    private val accounts: AccountRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** Page [page] (from 1) of them. */
    suspend fun forums(page: Int): Page<Int, DislikedForum> = withContext(io) {
        val response = remote.dislikedForums(userId(), page)
        val forums = response.forums.mapNotNull { forum ->
            val id = forum.forumId?.toLongOrNull() ?: return@mapNotNull null
            DislikedForum(id = id, name = forum.forumName.orEmpty(), avatar = forum.avatar.orEmpty(), slogan = forum.slogan.orEmpty())
        }
        Page(forums, next = (page + 1).takeIf { response.hasMore == "1" && forums.isNotEmpty() })
    }

    /** Lets [forum] back into the recommendations. */
    suspend fun cancel(forum: DislikedForum) = withContext(io) { remote.cancelDislikedForum(userId(), forum.id) }

    private fun userId(): Long = checkNotNull(accounts.current.value?.uid?.toLongOrNull()) { "Not signed in" }
}
