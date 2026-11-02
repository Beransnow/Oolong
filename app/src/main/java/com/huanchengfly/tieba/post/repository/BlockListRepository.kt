package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.post.arch.IoDispatcher
import com.huanchengfly.tieba.post.database.BlockDao
import com.huanchengfly.tieba.post.database.BlockEntity
import com.huanchengfly.tieba.post.fromJson
import com.huanchengfly.tieba.post.model.BlockRule
import com.huanchengfly.tieba.post.models.database.Block
import com.huanchengfly.tieba.post.toJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** The user's block rules, as the settings list and edit them. */
class BlockListRepository @Inject constructor(
    private val dao: BlockDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** Every rule, and every rule again whenever one changes. */
    val blockRules: Flow<List<BlockRule>> = dao.all().map { entities -> entities.map { it.toBlockRule() } }

    /** Every rule as the old block and allow lists had them. */
    val rules: Flow<List<Block>> = dao.all().map { entities ->
        entities.map { Block(it.category, it.type, it.keywords, it.username, it.uid, it.id) }
    }

    /** Adds [rule], dated now; returns its id. */
    suspend fun add(rule: BlockRule): Long = withContext(io) {
        dao.insert(rule.copy(id = 0, createdAt = System.currentTimeMillis()).toEntity())
    }

    suspend fun update(rule: BlockRule) = withContext(io) { dao.update(rule.toEntity()) }

    /** Adds those of [rules] that no saved rule does already; returns how many. */
    suspend fun import(rules: List<BlockRule>): Int = withContext(io) {
        val saved = dao.all().first().map { it.toBlockRule() }
        val now = System.currentTimeMillis()
        val added = rules.filter { rule -> saved.none { it.sameAs(rule) } }
            .distinctBy { it.copy(id = 0) }
            .map { it.copy(id = 0, createdAt = now) }
        dao.insertAll(added.map { it.toEntity() })
        added.size
    }

    /** Puts back rules just removed, with their ids, counts and dates. */
    suspend fun restore(rules: List<BlockRule>) = withContext(io) { dao.insertAll(rules.map { it.toEntity() }) }

    suspend fun remove(ids: List<Long>) = withContext(io) { dao.deleteAll(ids) }

    /** Adds a rule on [category]'s list matching all of [keywords]. */
    suspend fun addKeywords(category: Int, keywords: List<String>) = add(
        BlockRule(match = BlockRule.Match.Keyword(keywords), action = actionOf(category)),
    )

    /** Adds a rule on [category]'s list matching the user with [uid] or [name]. */
    suspend fun addUser(category: Int, uid: Long, name: String) = add(
        BlockRule(match = BlockRule.Match.User(uid, name), action = actionOf(category)),
    )

    suspend fun remove(id: Long) = withContext(io) { dao.delete(id) }

    private fun actionOf(category: Int) =
        if (category == Block.CATEGORY_WHITE_LIST) BlockRule.Action.Allow else BlockRule.Action.Default
}

private const val TypeKeyword = 0
private const val TypeUser = 1
private const val TypeForum = 2
private const val TypeLevel = 3

/** The rule a stored row describes; exceptions are kept on the old allow list. */
fun BlockEntity.toBlockRule() = BlockRule(
    id = id,
    match = when (type) {
        TypeUser -> BlockRule.Match.User(uid?.toLongOrNull() ?: 0L, username.orEmpty())
        TypeForum -> BlockRule.Match.Forum(forumName.orEmpty())
        TypeLevel -> BlockRule.Match.Level(level ?: 0)
        else -> BlockRule.Match.Keyword(keywords?.let { runCatching { it.fromJson<List<String>>() }.getOrNull() }.orEmpty(), regex)
    },
    inForum = inForum,
    titleOnly = titleOnly,
    scope = BlockRule.Scope.entries.getOrElse(scope) { BlockRule.Scope.All },
    action = if (category == Block.CATEGORY_WHITE_LIST) {
        BlockRule.Action.Allow
    } else {
        when (action) {
            1 -> BlockRule.Action.Fold
            2 -> BlockRule.Action.Hide
            else -> BlockRule.Action.Default
        }
    },
    expiresAt = expiresAt,
    enabled = enabled,
    hits = hits,
    lastHitAt = lastHitAt,
    createdAt = createdAt,
)

fun BlockRule.toEntity(): BlockEntity {
    val base = BlockEntity(
        id = id,
        category = if (action == BlockRule.Action.Allow) Block.CATEGORY_WHITE_LIST else Block.CATEGORY_BLACK_LIST,
        type = TypeKeyword,
        inForum = inForum,
        titleOnly = titleOnly,
        scope = scope.ordinal,
        action = when (action) {
            BlockRule.Action.Fold -> 1
            BlockRule.Action.Hide -> 2
            else -> 0
        },
        expiresAt = expiresAt,
        enabled = enabled,
        hits = hits,
        lastHitAt = lastHitAt,
        createdAt = createdAt,
    )
    return when (val match = match) {
        is BlockRule.Match.Keyword -> base.copy(type = TypeKeyword, keywords = match.words.toJson(), regex = match.regex)
        is BlockRule.Match.User -> base.copy(type = TypeUser, uid = match.uid.toString(), username = match.name)
        is BlockRule.Match.Forum -> base.copy(type = TypeForum, forumName = match.name)
        is BlockRule.Match.Level -> base.copy(type = TypeLevel, level = match.level)
    }
}
