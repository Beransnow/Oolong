package com.huanchengfly.tieba.post.utils

import com.huanchengfly.tieba.post.database.TopForumEntity
import com.huanchengfly.tieba.post.database.appDatabase

/** The followed forums pinned to the top of the home page. */
object TopForumStore {
    private val dao get() = appDatabase.topForumDao()

    suspend fun forumIds(): List<String> = dao.forumIds()

    suspend fun add(forumId: String) = dao.insert(TopForumEntity(forumId))

    /** Returns whether the forum was pinned. */
    suspend fun remove(forumId: String): Boolean = dao.delete(forumId) > 0
}
