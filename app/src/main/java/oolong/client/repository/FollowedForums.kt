package oolong.client.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import oolong.client.arch.IoDispatcher
import oolong.client.arch.query.QueryClient
import oolong.client.model.FollowedForum
import oolong.core.network.forum.LikeRemoteDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes

/**
 * The forums the account follows, kept for every page that shows them. Following, unfollowing or
 * signing in to a forum anywhere calls [changed], so a page showing them loads them again at once.
 */
@Singleton
class FollowedForums @Inject constructor(
    private val likeRemote: LikeRemoteDataSource,
    @IoDispatcher private val io: CoroutineDispatcher,
    client: QueryClient,
) {
    /** By the account's uid; the request is always the logged-in account's. */
    val query = client.query<String, List<FollowedForum>>(name = "followed forums", staleTime = 1.minutes) { load() }

    /** Marks the followed forums to load again: those shown now load at once, the rest when next shown. */
    fun changed() = query.invalidate()

    private suspend fun load(): List<FollowedForum> = withContext(io) {
        val forums = mutableListOf<FollowedForum>()
        var page = 1
        do {
            val response = likeRemote.forumGuide(page++)
            val pageForums = response.likeForum.orEmpty().mapNotNull { forum ->
                FollowedForum(
                    id = forum.forumId.toLongOrNull() ?: return@mapNotNull null,
                    name = forum.forumName,
                    avatar = forum.avatar.orEmpty(),
                    level = forum.levelId?.toIntOrNull() ?: 0,
                    signedIn = forum.isSign == "1",
                )
            }
            forums += pageForums
        } while (response.hasMore && pageForums.isNotEmpty())
        forums.distinctBy { it.id }
    }
}
