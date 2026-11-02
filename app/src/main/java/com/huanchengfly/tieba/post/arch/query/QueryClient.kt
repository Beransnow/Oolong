package com.huanchengfly.tieba.post.arch.query

import androidx.compose.runtime.Immutable
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.arch.Page
import com.huanchengfly.tieba.post.arch.Pager
import com.huanchengfly.tieba.post.arch.RefreshMode
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/** What a [Query] has for a key: its last result, and whether it is loading or failed since. */
@Immutable
data class QueryState<out T>(
    val data: T? = null,
    val status: LoadStatus = LoadStatus.Idle,
)

/**
 * A request whose results are kept by key, declared once in a repository:
 *
 * ```
 * val firstPage = client.query<ThreadQuery, ThreadPage>(
 *     name = "thread",
 *     staleTime = 1.minutes,
 *     normalize = { it.copy(thread = Threads(it.thread)) },
 * ) { query -> load(query) }
 * ```
 */
class Query<K : Any, T : Any> internal constructor(
    private val client: QueryClient,
    internal val name: String,
    internal val staleTime: Duration,
    internal val gcTime: Duration,
    internal val normalize: Normalize<T>,
    internal val fetch: suspend (K) -> T,
) {
    /**
     * The result for [key], loaded when there is none or it is stale, and again whenever it is
     * invalidated while observed.
     */
    fun observe(key: K): Flow<QueryState<T>> = client.observe(this, key)

    /** The result for [key]: the kept one while fresh, else that of the request already running or a new one. */
    suspend fun get(key: K): T = client.get(this, key)

    /** Loads [key] again even if fresh, joining a request already running, and returns the result. */
    suspend fun reload(key: K): T = client.reload(this, key)

    /** The kept result for [key], fresh or not, without loading it. */
    fun peek(key: K): T? = client.peek(this, key)

    /** Starts loading [key] in the background unless its result is fresh. */
    fun prefetch(key: K) {
        if (!client.isFresh(this, key)) client.start(this, key)
    }

    /** Loads [key] again even if fresh, e.g. to pull to refresh; failures show in [observe]. */
    fun refresh(key: K) {
        client.start(this, key)
    }

    /** Marks the results whose keys match stale: observed ones load again at once, the rest when next used. */
    fun invalidate(predicate: (K) -> Boolean = { true }) = client.invalidate(this, predicate)
}

/**
 * The app's memory of what it loaded: the results of [Query]s by key, and the entities in them by
 * id, one copy each, so a thread liked in one place shows liked everywhere.
 *
 * A result is dropped once nothing has observed it for its query's `gcTime`, and an entity once no
 * result or pager holds it. Everything is in memory, and [clear] drops it all.
 */
class QueryClient(
    parent: CoroutineScope,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {
    // A failed request fails only its callers, not the requests beside it
    private val scope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))
    private val state = MutableStateFlow(Snapshot())
    private val names = ConcurrentHashMap.newKeySet<String>()
    private val queries = ConcurrentHashMap<String, Query<*, *>>()
    private val lists = ConcurrentHashMap<QueryId, ListEntry>()
    private val requests = ConcurrentHashMap<QueryId, Deferred<Any>>()
    private val observers = ConcurrentHashMap<QueryId, Int>()
    private val collections = ConcurrentHashMap<QueryId, Job>()

    /** Declares a query; [name] tells its results apart from other queries' and must be unique. */
    fun <K : Any, T : Any> query(
        name: String,
        staleTime: Duration = Duration.ZERO,
        gcTime: Duration = 5.minutes,
        normalize: Normalize<T> = { it },
        fetch: suspend (K) -> T,
    ): Query<K, T> {
        check(names.add(name)) { "Query $name is declared twice" }
        return Query(this, name, staleTime, gcTime, normalize, fetch).also { queries[name] = it }
    }

    /**
     * Declares a query for lists loaded page by page, whose [page]s the [fetch]ed responses hold;
     * [name] must be unique among all queries.
     */
    fun <K : Any, C : Any, P : Any, T> infiniteQuery(
        name: String,
        itemKey: (T) -> Any,
        page: (P) -> Page<C, T>,
        staleTime: Duration = Duration.ZERO,
        gcTime: Duration = 5.minutes,
        refreshMode: RefreshMode = RefreshMode.Replace,
        normalize: Normalize<T> = { it },
        fetch: suspend (key: K, cursor: C) -> P,
    ): InfiniteQuery<K, C, P, T> {
        check(names.add(name)) { "Query $name is declared twice" }
        return InfiniteQuery(this, name, staleTime, gcTime, itemKey, refreshMode, page, normalize, fetch)
    }

    /** The latest version of an entity, with the changes of running mutations. */
    fun <I : Any, E : Any> get(type: EntityType<I, E>, id: I): E? = state.value.entity(EntityKey(type, id))

    fun <I : Any, E : Any> observe(type: EntityType<I, E>, id: I): Flow<E?> =
        state.map { it.entity<E>(EntityKey(type, id)) }.distinctUntilChanged()

    /** Changes an entity the app has, e.g. to what a request just returned. */
    fun <I : Any, E : Any> update(type: EntityType<I, E>, id: I, transform: (E) -> E) {
        state.update { it.commit(Layer.of { update(type, id, transform) }) }
    }

    /**
     * Runs [request], showing the [optimistic] changes while it runs. They are kept when it
     * succeeds and dropped when it fails, and a copy loaded meanwhile doesn't hide them. The
     * request runs on even if the caller is cancelled, e.g. by leaving the page.
     */
    suspend fun <R> mutate(optimistic: Updates.() -> Unit = {}, request: suspend () -> R): R {
        val layer = Layer.of(optimistic)
        state.update { it.copy(layers = it.layers.add(layer)) }
        return scope.async {
            try {
                request().also { state.update { it.commit(layer) } }
            } catch (e: Throwable) {
                state.update { it.copy(layers = it.layers.remove(layer)) }
                throw e
            }
        }.await()
    }

    /** Drops every result and entity and cancels the requests running, e.g. when the account changes. */
    fun clear() {
        requests.values.forEach { it.cancel() }
        requests.clear()
        state.update { Snapshot(holds = it.holds.mutate { holds -> holds.keys.removeAll { holder -> holder is QueryId } }) }
        observers.keys.forEach { id -> queries[id.name]?.let { start(it, id.key) } }
        lists.forEach { (id, list) ->
            if (observers.containsKey(id)) list.reload() else drop(id)
        }
    }

    internal fun <K : Any, T : Any> observe(query: Query<K, T>, key: K): Flow<QueryState<T>> {
        val id = QueryId(query.name, key)
        return state
            .map { it.queryState(id, query.normalize) }
            .distinctUntilChanged()
            .onStart {
                attach(id)
                if (!isFresh(query, key)) start(query, key)
            }
            .onCompletion { detach(id, query.gcTime) }
    }

    private fun attach(id: QueryId) {
        observers.merge(id, 1, Int::plus)
        collections.remove(id)?.cancel()
    }

    private fun detach(id: QueryId, gcTime: Duration) {
        if (observers.computeIfPresent(id) { _, count -> (count - 1).takeIf { it > 0 } } == null) {
            collectLater(id, gcTime)
        }
    }

    internal suspend fun <K : Any, T : Any> get(query: Query<K, T>, key: K): T {
        if (isFresh(query, key)) peek(query, key)?.let { return it }
        return reload(query, key)
    }

    internal suspend fun <K : Any, T : Any> reload(query: Query<K, T>, key: K): T {
        @Suppress("UNCHECKED_CAST")
        val data = start(query, key).await() as T
        return state.value.read(data, query.normalize)
    }

    internal fun <K : Any, T : Any> peek(query: Query<K, T>, key: K): T? =
        state.value.queryState(QueryId(query.name, key), query.normalize).data

    internal fun isFresh(query: Query<*, *>, key: Any): Boolean {
        val loadedAt = state.value.queries[QueryId(query.name, key)]?.loadedAt ?: return false
        return loadedAt.elapsedNow() < query.staleTime
    }

    /** The request running for [key], or a new one. */
    internal fun start(query: Query<*, *>, key: Any): Deferred<Any> {
        val id = QueryId(query.name, key)
        var started = false
        val request = requests.computeIfAbsent(id) {
            started = true
            @Suppress("UNCHECKED_CAST")
            scope.async(start = CoroutineStart.LAZY) { load(query as Query<Any, Any>, id) }
        }
        if (started) {
            state.update { it.withStatus(id, LoadStatus.Loading) }
            request.invokeOnCompletion {
                requests.remove(id, request)
                if (!observers.containsKey(id)) collectLater(id, query.gcTime)
            }
            request.start()
        }
        return request
    }

    private suspend fun CoroutineScope.load(query: Query<Any, Any>, id: QueryId): Any = try {
        val data = query.fetch(id.key)
        // A request cancelled by clear() mustn't bring back what it cleared
        ensureActive()
        state.update { it.loaded(id, data, query.normalize, timeSource.markNow()) }
        data
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        state.update { it.withStatus(id, LoadStatus.Error(e)) }
        throw e
    }

    internal fun <K : Any> invalidate(query: Query<K, *>, predicate: (K) -> Boolean) {
        @Suppress("UNCHECKED_CAST")
        fun QueryId.matches() = name == query.name && predicate(key as K)
        state.update { it.invalidated { id -> id.matches() } }
        observers.keys.filter { it.matches() }.forEach { start(query, it.key) }
    }

    private fun collectLater(id: QueryId, gcTime: Duration) {
        val collection = scope.launch {
            delay(gcTime)
            if (!observers.containsKey(id) && !requests.containsKey(id)) drop(id)
        }
        collections.put(id, collection)?.cancel()
    }

    private fun drop(id: QueryId) {
        lists.remove(id)?.job?.cancel()
        state.update { it.release(id) }
    }

    /** The list of [key] from [initialCursor], shared by all who show it, held while [scope] lives. */
    internal fun <K : Any, C : Any, P : Any, T> pager(
        query: InfiniteQuery<K, C, P, T>,
        scope: CoroutineScope,
        key: K,
        initialCursor: C,
    ): QueryPager<C, T, P> {
        val id = QueryId(query.name, ListKey(key, initialCursor))
        attach(id)
        scope.coroutineContext.job.invokeOnCompletion { detach(id, query.gcTime) }
        val list = list(query, id, key, initialCursor)
        @Suppress("UNCHECKED_CAST")
        return QueryPager(list.pager as Pager<C, T>, list.loads as StateFlow<Loads<C, P>>)
    }

    internal fun <K : Any, C : Any> prefetch(query: InfiniteQuery<K, C, *, *>, key: K, initialCursor: C) {
        val id = QueryId(query.name, ListKey(key, initialCursor))
        list(query, id, key, initialCursor)
        if (!observers.containsKey(id)) collectLater(id, query.gcTime)
    }

    internal fun <K : Any> invalidate(query: InfiniteQuery<K, *, *, *>, predicate: (K) -> Boolean) {
        lists.forEach { (id, list) ->
            @Suppress("UNCHECKED_CAST")
            if (id.name == query.name && predicate((id.key as ListKey).key as K)) {
                if (observers.containsKey(id)) list.loadedAt = null else drop(id)
            }
        }
    }

    /** The list kept for [id], loading again if it is stale, or a new one loading its first page. */
    private fun list(query: InfiniteQuery<*, *, *, *>, id: QueryId, key: Any, initialCursor: Any): ListEntry {
        lists[id]?.let { return it.apply { if (!isFresh(query.staleTime)) reload() } }
        synchronized(lists) {
            lists[id]?.let { return it }
            val job = SupervisorJob(scope.coroutineContext.job)
            val list = ListEntry(job)
            lists[id] = list
            @Suppress("UNCHECKED_CAST")
            list.start(CoroutineScope(scope.coroutineContext + job), query as InfiniteQuery<Any, Any, Any, Any?>, id, key, initialCursor)
            return list
        }
    }

    private fun ListEntry.start(
        listScope: CoroutineScope,
        query: InfiniteQuery<Any, Any, Any, Any?>,
        id: QueryId,
        key: Any,
        initialCursor: Any,
    ) {
        pager = Pager(listScope, initialCursor, query.itemKey, query.refreshMode) { cursor ->
            val response = query.fetch(key, cursor)
            currentCoroutineContext().ensureActive()
            val loaded = Loaded(cursor, response, refreshing)
            loads.update { if (refreshing) Loads(loaded, loaded) else it.copy(latest = loaded) }
            if (refreshing) loadedAt = timeSource.markNow()
            val page = query.page(response)
            page.copy(items = hold(id, page.items, query.normalize))
        }
        listScope.launch { entityChanges.collect { pager.update { items -> latest(items, query.normalize) } } }
    }

    /** Keeps [entities] while [holder] holds them, along with what it held already. */
    internal fun hold(holder: Any, entities: Map<EntityKey, Any>) =
        state.update { it.put(entities).holding(holder, entities.keys) }

    /** Keeps the entities of [items] while [holder] holds them, and returns the items with their latest versions. */
    internal fun <T> hold(holder: Any, items: List<T>, normalize: Normalize<T>): List<T> {
        val taken = Taker().apply { items.forEach { normalize(it) } }.entities
        val snapshot = state.updateAndGet { it.put(taken).holding(holder, taken.keys) }
        return items.map { snapshot.read(it, normalize) }
    }

    internal fun <T> latest(items: List<T>, normalize: Normalize<T>): List<T> {
        val snapshot = state.value
        return items.map { snapshot.read(it, normalize) }
    }

    /** Emits whenever an entity changes. */
    internal val entityChanges: Flow<Unit> = state
        .distinctUntilChanged { old, new -> old.entities === new.entities && old.layers === new.layers }
        .map { }

    internal fun release(holder: Any) = state.update { it.release(holder) }
}

internal data class QueryId(val name: String, val key: Any)

private data class ListKey(val key: Any, val initialCursor: Any)

/** A list an [InfiniteQuery] keeps: its pager, which loads in the client's scope, and what it loaded. */
private class ListEntry(val job: Job) {
    lateinit var pager: Pager<Any, Any?>
    val loads = MutableStateFlow(Loads<Any, Any>())

    /** When the list was last refreshed; null before, or once invalidated. */
    @Volatile
    var loadedAt: ComparableTimeMark? = null

    fun isFresh(staleTime: Duration) = loadedAt?.let { it.elapsedNow() < staleTime } == true

    /** Refreshes the list unless it already is, keeping what it shows until the new page comes. */
    fun reload() {
        if (pager.state.value.refresh != LoadStatus.Loading) pager.refresh()
    }
}

private data class Entry(
    val data: Any? = null,
    val status: LoadStatus = LoadStatus.Idle,
    /** When [data] loaded; null when there is none or it was invalidated since. */
    val loadedAt: ComparableTimeMark? = null,
)

/** Everything the client has, changed only as a whole. */
private data class Snapshot(
    /** Entities as the server last sent them. */
    val entities: PersistentMap<EntityKey, Any> = persistentHashMapOf(),
    /** Changes of the mutations running, oldest first, shown on top of [entities]. */
    val layers: PersistentList<Layer> = persistentListOf(),
    val queries: PersistentMap<QueryId, Entry> = persistentHashMapOf(),
    /** The entities each result ([QueryId]) or pager holds. */
    val holds: PersistentMap<Any, Set<EntityKey>> = persistentHashMapOf(),
) {
    @Suppress("UNCHECKED_CAST")
    fun <E> entity(key: EntityKey, fallback: Any? = null): E? =
        layers.fold(entities[key] ?: fallback) { entity, layer -> entity?.let { layer.apply(key, it) } } as E?

    fun <T> read(value: T, normalize: Normalize<T>): T = Reader(this).normalize(value)

    @Suppress("UNCHECKED_CAST")
    fun <T> queryState(id: QueryId, normalize: Normalize<T>): QueryState<T> {
        val entry = queries[id] ?: return QueryState()
        return QueryState(entry.data?.let { read(it as T, normalize) }, entry.status)
    }

    fun withStatus(id: QueryId, status: LoadStatus) =
        copy(queries = queries.put(id, (queries[id] ?: Entry()).copy(status = status)))

    fun <T> loaded(id: QueryId, data: T, normalize: Normalize<T>, at: ComparableTimeMark): Snapshot {
        val taken = Taker().apply { normalize(data) }.entities
        return put(taken).copy(
            queries = queries.put(id, Entry(data, LoadStatus.Idle, at)),
            holds = holds.put(id, taken.keys),
        )
    }

    fun invalidated(predicate: (QueryId) -> Boolean) = copy(
        queries = queries.mutate { entries ->
            queries.forEach { (id, entry) -> if (predicate(id)) entries[id] = entry.copy(loadedAt = null) }
        },
    )

    fun put(taken: Map<EntityKey, Any>) = copy(
        entities = entities.mutate { result ->
            taken.forEach { (key, newer) -> result[key] = entities[key]?.let { key.type.mergeAny(it, newer) } ?: newer }
        },
    )

    fun holding(holder: Any, keys: Set<EntityKey>) = copy(holds = holds.put(holder, holds[holder].orEmpty() + keys))

    /** Applies [layer] to the entities, ending it if it is running. */
    fun commit(layer: Layer) = copy(
        entities = layer.changes.fold(entities) { result, (key, _) ->
            result[key]?.let { result.put(key, layer.apply(key, it)) } ?: result
        },
        layers = layers.remove(layer),
    )

    /** Drops what [holder] holds, and with it the entities nothing else holds. */
    fun release(holder: Any): Snapshot {
        val holds = holds.remove(holder)
        val held = holds.values.flatMapTo(HashSet()) { it }
        layers.flatMapTo(held) { layer -> layer.changes.map { it.first } }
        return copy(
            entities = entities.mutate { it.keys.retainAll(held) },
            queries = if (holder is QueryId) queries.remove(holder) else queries,
            holds = holds,
        )
    }
}

/** Takes the entities from a value just loaded. */
internal class Taker : Normalizer {
    val entities = HashMap<EntityKey, Any>()

    override fun <E : Any> EntityType<*, E>.invoke(entity: E): E = entity.also { entities[keyOf(it)] = it }
}

/** Puts the latest versions of its entities into a value. */
private class Reader(private val snapshot: Snapshot) : Normalizer {
    override fun <E : Any> EntityType<*, E>.invoke(entity: E): E = snapshot.entity(keyOf(entity), entity) ?: entity
}
