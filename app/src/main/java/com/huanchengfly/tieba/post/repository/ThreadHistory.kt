package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.post.arch.ApplicationScope
import com.huanchengfly.tieba.post.database.HistoryDao
import com.huanchengfly.tieba.post.database.HistoryEntity
import com.huanchengfly.tieba.post.model.Post
import com.huanchengfly.tieba.post.model.ThreadDetail
import com.huanchengfly.tieba.post.models.ThreadHistoryInfoBean
import com.huanchengfly.tieba.post.toJson
import com.huanchengfly.tieba.post.utils.StringUtil
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The browsing history of threads, which remembers where in a thread the user was. */
fun interface ThreadHistory {
    fun save(thread: ThreadDetail, seeLz: Boolean, readUpTo: Post?)
}

class DatabaseThreadHistory @Inject constructor(
    private val dao: HistoryDao,
    @ApplicationScope private val scope: CoroutineScope,
) : ThreadHistory {
    override fun save(thread: ThreadDetail, seeLz: Boolean, readUpTo: Post?) {
        val entry = HistoryEntity(
            type = HistoryEntity.TYPE_THREAD,
            data = thread.id.toString(),
            title = thread.title,
            extras = ThreadHistoryInfoBean(
                isSeeLz = seeLz,
                pid = readUpTo?.id?.toString(),
                forumName = thread.forum.name,
                floor = readUpTo?.floor?.toString(),
            ).toJson(),
            avatar = StringUtil.getAvatarUrl(thread.author.portrait),
            username = thread.author.nameShow,
        )
        scope.launch { dao.visit(entry, System.currentTimeMillis()) }
    }
}
