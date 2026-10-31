package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.post.model.User
import com.huanchengfly.tieba.post.utils.BlockManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Whether the user's block rules hide something by [author] saying [content]. */
fun interface BlockRules {
    fun blocks(author: User, content: String): Boolean
}

interface BlockRuleSource {
    /** The current rules, and new ones whenever the user changes them. */
    val rules: Flow<BlockRules>
}

class BlockManagerRuleSource @Inject constructor() : BlockRuleSource {
    override val rules: Flow<BlockRules> = BlockManager.version.map {
        BlockRules { author, content ->
            BlockManager.shouldBlock(content) || BlockManager.shouldBlock(author.id, author.name)
        }
    }
}
