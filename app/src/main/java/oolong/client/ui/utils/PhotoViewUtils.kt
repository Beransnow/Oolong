package oolong.client.ui.utils

import kotlinx.collections.immutable.toImmutableList
import oolong.client.model.ThreadImage
import oolong.client.models.LoadPicPageData
import oolong.client.models.PhotoViewData
import oolong.client.models.PicItem
import oolong.client.utils.ImageUtil

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
