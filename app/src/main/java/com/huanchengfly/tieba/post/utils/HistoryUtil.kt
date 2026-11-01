package com.huanchengfly.tieba.post.utils

import com.huanchengfly.tieba.post.database.HistoryEntity
import com.huanchengfly.tieba.post.database.appDatabase
import com.huanchengfly.tieba.post.models.database.History
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object HistoryUtil {
    const val PAGE_SIZE = 100
    const val TYPE_FORUM = 1
    const val TYPE_THREAD = 2

    private val dao get() = appDatabase.historyDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Records a visit, as a new entry or one more visit to the thread or forum's entry. */
    @JvmOverloads
    fun saveHistory(history: History, async: Boolean = true) {
        val visit = suspend { dao.visit(history.toEntity(), System.currentTimeMillis()) }
        if (async) scope.launch { visit() } else runBlocking { visit() }
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
}
