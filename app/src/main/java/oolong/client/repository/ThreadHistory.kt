package oolong.client.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import oolong.client.arch.ApplicationScope
import oolong.client.database.HistoryDao
import oolong.client.database.HistoryEntity
import oolong.client.model.Post
import oolong.client.model.ThreadDetail
import oolong.client.models.ThreadHistoryInfoBean
import oolong.client.utils.StringUtil
import javax.inject.Inject

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
            extras = Json.encodeToString(
                ThreadHistoryInfoBean(
                    isSeeLz = seeLz,
                    pid = readUpTo?.id?.toString(),
                    forumName = thread.forum.name,
                    floor = readUpTo?.floor?.toString(),
                ),
            ),
            avatar = StringUtil.getAvatarUrl(thread.author.portrait),
            username = thread.author.nameShow,
        )
        scope.launch { dao.visit(entry, System.currentTimeMillis()) }
    }
}
