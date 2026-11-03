package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.core.network.agree.AgreeRemoteDataSource
import com.huanchengfly.tieba.core.network.agree.AgreeTarget
import com.huanchengfly.tieba.post.arch.IoDispatcher
import com.huanchengfly.tieba.post.arch.query.EntityType
import com.huanchengfly.tieba.post.arch.query.Normalizer
import com.huanchengfly.tieba.post.arch.query.QueryClient
import com.huanchengfly.tieba.post.model.Post
import com.huanchengfly.tieba.post.model.SubPost
import com.huanchengfly.tieba.post.model.ThreadSummary
import com.huanchengfly.tieba.post.model.withAgreed
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Threads, one copy each in the [QueryClient] wherever they were loaded. */
val Threads = EntityType<Long, ThreadSummary>("thread", id = { it.id }, merge = { older, newer -> newer.filledFrom(older) })

/**
 * [this] newer copy with what it leaves out taken from [older]: the thread page leaves out what
 * only lists show, such as the abstract and pictures, and a user's posts leave out the first floor.
 * What changes over time, such as counts and likes, is always the newer copy's.
 */
private fun ThreadSummary.filledFrom(older: ThreadSummary) = copy(
    forum = forum.copy(
        id = forum.id.takeIf { it != 0L } ?: older.forum.id,
        name = forum.name.ifEmpty { older.forum.name },
        avatar = forum.avatar.ifEmpty { older.forum.avatar },
    ),
    title = title.ifEmpty { older.title },
    author = author.takeIf { it.portrait.isNotEmpty() } ?: older.author,
    createTime = createTime.takeIf { it != 0L } ?: older.createTime,
    listTime = listTime.takeIf { it != 0L } ?: older.listTime,
    abstract = abstract.ifEmpty { older.abstract },
    firstPostId = firstPostId.takeIf { it != 0L } ?: older.firstPostId,
    firstPost = firstPost.ifEmpty { older.firstPost },
    images = images.ifEmpty { older.images },
    video = video ?: older.video,
    shared = shared ?: older.shared,
    tabName = tabName.ifEmpty { older.tabName },
    hotNum = hotNum.takeIf { it != 0L } ?: older.hotNum,
    isTop = isTop ?: older.isTop,
)

/** Posts, one copy each in the [QueryClient] wherever they were loaded. */
val Posts = EntityType<Long, Post>("post", id = { it.id }, merge = { older, newer -> newer.filledFrom(older) })

/** Replies inside posts, one copy each in the [QueryClient] wherever they were loaded. */
val SubPosts = EntityType<Long, SubPost>("sub post", id = { it.id })

/** The page of the replies inside a post leaves out the few the thread page shows in it. */
private fun Post.filledFrom(older: Post) = copy(
    subPosts = subPosts.ifEmpty { older.subPosts },
    subPostCount = subPostCount.takeIf { it != 0 } ?: older.subPostCount,
)

/** Marks [post] and the replies shown inside it as entities: the post first, then its replies as it has them. */
fun Normalizer.post(post: Post): Post =
    Posts(post).let { latest -> latest.copy(subPosts = latest.subPosts.map { SubPosts(it) }.toImmutableList()) }

/** What the user does to a thread or its posts from wherever they show, seen at once everywhere they do. */
@Singleton
class ThreadActions @Inject constructor(
    private val client: QueryClient,
    private val agreeRemote: AgreeRemoteDataSource,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** Agrees with [thread], or takes it back when [agree] is false; rolled back if it fails. */
    suspend fun agree(thread: ThreadSummary, agree: Boolean) {
        client.mutate({ update(Threads, thread.id) { it.withAgreed(agree) } }) {
            withContext(io) {
                agreeRemote.opAgree(AgreeTarget.Thread(thread.id, thread.firstPostId), thread.forum.id, undo = !agree)
            }
        }
    }

    /** Agrees with post [postId] of a thread, or takes it back; rolled back if it fails. */
    suspend fun agreePost(threadId: Long, forumId: Long, postId: Long, agree: Boolean) {
        client.mutate({ update(Posts, postId) { it.copy(agree = it.agree.withAgreed(agree)) } }) {
            withContext(io) { agreeRemote.opAgree(AgreeTarget.Post(threadId, postId), forumId, undo = !agree) }
        }
    }

    /** Agrees with reply [subPostId] inside a post, or takes it back; rolled back if it fails. */
    suspend fun agreeSubPost(threadId: Long, forumId: Long, subPostId: Long, agree: Boolean) {
        client.mutate({ update(SubPosts, subPostId) { it.copy(agree = it.agree.withAgreed(agree)) } }) {
            withContext(io) { agreeRemote.opAgree(AgreeTarget.SubPost(threadId, subPostId), forumId, undo = !agree) }
        }
    }
}
