package com.huanchengfly.tieba.post.utils

import com.huanchengfly.tieba.post.database.HistoryEntity
import com.huanchengfly.tieba.post.database.appDatabase
import com.huanchengfly.tieba.post.models.database.History
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object HistoryUtil {
    const val PAGE_SIZE = 100
    const val TYPE_FORUM = 1
    const val TYPE_THREAD = 2

    private val dao get() = appDatabase.historyDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun deleteAll() = dao.clear()

    /** Returns whether there was an entry to delete. */
    suspend fun delete(id: Long): Boolean = dao.delete(id) > 0

    /** Records a visit, as a new entry or one more visit to the thread or forum's entry. */
    @JvmOverloads
    fun saveHistory(history: History, async: Boolean = true) {
        val visit = suspend { dao.visit(history.toEntity(), System.currentTimeMillis()) }
        if (async) scope.launch { visit() } else runBlocking { visit() }
    }

    /** One page of the entries of [type], most recent first. */
    fun getFlow(type: Int, page: Int): Flow<List<History>> = flow {
        emit(dao.page(type, PAGE_SIZE, page * PAGE_SIZE).map { it.toHistory() })
    }

    private fun History.toEntity() = HistoryEntity(
        id = id,
        type = type,
        data = data,
        title = title,
        timestamp = timestamp,
        count = count,
        extras = extras,
        avatar = avatar,
        username = username,
    )

    private fun HistoryEntity.toHistory() = History(
        title = title,
        data = data,
        type = type,
        timestamp = timestamp,
        count = count,
        extras = extras,
        avatar = avatar,
        username = username,
        id = id,
    )
}
