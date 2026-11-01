package com.huanchengfly.tieba.post.utils

import com.huanchengfly.tieba.post.api.models.protos.Post
import com.huanchengfly.tieba.post.api.models.protos.SubPostList
import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.api.models.protos.abstractText
import com.huanchengfly.tieba.post.api.models.protos.plainText
import com.huanchengfly.tieba.post.api.models.protos.resolvedAuthorId
import com.huanchengfly.tieba.post.database.BlockEntity
import com.huanchengfly.tieba.post.database.appDatabase
import com.huanchengfly.tieba.post.models.database.Block
import com.huanchengfly.tieba.post.models.database.Block.Companion.getKeywords
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object BlockManager {
    private val dao get() = appDatabase.blockDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var blockList: List<Block> = emptyList()

    private val _version = MutableStateFlow(0)

    /** Changes whenever a rule is added or removed, so that what was filtered can be filtered again. */
    val version: StateFlow<Int> = _version.asStateFlow()

    private val blackList: List<Block>
        get() = blockList.filter { it.category == Block.CATEGORY_BLACK_LIST }

    private val whiteList: List<Block>
        get() = blockList.filter { it.category == Block.CATEGORY_WHITE_LIST }

    /** Saves the rule in the background, then calls [callback] on the main thread with whether it was. */
    fun addBlockAsync(
        block: Block,
        callback: ((Boolean) -> Unit)? = null,
    ) {
        scope.launch {
            val saved = runCatching { dao.insert(block.toEntity()) }.isSuccess
            withContext(Dispatchers.Main) { callback?.invoke(saved) }
        }
    }

    /** Starts following the saved rules. */
    fun init() {
        scope.launch {
            dao.all().collect { blocks ->
                blockList = blocks.map { it.toBlock() }
                _version.update { it + 1 }
            }
        }
    }

    private fun Block.toEntity() =
        BlockEntity(id = id, category = category, type = type, keywords = keywords, username = username, uid = uid)

    private fun BlockEntity.toBlock() =
        Block(category = category, type = type, keywords = keywords, username = username, uid = uid, id = id)

    fun shouldBlock(content: String): Boolean {
        return blackList.any { block ->
            block.type == Block.TYPE_KEYWORD
                    && block.getKeywords().all { content.contains(it) }
        } && whiteList.none { block ->
            block.type == Block.TYPE_KEYWORD
                    && block.getKeywords().all { content.contains(it) }
        }
    }

    fun shouldBlock(userId: Long = 0L, userName: String? = null): Boolean {
        return blackList.any { it.matchesUser(userId, userName) }
                && whiteList.none { it.matchesUser(userId, userName) }
    }

    // A missing id (0) or blank name must never match, otherwise one rule saved without a name
    // would hide every user whose name the server left empty.
    private fun Block.matchesUser(userId: Long, userName: String?): Boolean =
        type == Block.TYPE_USER && (
                (userId != 0L && uid == userId.toString()) ||
                        (!userName.isNullOrBlank() && username == userName)
                )

    fun ThreadInfo.shouldBlock(): Boolean =
        shouldBlock(title) || shouldBlock(abstractText) || shouldBlock(authorId, author?.name)

    fun Post.shouldBlock(): Boolean =
        shouldBlock(content.plainText) || shouldBlock(resolvedAuthorId, author?.name)

    fun SubPostList.shouldBlock(): Boolean =
        shouldBlock(content.plainText) || shouldBlock(resolvedAuthorId, author?.name)
}