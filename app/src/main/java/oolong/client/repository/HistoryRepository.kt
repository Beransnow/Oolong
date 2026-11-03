package oolong.client.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import oolong.client.arch.IoDispatcher
import oolong.client.database.HistoryDao
import oolong.client.database.HistoryEntity
import oolong.client.model.HistoryEntry
import oolong.client.models.ThreadHistoryInfoBean
import javax.inject.Inject

/** The threads and forums the user visited, most recent first. */
class HistoryRepository @Inject constructor(
    private val dao: HistoryDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** Up to [limit] of the latest visited threads, and the latest again whenever they change. */
    fun threads(limit: Int): Flow<List<HistoryEntry.Thread>> =
        dao.latest(HistoryEntity.TYPE_THREAD, limit).map { entries -> entries.mapNotNull { it.toThread() } }

    /** Up to [limit] of the latest visited forums, and the latest again whenever they change. */
    fun forums(limit: Int): Flow<List<HistoryEntry.Forum>> =
        dao.latest(HistoryEntity.TYPE_FORUM, limit).map { entries -> entries.map { it.toForum() } }

    suspend fun delete(entry: HistoryEntry) = withContext(io) { dao.delete(entry.id) }

    suspend fun clear() = withContext(io) { dao.clear() }
}

private val extrasJson = Json { ignoreUnknownKeys = true }

private fun HistoryEntity.toThread(): HistoryEntry.Thread? {
    val threadId = data.toLongOrNull() ?: return null
    val extras = extras?.let { runCatching { extrasJson.decodeFromString<ThreadHistoryInfoBean>(it) }.getOrNull() }
    return HistoryEntry.Thread(
        id = id,
        time = timestamp,
        avatar = avatar.orEmpty(),
        threadId = threadId,
        title = title,
        authorName = username.orEmpty(),
        postId = extras?.pid?.toLongOrNull() ?: 0,
        seeLz = extras?.isSeeLz ?: false,
    )
}

private fun HistoryEntity.toForum() = HistoryEntry.Forum(
    id = id,
    time = timestamp,
    avatar = avatar.orEmpty(),
    name = data,
    title = title,
)
