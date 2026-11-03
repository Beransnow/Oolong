package oolong.client.arch.query

/**
 * A kind of thing the app shows in many places, such as a thread, told apart by [id]. Declared
 * once, e.g. `val Threads = EntityType<Long, ThreadSummary>("thread", id = { it.id })`.
 *
 * @param merge the entity to keep when a [newer] copy loads while there is an [older] one; places
 * leaving out some of what others show can fill it in from the older copy.
 */
class EntityType<I : Any, E : Any>(
    private val name: String,
    val id: (E) -> I,
    private val merge: (older: E, newer: E) -> E = { _, newer -> newer },
) {
    internal fun keyOf(entity: E) = EntityKey(this, id(entity))

    @Suppress("UNCHECKED_CAST")
    internal fun mergeAny(older: Any, newer: Any): Any = merge(older as E, newer as E)

    override fun toString() = name
}

internal data class EntityKey(val type: EntityType<*, *>, val id: Any)

/** Passes the entities of a value through their type; see [Normalize]. */
interface Normalizer {
    operator fun <E : Any> EntityType<*, E>.invoke(entity: E): E
}

/**
 * Says where entities sit in a value by passing each through its type, e.g.
 * `{ it.copy(thread = Threads(it.thread)) }`. The cache runs it both to take the entities from a
 * value just loaded and to put their latest versions in when showing the value.
 */
typealias Normalize<T> = Normalizer.(T) -> T

/** Changes to entities; see [QueryClient.mutate]. */
interface Updates {
    /**
     * Changes the entity of [type] with [id]. [transform] should set what the change is about
     * (liked, not liked) rather than flip it: it runs again on the server's copy when the
     * request succeeds, and that copy may already have the change.
     */
    fun <I : Any, E : Any> update(type: EntityType<I, E>, id: I, transform: (E) -> E)
}

/** The changes of one running mutation, shown on top of the entities until it ends. */
internal class Layer(val changes: List<Pair<EntityKey, (Any) -> Any>>) {
    fun apply(key: EntityKey, entity: Any): Any =
        changes.fold(entity) { current, (changed, transform) -> if (changed == key) transform(current) else current }

    companion object {
        fun of(updates: Updates.() -> Unit): Layer {
            val changes = mutableListOf<Pair<EntityKey, (Any) -> Any>>()
            val collector = object : Updates {
                override fun <I : Any, E : Any> update(type: EntityType<I, E>, id: I, transform: (E) -> E) {
                    @Suppress("UNCHECKED_CAST")
                    changes += EntityKey(type, id) to { entity: Any -> transform(entity as E) }
                }
            }
            collector.updates()
            return Layer(changes)
        }
    }
}
