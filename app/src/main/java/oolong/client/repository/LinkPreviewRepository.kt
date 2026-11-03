package oolong.client.repository

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import oolong.client.App
import oolong.client.arch.IoDispatcher
import oolong.core.network.forum.ForumRemoteDataSource
import oolong.core.network.thread.ThreadRemoteDataSource
import javax.inject.Inject

/** What a link to a thread shows before it is opened. */
data class ThreadLinkPreview(val title: String, val forumName: String, val replyCount: Int, val authorPortrait: String)

/** What a link to a forum shows before it is opened. */
data class ForumLinkPreview(val slogan: String, val avatar: String)

/** Previews of copied links to threads and forums. */
class LinkPreviewRepository @Inject constructor(
    private val threadRemote: ThreadRemoteDataSource,
    private val forumRemote: ForumRemoteDataSource,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun thread(threadId: Long): ThreadLinkPreview = withContext(io) {
        val data = threadRemote.page(threadId, page = 1)
        val thread = checkNotNull(data.thread) { "No thread" }
        // A thread's own author holds little more than the id; the user list has the rest
        val author = data.user_list.find { it.id == thread.author?.id } ?: thread.author
        ThreadLinkPreview(
            title = thread.title,
            forumName = data.forum?.name.orEmpty(),
            replyCount = thread.replyNum,
            authorPortrait = author?.portrait.orEmpty(),
        )
    }

    suspend fun forum(forumName: String): ForumLinkPreview = withContext(io) {
        val forum = checkNotNull(forumRemote.page(forumName, page = 1, loadType = 1, sortType = null).forum) { "No forum" }
        ForumLinkPreview(slogan = forum.slogan, avatar = forum.avatar)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LinkPreviewEntryPoint {
    fun linkPreviewRepository(): LinkPreviewRepository
}

/** For the clipboard link detector, which can't be injected. */
internal val linkPreviewRepository: LinkPreviewRepository
    get() = EntryPointAccessors.fromApplication(App.INSTANCE, LinkPreviewEntryPoint::class.java).linkPreviewRepository()
