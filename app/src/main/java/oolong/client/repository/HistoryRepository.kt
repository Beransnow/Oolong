package oolong.client.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import oolong.client.arch.IoDispatcher
import oolong.client.database.HistoryDao
import oolong.client.database.HistoryEntity
import oolong.client.model.HistoryEntry
import oolong.client.models.ThreadHistoryInfoBean
import javax.inject.Inject

/** The threads and forums the current account visited, most recent first; another account's when it is switched to. */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryRepository @Inject constructor(
    private val dao: HistoryDao,
    private val owner: HistoryOwner,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /**
     * Up to [limit] of the latest visited threads, and the latest again whenever they change; with
     * a [query], only those whose title, author or forum has it.
     */
    fun threads(limit: Int, query: String = ""): Flow<List<HistoryEntry.Thread>> =
        entries(HistoryEntity.TYPE_THREAD, limit, query, { it.toThread() }) { thread, words ->
            thread.title.contains(words, ignoreCase = true) ||
                thread.authorName.contains(words, ignoreCase = true) ||
                thread.forumName.contains(words, ignoreCase = true)
        }

    /** Up to [limit] of the latest visited forums, and the latest again whenever they change; with a [query], only those whose name has it. */
    fun forums(limit: Int, query: String = ""): Flow<List<HistoryEntry.Forum>> =
        entries(HistoryEntity.TYPE_FORUM, limit, query, { it.toForum() }) { forum, words -> forum.title.contains(words, ignoreCase = true) }

    // A history is a few thousand rows at most: searching goes through all of them in memory,
    // which also matches the forum names kept in the extras
    private fun <T : HistoryEntry> entries(
        type: Int,
        limit: Int,
        query: String,
        convert: (HistoryEntity) -> T?,
        matches: (T, String) -> Boolean,
    ): Flow<List<T>> = owner.uid.flatMapLatest { uid ->
        val words = query.trim()
        if (words.isEmpty()) {
            dao.latest(uid, type, limit).map { entries -> entries.mapNotNull(convert) }
        } else {
            dao.all(uid, type).map { entries -> entries.asSequence().mapNotNull(convert).filter { matches(it, words) }.take(limit).toList() }
        }
    }

    suspend fun delete(entry: HistoryEntry) = withContext(io) { dao.delete(entry.id) }

    /** Deletes the entries with [ids]. */
    suspend fun delete(ids: Collection<Long>) = withContext(io) { dao.delete(ids) }

    /** Deletes the current account's visits from [since] on, or all of them when it is null. */
    suspend fun clear(since: Long? = null) = withContext(io) {
        if (since == null) dao.clear(owner.uid.value) else dao.clearSince(owner.uid.value, since)
    }
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
        floor = extras?.floor?.toIntOrNull() ?: 0,
        forumName = extras?.forumName.orEmpty(),
        forumAvatar = extras?.forumAvatar.orEmpty(),
        thumbnail = extras?.thumbnail.orEmpty(),
    )
}

private fun HistoryEntity.toForum() = HistoryEntry.Forum(
    id = id,
    time = timestamp,
    avatar = avatar.orEmpty(),
    name = data,
    title = title,
    visits = count,
)
