package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.post.arch.IoDispatcher
import com.huanchengfly.tieba.post.database.BlockDao
import com.huanchengfly.tieba.post.database.BlockEntity
import com.huanchengfly.tieba.post.models.database.Block
import com.huanchengfly.tieba.post.toJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** The user's block and allow rules, as the settings list them. */
class BlockListRepository @Inject constructor(
    private val dao: BlockDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** Every rule, and every rule again whenever one is added or removed. */
    val rules: Flow<List<Block>> = dao.all().map { entities ->
        entities.map { Block(it.category, it.type, it.keywords, it.username, it.uid, it.id) }
    }

    /** Adds a rule on [category]'s list matching all of [keywords]. */
    suspend fun addKeywords(category: Int, keywords: List<String>) = withContext(io) {
        dao.insert(BlockEntity(category = category, type = Block.TYPE_KEYWORD, keywords = keywords.toJson()))
    }

    /** Adds a rule on [category]'s list matching the user with [uid] or [name]. */
    suspend fun addUser(category: Int, uid: Long, name: String) = withContext(io) {
        dao.insert(BlockEntity(category = category, type = Block.TYPE_USER, username = name, uid = uid.toString()))
    }

    suspend fun remove(id: Long) = withContext(io) { dao.delete(id) }
}
