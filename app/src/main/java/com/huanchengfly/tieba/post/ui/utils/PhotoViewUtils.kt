package com.huanchengfly.tieba.post.ui.utils

import com.huanchengfly.tieba.post.model.ThreadImage
import com.huanchengfly.tieba.post.models.LoadPicPageData
import com.huanchengfly.tieba.post.models.PhotoViewData
import com.huanchengfly.tieba.post.models.PicItem
import com.huanchengfly.tieba.post.utils.ImageUtil
import kotlinx.collections.immutable.toImmutableList

fun getPhotoViewData(
    images: List<ThreadImage>,
    forumId: Long,
    forumName: String,
    threadId: Long,
    index: Int,
): PhotoViewData {
    val image = images[index]
    return PhotoViewData(
        data = LoadPicPageData(
            forumId = forumId,
            forumName = forumName,
            threadId = threadId,
            postId = image.postId,
            seeLz = false,
            objType = "index",
            picId = ImageUtil.getPicId(image.original),
            picIndex = index + 1,
            originUrl = image.original,
        ),
        picItems = images.mapIndexed { i, item ->
            PicItem(
                picId = ImageUtil.getPicId(item.original),
                picIndex = i + 1,
                url = item.large,
                originUrl = item.original,
                showOriginBtn = item.showOriginalButton,
                originSize = item.originalSize,
                postId = item.postId,
            )
        }.toImmutableList(),
        index = index,
    )
}
