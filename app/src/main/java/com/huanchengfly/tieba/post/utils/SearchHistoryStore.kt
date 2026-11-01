package com.huanchengfly.tieba.post.utils

import com.huanchengfly.tieba.post.database.appDatabase
import com.huanchengfly.tieba.post.models.database.SearchHistory
import com.huanchengfly.tieba.post.models.database.SearchPostHistory

/** Keywords searched for, across Tieba and inside forums, most recent first. */
object SearchHistoryStore {
    private val dao get() = appDatabase.searchHistoryDao()

    suspend fun all(): List<SearchHistory> =
        dao.all().map { SearchHistory(it.content, it.timestamp, it.id) }

    /** Adds the keyword, or moves it to the top if it was searched for before. */
    suspend fun add(keyword: String) = dao.add(keyword, System.currentTimeMillis())

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun clear() = dao.clear()

    suspend fun allInForums(): List<SearchPostHistory> =
        dao.allInForums().map { SearchPostHistory(it.content, it.forumName, it.timestamp, it.id) }

    suspend fun addInForum(keyword: String, forumName: String) =
        dao.addInForum(keyword, forumName, System.currentTimeMillis())

    suspend fun deleteInForum(id: Long) = dao.deleteInForum(id)

    suspend fun clearInForums() = dao.clearInForums()
}
