package oolong.client.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import oolong.client.arch.IoDispatcher
import oolong.client.arch.query.QueryState
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
    private val followed: FollowedForums,
    private val topForumDao: TopForumDao,
    private val historyDao: HistoryDao,
    private val historyOwner: HistoryOwner,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** The account whose forums these are, by uid; empty when no one is logged in. */
    val account: Flow<String> get() = historyOwner.uid

    /** The forums account [uid] follows, kept, loaded when stale and again whenever they change. */
    fun followedForums(uid: String): Flow<QueryState<List<FollowedForum>>> = followed.query.observe(uid)

    /** Loads the followed forums of [uid] again, even if fresh, and returns them. */
    suspend fun reloadFollowedForums(uid: String): List<FollowedForum> = followed.query.reload(uid)

    /** Loads the followed forums of [uid] again if they are stale. */
    fun freshenFollowedForums(uid: String) = followed.query.prefetch(uid)

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
        followed.changed()
    }

    private companion object {
        /** How many visited forums the home page offers, as many as a page of history holds. */
        const val VISITED_LIMIT = 100
    }
}
