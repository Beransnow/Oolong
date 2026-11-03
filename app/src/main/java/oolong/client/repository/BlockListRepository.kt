package oolong.client.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import oolong.client.arch.IoDispatcher
import oolong.client.database.BlockDao
import oolong.client.database.BlockEntity
import oolong.client.database.blockConditionOf
import oolong.client.database.toJson
import oolong.client.model.BlockRule
import oolong.client.models.database.Block
import javax.inject.Inject

/** The user's block rules, as the settings list and edit them. */
class BlockListRepository @Inject constructor(
    private val dao: BlockDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** Every rule, and every rule again whenever one changes. */
    val blockRules: Flow<List<BlockRule>> = dao.all().map { entities -> entities.map { it.toBlockRule() } }

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
        BlockRule(condition = BlockRule.Condition.Text(BlockRule.Pattern.Words(keywords)), action = actionOf(category)),
    )

    /** Adds a rule on [category]'s list matching the user with [uid] or [name]. */
    suspend fun addUser(category: Int, uid: Long, name: String, displayName: String = "") = add(
        BlockRule(
            condition = BlockRule.Condition.User(uid, name, displayName.takeIf { it != name }.orEmpty()),
            action = actionOf(category),
        ),
    )

    suspend fun remove(id: Long) = withContext(io) { dao.delete(id) }

    private fun actionOf(category: Int) =
        if (category == Block.CATEGORY_WHITE_LIST) BlockRule.Action.Allow else BlockRule.Action.Default
}

/** The rule a stored row describes; a condition this version can't read matches nothing. */
fun BlockEntity.toBlockRule() = BlockRule(
    id = id,
    condition = blockConditionOf(condition) ?: BlockRule.Condition.AllOf(emptyList()),
    scope = BlockRule.Scope.entries.getOrElse(scope) { BlockRule.Scope.All },
    action = BlockRule.Action.entries.getOrElse(action) { BlockRule.Action.Default },
    expiresAt = expiresAt,
    enabled = enabled,
    hits = hits,
    lastHitAt = lastHitAt,
    createdAt = createdAt,
)

fun BlockRule.toEntity() = BlockEntity(
    id = id,
    condition = condition.toJson(),
    scope = scope.ordinal,
    action = action.ordinal,
    expiresAt = expiresAt,
    enabled = enabled,
    hits = hits,
    lastHitAt = lastHitAt,
    createdAt = createdAt,
)
