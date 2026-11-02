package com.huanchengfly.tieba.post.arch.query

import com.huanchengfly.tieba.post.arch.Page
import com.huanchengfly.tieba.post.arch.Pager
import com.huanchengfly.tieba.post.arch.RefreshMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * A [Pager] whose items show the latest version of the entities [normalize] marks in them: a
 * thread liked elsewhere shows liked here too. The pager holds those entities while [scope] lives.
 */
fun <K : Any, T> QueryClient.pager(
    scope: CoroutineScope,
    initialKey: K,
    itemKey: (T) -> Any,
    normalize: Normalize<T>,
    refreshMode: RefreshMode = RefreshMode.Replace,
    load: suspend (key: K) -> Page<K, T>,
): Pager<K, T> {
    val holder = Any()
    val pager = Pager(scope, initialKey, itemKey, refreshMode) { key ->
        val page = load(key)
        page.copy(items = hold(holder, page.items, normalize))
    }
    scope.launch { entityChanges.collect { pager.update { items -> latest(items, normalize) } } }
    scope.coroutineContext.job.invokeOnCompletion { release(holder) }
    return pager
}
