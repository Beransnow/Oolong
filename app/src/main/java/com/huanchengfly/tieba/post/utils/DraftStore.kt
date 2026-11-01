package com.huanchengfly.tieba.post.utils

import com.huanchengfly.tieba.post.database.DraftEntity
import com.huanchengfly.tieba.post.database.appDatabase

/** Unsent replies, keyed by a hash of what they reply to. */
object DraftStore {
    private val dao get() = appDatabase.draftDao()

    suspend fun content(hash: String): String? = dao.content(hash)

    suspend fun save(hash: String, content: String) = dao.upsert(DraftEntity(hash, content))

    suspend fun delete(hash: String) = dao.delete(hash)
}
