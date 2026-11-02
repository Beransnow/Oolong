package com.huanchengfly.tieba.post.ui.page.main.explore

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.Actions
import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.model.DislikeOption
import com.huanchengfly.tieba.post.model.ThreadSummary
import com.huanchengfly.tieba.post.model.knownFirstFloor
import com.huanchengfly.tieba.post.repository.ThreadRepository
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.page.main.explore.personalized.dislikeMenuItems
import com.huanchengfly.tieba.post.ui.page.main.explore.personalized.shareMenuItems
import com.huanchengfly.tieba.post.ui.page.thread.FirstPostContent
import com.huanchengfly.tieba.post.ui.page.thread.navigateToThread
import com.huanchengfly.tieba.post.ui.widgets.compose.ContextMenuItem
import com.huanchengfly.tieba.post.ui.widgets.compose.ContextMenuPreview
import com.huanchengfly.tieba.post.ui.widgets.compose.LocalContextMenus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import javax.inject.Inject

/** Storing a thread from its peek. */
@HiltViewModel
class ThreadPeekViewModel @Inject constructor(
    private val repository: ThreadRepository,
    private val messages: MessageController,
) : ViewModel() {
    private val actions = Actions(viewModelScope)

    fun store(thread: ThreadSummary) = actions.launch("store-${thread.id}") {
        runCatching { repository.addStore(thread.id, thread.firstPostId) }
            .onSuccess { messages.show(R.string.message_add_favorite_success, 1) }
            .onFailure { messages.showError(R.string.message_add_favorite_failure, it) }
    }
}

/** Long-pressing a thread card: a look at its whole first floor, over what can be done with it. */
@Stable
class ThreadPeeker internal constructor(
    private val show: (ThreadSummary, Rect, Offset, ImmutableList<DislikeOption>, (Long, ImmutableList<DislikeOption>) -> Unit) -> Unit,
) {
    /**
     * Peeks at [thread], whose card is at [from] in the window and was pressed [at], offering the
     * server's [options] for not wanting it besides storing, sharing and copying its link.
     */
    fun peek(
        thread: ThreadSummary,
        from: Rect,
        at: Offset,
        options: ImmutableList<DislikeOption> = persistentListOf(),
        onDislike: (clickTime: Long, reasons: ImmutableList<DislikeOption>) -> Unit = { _, _ -> },
    ) = show(thread, from, at, options, onDislike)
}

/** Peeks at threads of this feed; null where there are no context menus. */
@Composable
fun rememberThreadPeeker(): ThreadPeeker? {
    // Checked first: where there are no menus, as in previews and tests, there is no view model either
    val menus = LocalContextMenus.current ?: return null
    val viewModel = hiltViewModel<ThreadPeekViewModel>()
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val collect = stringResource(id = R.string.title_collect)
    return remember(menus, context, navigator, viewModel, collect) {
        ThreadPeeker { thread, from, at, options, onDislike ->
            val items = buildList {
                add(ContextMenuItem(Icons.Rounded.StarBorder, collect) { viewModel.store(thread) })
                addAll(shareMenuItems(context, thread.id, thread.title))
                dislikeMenuItems(context, navigator, options, onDislike).forEachIndexed { i, item ->
                    add(if (i == 0) item.copy(startsGroup = true) else item)
                }
            }
            menus.show(
                items = items,
                anchor = Rect(at, Size.Zero),
                preview = ContextMenuPreview(
                    from = from,
                    onClick = { navigator.navigateToThread(thread) },
                ) {
                    ThreadPeek(thread = thread)
                },
            )
        }
    }
}

/**
 * A thread's first floor in full, as the thread page shows it, from what the list knows of it:
 * lists carry the whole first floor, only their abstract is cut short.
 */
@Composable
fun ThreadPeek(thread: ThreadSummary, modifier: Modifier = Modifier) {
    val post = remember(thread) { thread.knownFirstFloor() }
    FirstPostContent(
        post = post,
        title = post.title,
        modifier = modifier.fillMaxWidth().padding(vertical = 16.dp),
    )
}
