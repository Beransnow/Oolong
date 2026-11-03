package oolong.client.arch.query

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import oolong.client.arch.Page
import oolong.client.arch.Pager
import oolong.client.arch.PagerState
import oolong.client.arch.RefreshMode
import kotlin.time.Duration

/** A page an [InfiniteQuery] loaded: from where, the whole response, and whether it refreshed the list. */
@Immutable
data class Loaded<C, P>(val cursor: C, val response: P, val refreshed: Boolean)

/**
 * What a list loaded besides its items, for the rest of the page: the load that last refreshed it
 * and the latest load, the same one right after a refresh.
 */
@Immutable
data class Loads<C, P>(val refreshed: Loaded<C, P>? = null, val latest: Loaded<C, P>? = null)

/**
 * Lists loaded page by page whose pages are kept by key, declared once in a repository:
 *
 * ```
 * val posts = client.infiniteQuery<ThreadQuery, ThreadCursor, ThreadPage, Post>(
 *     name = "thread",
 *     itemKey = Post::id,
 *     page = { it.posts },
 *     staleTime = 1.minutes,
 * ) { query, cursor -> load(query, cursor) }
 * ```
 *
 * Everything showing the list of a key from the same first cursor shares it: pages loaded by one
 * show in all. A list shown again while fresh is used as it is; once stale it shows what it has
 * while it refreshes.
 */
class InfiniteQuery<K : Any, C : Any, P : Any, T> internal constructor(
    private val client: QueryClient,
    internal val name: String,
    internal val staleTime: Duration,
    internal val gcTime: Duration,
    internal val itemKey: (T) -> Any,
    internal val refreshMode: RefreshMode,
    internal val page: (P) -> Page<C, T>,
    internal val normalize: Normalize<T>,
    internal val fetch: suspend (key: K, cursor: C) -> P,
) {
    /** The list of [key] starting at [initialCursor], held while [scope] lives. */
    fun pager(scope: CoroutineScope, key: K, initialCursor: C): QueryPager<C, T, P> =
        client.pager(this, scope, key, initialCursor)

    /** Starts loading the list of [key] from [initialCursor] unless it is fresh, e.g. before opening it. */
    fun prefetch(key: K, initialCursor: C) = client.prefetch(this, key, initialCursor)

    /**
     * Marks the lists of the keys that match stale. Lists not shown are dropped; those shown stay as
     * they are, as loading them again would move them under the reader, and refresh when shown next.
     */
    fun invalidate(predicate: (K) -> Boolean = { true }) = client.invalidate(this, predicate)
}

/** A list of an [InfiniteQuery], used like a [Pager]. */
class QueryPager<C : Any, T, P> internal constructor(
    private val pager: Pager<C, T>,
    /** What the list loaded besides its items. */
    val loads: StateFlow<Loads<C, P>>,
) {
    val state: StateFlow<PagerState<T>> get() = pager.state

    /** Loads the list again from [from], or from where it first started. */
    fun refresh(from: C? = null) = if (from == null) pager.refresh() else pager.refresh(from)

    fun loadMore(from: C? = null) = pager.loadMore(from)

    fun loadPrevious(from: C? = null) = pager.loadPrevious(from)

    fun update(transform: (ImmutableList<T>) -> List<T>) = pager.update(transform)
}
