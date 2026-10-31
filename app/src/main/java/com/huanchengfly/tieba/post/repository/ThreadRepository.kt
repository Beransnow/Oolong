package com.huanchengfly.tieba.post.repository

import androidx.compose.runtime.Immutable
import com.huanchengfly.tieba.core.network.thread.ThreadRemoteDataSource
import com.huanchengfly.tieba.post.api.models.protos.PbContent
import com.huanchengfly.tieba.post.api.models.protos.VideoInfo
import com.huanchengfly.tieba.post.api.models.protos.pbPage.PbPageResponseData
import com.huanchengfly.tieba.post.api.retrofit.exception.TiebaUnknownException
import com.huanchengfly.tieba.post.arch.Page
import com.huanchengfly.tieba.post.arch.wrapImmutable
import com.huanchengfly.tieba.post.model.Agree
import com.huanchengfly.tieba.post.model.BawuType
import com.huanchengfly.tieba.post.model.ForumSummary
import com.huanchengfly.tieba.post.model.Post
import com.huanchengfly.tieba.post.model.SubPost
import com.huanchengfly.tieba.post.model.ThreadDetail
import com.huanchengfly.tieba.post.model.User
import com.huanchengfly.tieba.post.model.Viewer
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
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
     * A page by number. Page 0 leaves it to the server, which returns the first page; with
     * [last], the server returns the last page, which is the first one when newest first.
     */
    data class Number(val page: Int, val last: Boolean = false) : ThreadCursor

    /**
     * The posts from [postId] on: the page containing it when oldest first, the posts older than
     * it when newest first.
     */
    data class Anchor(val postId: Long) : ThreadCursor

    /** The page before the one starting at [postId], which is page [page]. */
    data class Before(val postId: Long, val page: Int) : ThreadCursor

    /** Posts newer than [postId], the last one loaded, once the end has been reached. */
    data class After(val postId: Long) : ThreadCursor

    companion object {
        /** Where a thread opened at [postId], or from its start when 0, begins. */
        fun first(sort: ThreadSort, postId: Long = 0): ThreadCursor = when {
            postId != 0L -> Anchor(postId)
            sort == ThreadSort.NewestFirst -> Number(1, last = true)
            else -> Number(0)
        }
    }
}

@Immutable
data class ThreadPage(
    val thread: ThreadDetail,
    /** Shown above the replies, and never part of [posts]. */
    val firstPost: Post?,
    val posts: Page<ThreadCursor, Post>,
    val currentPage: Int,
    val totalPage: Int,
    val viewer: Viewer,
    /** The tbs to send with actions on this thread, or null to send the account's. */
    val tbs: String?,
)

@Singleton
class ThreadRepository @Inject constructor(
    private val remote: ThreadRemoteDataSource,
    private val emoticons: EmoticonRegistry,
) {
    suspend fun page(query: ThreadQuery, cursor: ThreadCursor): ThreadPage = withContext(Dispatchers.IO) {
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
            last = cursor is ThreadCursor.Number && cursor.last,
            lastPostId = (cursor as? ThreadCursor.After)?.postId ?: 0,
            forumId = query.forumId ?: 0,
            stType = if (query.fromStore) "store_thread" else "",
            mark = if (query.fromStore) 1 else 0,
        )
        data.toThreadPage(query.sort, emoticons)
    }

    suspend fun addStore(threadId: Long, markPostId: Long) = withContext(Dispatchers.IO) {
        remote.addStore(threadId, markPostId)
    }

    suspend fun removeStore(threadId: Long, forumId: Long, tbs: String?) = withContext(Dispatchers.IO) {
        remote.removeStore(threadId, forumId, tbs)
    }

    suspend fun deletePost(
        thread: ThreadDetail,
        postId: Long,
        ownPost: Boolean,
        tbs: String?,
    ) = withContext(Dispatchers.IO) {
        remote.deletePost(thread.forum.id, thread.forum.name, thread.id, postId, ownPost, tbs = tbs)
    }

    suspend fun deleteThread(thread: ThreadDetail, ownThread: Boolean, tbs: String?) =
        withContext(Dispatchers.IO) {
            remote.deleteThread(thread.forum.id, thread.forum.name, thread.id, ownThread, tbs = tbs)
        }
}

internal fun PbPageResponseData.toThreadPage(sort: ThreadSort, emoticons: EmoticonRegistry): ThreadPage {
    val thread = thread ?: throw TiebaUnknownException
    val threadAuthor = thread.author ?: throw TiebaUnknownException
    val forum = forum ?: throw TiebaUnknownException
    val page = page ?: throw TiebaUnknownException
    val anti = anti ?: throw TiebaUnknownException
    val usersById = user_list.associateBy { it.id }

    fun ProtoUser?.orFromList(id: Long): User =
        (this ?: usersById[id])?.toUser() ?: User(id = id, name = "", nameShow = "", portrait = "")

    fun ProtoSubPost.toSubPost() = SubPost(
        id = id,
        author = author.orFromList(author_id.takeIf { it != 0L } ?: author?.id ?: 0L),
        content = content.toPostContent(emoticons),
        time = time.toLong(),
    )

    fun ProtoPost.toPost(content: List<PbContent> = this.content) = Post(
        id = id,
        floor = floor,
        title = if (floor <= 1 && is_ntitle != 1) title else "",
        time = time.toLong(),
        author = author.orFromList(author_id.takeIf { it != 0L } ?: author?.id ?: 0L),
        content = content.toPostContent(emoticons),
        agree = agree.toAgree(),
        subPosts = sub_post_list?.sub_post_list.orEmpty().map { it.toSubPost() }.toImmutableList(),
        subPostCount = sub_post_number,
    )

    val firstPost = post_list.firstOrNull { it.floor == 1 }?.toPost()
        ?: first_floor_post?.let { post ->
            post.copy(author = post.author ?: threadAuthor, author_id = threadAuthor.id)
                .toPost(content = post.content.withPlayableVideo(thread.videoInfo))
        }
    val posts = post_list.filterNot { it.floor == 1 }.map { it.toPost() }
    val hasMore = page.has_more != 0 && posts.isNotEmpty()
    val hasPrevious = page.has_prev != 0 && posts.isNotEmpty()

    return ThreadPage(
        thread = ThreadDetail(
            id = thread.id,
            title = thread.title,
            author = threadAuthor.toUser(),
            forum = ForumSummary(id = forum.id, name = forum.name, avatar = forum.avatar),
            replyCount = thread.replyNum,
            agree = thread.agree.toAgree(),
            firstPostId = thread.firstPostId.takeIf { it != 0L } ?: firstPost?.id ?: 0L,
            storeMarkPostId = if (thread.collectStatus != 0) thread.collectMarkPid.toLongOrNull() ?: 0L else null,
            origin = thread.origin_thread_info?.takeIf { thread.is_share_thread == 1 }?.wrapImmutable(),
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
                !hasPrevious || sort == ThreadSort.NewestFirst -> null
                else -> ThreadCursor.Before(posts.first().id, page.current_page - 1)
            },
        ),
        currentPage = page.current_page,
        totalPage = page.new_total_page,
        viewer = Viewer(user?.takeIf { it.is_login == 1 }?.toUser()),
        tbs = anti.tbs.ifEmpty { null },
    )
}

private fun ProtoUser.toUser() = User(
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
