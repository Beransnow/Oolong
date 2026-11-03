package oolong.client.arch.query

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach

/**
 * The values of this flow with the latest version of the entities [normalize] marks in them, for
 * state a view model keeps itself: a thread liked elsewhere shows liked here too. The entities are
 * held while the flow is collected.
 *
 * Only entities that changed since the last value are stored, so a value emitted again for another
 * reason, still holding an old copy, doesn't undo what happened to them meanwhile.
 */
fun <T> Flow<T>.withLatestEntities(client: QueryClient, normalize: Normalize<T>): Flow<T> = flow {
    val holder = Any()
    val stored = HashMap<EntityKey, Any>()
    val values = onEach { value ->
        val taken = Taker().apply { normalize(value) }.entities
        val changed = taken.filter { (key, entity) -> stored[key] != entity }
        stored.putAll(changed)
        if (changed.isNotEmpty()) client.hold(holder, changed)
    }
    try {
        combine(values, client.entityChanges) { value, _ -> client.latest(listOf(value), normalize).single() }
            .collect { emit(it) }
    } finally {
        client.release(holder)
    }
}
