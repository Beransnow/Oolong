package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.post.models.ThreadHistoryInfoBean
import com.huanchengfly.tieba.post.models.database.History
import com.huanchengfly.tieba.post.model.Post
import com.huanchengfly.tieba.post.model.ThreadDetail
import com.huanchengfly.tieba.post.toJson
import com.huanchengfly.tieba.post.utils.HistoryUtil
import com.huanchengfly.tieba.post.utils.StringUtil
import javax.inject.Inject

/** The browsing history of threads, which remembers where in a thread the user was. */
fun interface ThreadHistory {
    fun save(thread: ThreadDetail, seeLz: Boolean, readUpTo: Post?)
}

class HistoryUtilThreadHistory @Inject constructor() : ThreadHistory {
    override fun save(thread: ThreadDetail, seeLz: Boolean, readUpTo: Post?) {
        HistoryUtil.saveHistory(
            History(
                title = thread.title,
                data = thread.id.toString(),
                type = HistoryUtil.TYPE_THREAD,
                extras = ThreadHistoryInfoBean(
                    isSeeLz = seeLz,
                    pid = readUpTo?.id?.toString(),
                    forumName = thread.forum.name,
                    floor = readUpTo?.floor?.toString(),
                ).toJson(),
                avatar = StringUtil.getAvatarUrl(thread.author.portrait),
                username = thread.author.nameShow,
            ),
            async = true,
        )
    }
}
