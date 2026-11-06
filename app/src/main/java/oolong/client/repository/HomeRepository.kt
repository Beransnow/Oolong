package oolong.client.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import oolong.client.arch.IoDispatcher
import oolong.client.database.HistoryDao
import oolong.client.database.HistoryEntity
import oolong.client.database.TopForumDao
import oolong.client.database.TopForumEntity
import oolong.client.model.FollowedForum
import oolong.client.model.VisitedForum
import oolong.core.network.forum.LikeRemoteDataSource
import javax.inject.Inject

/** What the home page lists: the forums the user follows, pins and visited lately. */
class HomeRepository @Inject constructor(
    private val likeRemote: LikeRemoteDataSource,
    private val topForumDao: TopForumDao,
    private val historyDao: HistoryDao,
    private val historyOwner: HistoryOwner,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** Every followed forum, loaded page by page. */
    suspend fun followedForums(): List<FollowedForum> = withContext(io) {
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

    /** The ids of the forums pinned to the top of the home page. */
    val pinnedForumIds: Flow<Set<Long>> =
        topForumDao.forumIdsFlow().map { ids -> ids.mapNotNull { it.toLongOrNull() }.toSet() }

    /** The forums the current account visited, latest first. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val visitedForums: Flow<List<VisitedForum>> = historyOwner.uid.flatMapLatest { uid ->
        historyDao.latest(uid, HistoryEntity.TYPE_FORUM, VISITED_LIMIT).map { entries ->
            entries.map { VisitedForum(name = it.data, title = it.title, avatar = it.avatar.orEmpty()) }
        }
    }

    suspend fun pin(forumId: Long) = withContext(io) { topForumDao.insert(TopForumEntity(forumId.toString())) }

    suspend fun unpin(forumId: Long) = withContext(io) { topForumDao.delete(forumId.toString()) }

    suspend fun unfollow(forum: FollowedForum) = withContext(io) {
        likeRemote.unfavolike(forum.id, forum.name)
        topForumDao.delete(forum.id.toString())
    }

    private companion object {
        /** How many visited forums the home page offers, as many as a page of history holds. */
        const val VISITED_LIMIT = 100
    }
}
