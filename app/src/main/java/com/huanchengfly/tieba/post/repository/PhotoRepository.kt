package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.core.network.thread.ThreadRemoteDataSource
import com.huanchengfly.tieba.post.arch.IoDispatcher
import com.huanchengfly.tieba.post.model.PicturePage
import com.huanchengfly.tieba.post.model.ThreadPicture
import com.huanchengfly.tieba.post.models.LoadPicPageData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** The pictures of a thread, loaded around the one the user is looking at. */
class PhotoRepository @Inject constructor(
    private val remote: ThreadRemoteDataSource,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /**
     * The pictures after [picId], or with [previous] those before it, of the thread [source] names.
     *
     * @param picIndex the position of [picId] among all the thread's pictures, from 1
     */
    suspend fun pictures(source: LoadPicPageData, picId: String, picIndex: Int, previous: Boolean): PicturePage =
        withContext(io) {
            val response = remote.picPage(
                forumId = source.forumId,
                forumName = source.forumName,
                threadId = source.threadId,
                picId = picId,
                picIndex = picIndex,
                objType = source.objType,
                previous = previous,
                seeLz = source.seeLz,
                postId = source.postId,
            )
            PicturePage(
                total = response.picAmount.toIntOrNull() ?: 0,
                pictures = response.picList.map {
                    ThreadPicture(
                        picId = it.img.original.id,
                        originUrl = it.img.original.originalSrc,
                        url = it.img.original.bigCdnSrc.takeIf { _ -> it.showOriginalBtn == "1" },
                        overallIndex = it.overallIndex.toIntOrNull() ?: 0,
                        postId = it.postId?.toLongOrNull(),
                    )
                },
            )
        }
}
