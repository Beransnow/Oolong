package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.api.models.protos.abstractText
import com.huanchengfly.tieba.post.arch.ApplicationScope
import com.huanchengfly.tieba.post.database.BlockDao
import com.huanchengfly.tieba.post.model.User
import com.huanchengfly.tieba.post.models.database.Block
import com.huanchengfly.tieba.post.models.database.Block.Companion.getKeywords
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the user's block rules hide something by [author] saying [content]. */
fun interface BlockRules {
    fun blocks(author: User, content: String): Boolean
}

/** Whether the rules hide [thread], by its author or by what its title or abstract says. */
fun BlockRules.blocks(thread: ThreadInfo): Boolean {
    val author = User(id = thread.authorId, name = thread.author?.name.orEmpty(), nameShow = "", portrait = "")
    return blocks(author, thread.title) || blocks(author, thread.abstractText)
}

/**
 * Rules from the user's lists. Content is hidden when a block keyword rule matches it and no
 * allow keyword rule does, and an author when a block user rule matches them and no allow user
 * rule does. A keyword rule matches content containing all of its keywords; a user rule matches
 * by id or name.
 */
fun blockRulesOf(rules: List<Block>): BlockRules {
    val (allowed, blocked) = rules.partition { it.category == Block.CATEGORY_WHITE_LIST }
    fun List<Block>.match(type: Int, author: User, content: String) =
        any { it.type == type && it.matches(author, content) }
    fun hides(type: Int, author: User, content: String) =
        blocked.match(type, author, content) && !allowed.match(type, author, content)
    return BlockRules { author, content ->
        hides(Block.TYPE_KEYWORD, author, content) || hides(Block.TYPE_USER, author, content)
    }
}

private fun Block.matches(author: User, content: String): Boolean = when (type) {
    Block.TYPE_KEYWORD -> getKeywords().all { content.contains(it) }
    // A missing id (0) or blank name must never match, otherwise one rule saved without a name
    // would hide every user whose name the server left empty.
    Block.TYPE_USER -> (author.id != 0L && uid == author.id.toString()) ||
        (author.name.isNotBlank() && username == author.name)

    else -> false
}

interface BlockRuleSource {
    /** The current rules, and new ones whenever the user changes them. */
    val rules: Flow<BlockRules>
}

@Singleton
class DatabaseBlockRuleSource @Inject constructor(
    dao: BlockDao,
    @ApplicationScope scope: CoroutineScope,
) : BlockRuleSource {
    override val rules: Flow<BlockRules> = dao.all()
        .map { entities ->
            blockRulesOf(entities.map { Block(it.category, it.type, it.keywords, it.username, it.uid, it.id) })
        }
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)
}
