package com.huanchengfly.tieba.post.repository

import androidx.compose.runtime.Immutable
import com.huanchengfly.tieba.core.network.agree.AgreeRemoteDataSource
import com.huanchengfly.tieba.core.network.agree.AgreeTarget
import com.huanchengfly.tieba.core.network.post.PostRemoteDataSource
import com.huanchengfly.tieba.core.network.thread.ThreadRemoteDataSource
import com.huanchengfly.tieba.post.api.models.protos.PbContent
import com.huanchengfly.tieba.post.api.models.protos.VideoInfo
import com.huanchengfly.tieba.post.api.models.protos.pbFloor.PbFloorResponseData
import com.huanchengfly.tieba.post.api.models.protos.pbPage.PbPageResponseData
import com.huanchengfly.tieba.post.api.retrofit.exception.TiebaUnknownException
import com.huanchengfly.tieba.post.arch.IoDispatcher
import com.huanchengfly.tieba.post.arch.Page
import com.huanchengfly.tieba.post.model.Agree
import com.huanchengfly.tieba.post.model.BawuType
import com.huanchengfly.tieba.post.model.ForumSummary
import com.huanchengfly.tieba.post.model.Post
import com.huanchengfly.tieba.post.model.SubPost
import com.huanchengfly.tieba.post.model.ThreadDetail
import com.huanchengfly.tieba.post.model.User
import com.huanchengfly.tieba.post.model.Viewer
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import com.huanchengfly.tieba.post.api.models.protos.Agree as ProtoAgree
import com.huanchengfly.tieba.post.api.models.protos.Post as ProtoPost
import com.huanchengfly.tieba.post.api.models.protos.SubPostList as ProtoSubPost
import com.huanchengfly.tieba.post.api.models.protos.User as ProtoUser

enum class ThreadSort(val value: Int) {
    OldestFirst(0),
    NewestFirst(1),
    Hottest(2),
}

/** What the thread page shows: which thread, in which order, and whose posts. */
@Immutable
data class ThreadQuery(
    val threadId: Long,
    val sort: ThreadSort = ThreadSort.OldestFirst,
    val seeLz: Boolean = false,
    val forumId: Long? = null,
    /** Opened from the thread store, which the server is told about. */
    val fromStore: Boolean = false,
)

/** Where a page of a thread starts. */
@Immutable
sealed interface ThreadCursor {
    /**
     * A page by number. Page 0 leaves it to the server, which returns the first page in the
     * thread's order: the oldest posts, or the newest when newest first. (The last-page flag
     * only means something together with the total page count, so it isn't sent.)
     */
    data class Number(val page: Int) : ThreadCursor

    /**
     * The posts from [postId] on: the page containing it when oldest first, the posts older than
     * it when newest first.
     */
    data class Anchor(val postId: Long) : ThreadCursor

    /**
     * The posts before [postId], the first one loaded: the previous page when oldest first, the
     * newer posts when newest first. [page] is the page number to send along, 0 for none.
     */
    data class Before(val postId: Long, val page: Int) : ThreadCursor

    /** Posts newer than [postId], the last one loaded, once the end has been reached. */
    data class After(val postId: Long) : ThreadCursor

    companion object {
        /**
         * Posts newer than [newestPostId], the newest loaded when newest first, with the page
         * after [maxPageSeen], the highest page loaded so far, while it isn't [lastPage].
         */
        fun newerThan(newestPostId: Long, maxPageSeen: Int, lastPage: Int): ThreadCursor =
            Before(newestPostId, page = if (maxPageSeen < lastPage) maxPageSeen + 1 else 0)

        /** Where a thread opened at [postId], or from its start when 0, begins. */
        fun first(postId: Long = 0): ThreadCursor = if (postId != 0L) Anchor(postId) else Number(0)
    }
}

@Immutable
data class ThreadPage(
    val thread: ThreadDetail,
    /** Shown above the replies, and never part of [posts]. */
    val firstPost: Post?,
    val posts: Page<ThreadCursor, Post>,
    val currentPage: Int,
    /** The number of pages as shown to the user. */
    val totalPage: Int,
    /** The last page number as the server counts pages, for loading newer posts newest first. */
    val lastPage: Int,
    val viewer: Viewer,
    /** The tbs to send with actions on this thread, or null to send the account's. */
    val tbs: String?,
)

/** Where a page of the replies inside a post starts. */
@Immutable
data class FloorCursor(
    val page: Int,
    /** A reply to load the page around, which the server then picks; 0 for none. */
    val subPostId: Long = 0,
)

/** A post with a page of the replies inside it. */
@Immutable
data class FloorPage(
    val post: Post,
    val threadAuthorId: Long,
    val forum: ForumSummary,
    val subPosts: Page<FloorCursor, SubPost>,
    /** How many replies the post has in all. */
    val totalCount: Int,
    /** The tbs to send with actions on these replies, or null to send the account's. */
    val tbs: String?,
)

@Singleton
class ThreadRepository @Inject constructor(
    private val remote: ThreadRemoteDataSource,
    private val agreeRemote: AgreeRemoteDataSource,
    private val postRemote: PostRemoteDataSource,
    private val emoticons: EmoticonRegistry,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun page(query: ThreadQuery, cursor: ThreadCursor): ThreadPage = withContext(io) {
        val data = remote.page(
            threadId = query.threadId,
            page = when (cursor) {
                is ThreadCursor.Number -> cursor.page
                is ThreadCursor.Before -> cursor.page
                is ThreadCursor.Anchor, is ThreadCursor.After -> 0
            },
            postId = when (cursor) {
                is ThreadCursor.Number -> 0
                is ThreadCursor.Anchor -> cursor.postId
                is ThreadCursor.Before -> cursor.postId
                is ThreadCursor.After -> cursor.postId
            },
            sortType = query.sort.value,
            seeLz = query.seeLz,
            back = cursor is ThreadCursor.Before,
            lastPostId = (cursor as? ThreadCursor.After)?.postId ?: 0,
            forumId = query.forumId ?: 0,
            stType = if (query.fromStore) "store_thread" else "",
            mark = if (query.fromStore) 1 else 0,
        )
        data.toThreadPage(query.sort, emoticons)
    }

    /** A page of the replies inside post [postId] of thread [threadId]. */
    suspend fun floor(threadId: Long, postId: Long, forumId: Long, cursor: FloorCursor): FloorPage =
        withContext(io) {
            remote.floor(threadId, postId, forumId, cursor.page, cursor.subPostId).toFloorPage(emoticons)
        }

    suspend fun agreeSubPost(threadId: Long, forumId: Long, subPostId: Long, agree: Boolean) = withContext(io) {
        agreeRemote.opAgree(AgreeTarget.SubPost(threadId, subPostId), forumId, undo = !agree)
        Unit
    }

    /** Deletes a post, or with [isSubPost] a reply inside one, from the sub-post page. */
    suspend fun deletePost(
        forum: ForumSummary,
        threadId: Long,
        postId: Long,
        ownPost: Boolean,
        isSubPost: Boolean,
        tbs: String?,
    ) = withContext(io) {
        remote.deletePost(forum.id, forum.name, threadId, postId, ownPost, isSubPost = isSubPost, tbs = tbs)
    }

    suspend fun vote(thread: ThreadDetail, optionIds: Collection<Int>) = withContext(io) {
        postRemote.addPollPost(thread.id, thread.forum.id, optionIds.toList())
    }

    suspend fun agreePost(thread: ThreadDetail, postId: Long, agree: Boolean) =
        agreePost(thread.id, thread.forum.id, postId, agree)

    suspend fun agreePost(threadId: Long, forumId: Long, postId: Long, agree: Boolean) = withContext(io) {
        agreeRemote.opAgree(AgreeTarget.Post(threadId, postId), forumId, undo = !agree)
        Unit
    }

    suspend fun addStore(threadId: Long, markPostId: Long) = withContext(io) {
        remote.addStore(threadId, markPostId)
    }

    suspend fun removeStore(threadId: Long, forumId: Long, tbs: String?) = withContext(io) {
        remote.removeStore(threadId, forumId, tbs)
    }

    suspend fun deletePost(
        thread: ThreadDetail,
        postId: Long,
        ownPost: Boolean,
        tbs: String?,
    ) = withContext(io) {
        remote.deletePost(thread.forum.id, thread.forum.name, thread.id, postId, ownPost, tbs = tbs)
    }

    suspend fun deleteThread(thread: ThreadDetail, ownThread: Boolean, tbs: String?) =
        withContext(io) {
            remote.deleteThread(thread.forum.id, thread.forum.name, thread.id, ownThread, tbs = tbs)
        }
}

internal fun PbPageResponseData.toThreadPage(sort: ThreadSort, emoticons: EmoticonRegistry): ThreadPage {
    val thread = thread ?: throw TiebaUnknownException
    val threadAuthor = thread.author ?: throw TiebaUnknownException
    val forum = forum ?: throw TiebaUnknownException
    val page = page ?: throw TiebaUnknownException
    val anti = anti ?: throw TiebaUnknownException
    val mapper = PostMapper(user_list, emoticons)
    val firstPost = post_list.firstOrNull { it.floor == 1 }?.let(mapper::post)
        ?: first_floor_post?.let { post ->
            mapper.post(
                post.copy(author = post.author ?: threadAuthor, author_id = threadAuthor.id),
                content = post.content.withPlayableVideo(thread.videoInfo),
            )
        }
    // Floor 0 holds adverts dressed as replies
    val posts = post_list.filter { it.floor > 1 }.map(mapper::post)
    // A page of only adverts is not the end of the thread
    val hasReplies = post_list.any { it.floor != 1 }
    val hasMore = page.has_more != 0 && hasReplies
    val hasPrevious = page.has_prev != 0 && hasReplies

    return ThreadPage(
        thread = ThreadDetail(
            summary = thread.toThreadSummary(emoticons).let { summary ->
                summary.copy(
                    // The page's own thread may leave out what lists fill in
                    id = thread.id,
                    title = thread.title,
                    author = threadAuthor.toUser(),
                    forum = ForumSummary(id = forum.id, name = forum.name, avatar = forum.avatar),
                    agree = thread.agree.toAgree(),
                    firstPostId = thread.firstPostId.takeIf { it != 0L } ?: firstPost?.id ?: 0L,
                    firstPost = firstPost?.content ?: summary.firstPost,
                )
            },
            storeMarkPostId = if (thread.collectStatus != 0) thread.collectMarkPid.toLongOrNull() ?: 0L else null,
            poll = thread.poll_info?.toPoll(),
        ),
        firstPost = firstPost,
        posts = Page(
            items = posts,
            next = when {
                !hasMore -> null
                sort == ThreadSort.NewestFirst -> ThreadCursor.Anchor(posts.last().id)
                else -> ThreadCursor.Number(page.current_page + 1)
            },
            prev = when {
                posts.isEmpty() -> null
                // New replies can arrive at any time, so there is always something to look for
                // Posts newer than the newest loaded, with the page after this one while there is one
                sort == ThreadSort.NewestFirst ->
                    ThreadCursor.newerThan(posts.first().id, page.current_page, page.total_page)
                !hasPrevious -> null
                else -> ThreadCursor.Before(posts.first().id, page.current_page - 1)
            },
        ),
        currentPage = page.current_page,
        totalPage = page.new_total_page,
        lastPage = page.total_page,
        viewer = Viewer(user?.takeIf { it.is_login == 1 }?.toUser()),
        tbs = anti.tbs.ifEmpty { null },
    )
}

internal fun PbFloorResponseData.toFloorPage(emoticons: EmoticonRegistry): FloorPage {
    val post = post ?: throw TiebaUnknownException
    val page = page ?: throw TiebaUnknownException
    val forum = forum ?: throw TiebaUnknownException
    val mapper = PostMapper(emptyList(), emoticons)
    val subPosts = subpost_list.map(mapper::subPost)
    return FloorPage(
        post = mapper.post(post),
        threadAuthorId = thread?.author?.id ?: 0L,
        forum = ForumSummary(id = forum.id, name = forum.name, avatar = forum.avatar),
        subPosts = Page(
            items = subPosts,
            next = FloorCursor(page.current_page + 1)
                .takeIf { page.current_page < page.total_page && subPosts.isNotEmpty() },
            prev = FloorCursor(page.current_page - 1).takeIf { page.current_page > 1 && subPosts.isNotEmpty() },
        ),
        totalCount = page.total_count,
        tbs = anti?.tbs?.ifEmpty { null },
    )
}

/** Maps posts and their replies, taking authors missing from them from [users]. */
internal class PostMapper(users: List<ProtoUser>, private val emoticons: EmoticonRegistry) {
    private val usersById = users.associateBy { it.id }

    private fun author(author: ProtoUser?, authorId: Long): User {
        val id = authorId.takeIf { it != 0L } ?: author?.id ?: 0L
        return (author ?: usersById[id])?.toUser() ?: User(id = id, name = "", nameShow = "", portrait = "")
    }

    fun subPost(subPost: ProtoSubPost) = SubPost(
        id = subPost.id,
        author = author(subPost.author, subPost.author_id),
        content = subPost.content.toPostContent(emoticons),
        time = subPost.time.toLong(),
        agree = subPost.agree.toAgree(),
    )

    fun post(post: ProtoPost) = post(post, post.content)

    fun post(post: ProtoPost, content: List<PbContent>) = Post(
        id = post.id,
        floor = post.floor,
        title = if (post.floor <= 1 && post.is_ntitle != 1) post.title else "",
        time = post.time.toLong(),
        author = author(post.author, post.author_id),
        content = content.toPostContent(emoticons),
        agree = post.agree.toAgree(),
        subPosts = post.sub_post_list?.sub_post_list.orEmpty().map(::subPost).toImmutableList(),
        subPostCount = post.sub_post_number,
    )
}

internal fun ProtoUser.toUser() = User(
    id = id,
    name = name,
    nameShow = nameShow,
    portrait = portrait,
    level = level_id,
    bawuType = when {
        is_bawu != 1 -> null
        bawu_type == "manager" -> BawuType.Manager
        else -> BawuType.Assist
    },
    ipLocation = ip_address,
)

private fun ProtoAgree?.toAgree() = Agree(count = this?.diffAgreeNum ?: 0L, agreed = this?.hasAgree == 1)

/**
 * When the page is requested from a specific post, first_floor_post links the raw upload of the
 * thread video, which can't be played; only thread.video_info carries the transcoded, signed URL.
 */
internal fun List<PbContent>.withPlayableVideo(videoInfo: VideoInfo?): List<PbContent> {
    if (videoInfo == null || videoInfo.videoUrl.isBlank() || videoInfo.videoMD5.isBlank()) return this
    return map {
        if (it.type == 5 && it.link != videoInfo.videoUrl && it.link.contains(videoInfo.videoMD5)) {
            it.copy(link = videoInfo.videoUrl, src = videoInfo.thumbnailUrl.ifBlank { it.src })
        } else {
            it
        }
    }
}
