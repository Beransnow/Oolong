package com.huanchengfly.tieba.post.ui.page.main.explore

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.arch.Actions
import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.model.Agree
import com.huanchengfly.tieba.post.model.DislikeOption
import com.huanchengfly.tieba.post.model.Post
import com.huanchengfly.tieba.post.repository.ThreadCursor
import com.huanchengfly.tieba.post.repository.ThreadPreview
import com.huanchengfly.tieba.post.repository.ThreadQuery
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * The first floors of threads peeked at from a feed, loaded when first peeked at and kept while
 * the feed is open, and storing a thread from its peek.
 */
@HiltViewModel
class ThreadPeekViewModel @Inject constructor(
    private val repository: ThreadRepository,
    private val messages: MessageController,
) : ViewModel() {
    private val actions = Actions(viewModelScope)
    private val _firstPosts = MutableStateFlow<Map<Long, Post>>(emptyMap())

    /** The first floors loaded so far, by thread id. */
    val firstPosts: StateFlow<Map<Long, Post>> = _firstPosts.asStateFlow()

    fun preview(thread: ThreadInfo): ThreadPreview = repository.preview(thread)

    /** Loads [thread]'s first floor unless it has been; until then its peek shows what the feed knows. */
    fun load(thread: ThreadInfo) {
        if (thread.threadId in _firstPosts.value) return
        actions.launch("load-${thread.threadId}") {
            runCatching {
                repository.page(ThreadQuery(thread.threadId, forumId = thread.forumId), ThreadCursor.first()).firstPost
            }.getOrNull()?.let { post -> _firstPosts.update { it + (thread.threadId to post) } }
        }
    }

    fun store(thread: ThreadInfo) = actions.launch("store-${thread.threadId}") {
        runCatching { repository.addStore(thread.threadId, thread.firstPostId) }
            .onSuccess { messages.show(R.string.message_add_favorite_success, 1) }
            .onFailure { messages.showError(R.string.message_add_favorite_failure, it) }
    }
}

/** Long-pressing a thread card: a look at its whole first floor, over what can be done with it. */
@Stable
class ThreadPeeker internal constructor(
    private val show: (ThreadInfo, Rect, Offset, ImmutableList<DislikeOption>, (Long, ImmutableList<DislikeOption>) -> Unit) -> Unit,
) {
    /**
     * Peeks at [thread], whose card is at [from] in the window and was pressed [at], offering the
     * server's [options] for not wanting it besides storing, sharing and copying its link.
     */
    fun peek(
        thread: ThreadInfo,
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
            viewModel.load(thread)
            val preview = viewModel.preview(thread)
            val items = buildList {
                add(ContextMenuItem(Icons.Rounded.StarBorder, collect) { viewModel.store(thread) })
                addAll(shareMenuItems(context, thread.threadId, thread.title))
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
                    val posts by viewModel.firstPosts.collectAsStateWithLifecycle()
                    ThreadPeek(preview = preview, firstPost = posts[thread.threadId])
                },
            )
        }
    }
}

/**
 * A thread's first floor in full, as the thread page shows it; what the feed knew of it until the
 * floor loads, crossfading into the floor when it does.
 */
@Composable
fun ThreadPeek(preview: ThreadPreview, firstPost: Post?, modifier: Modifier = Modifier) {
    val known = remember(preview) {
        preview.author?.let { author ->
            Post(
                id = 0,
                floor = 1,
                title = preview.title,
                time = preview.time,
                author = author,
                content = preview.content,
                agree = Agree(0, false),
                subPosts = persistentListOf(),
                subPostCount = 0,
            )
        }
    }
    Crossfade(targetState = firstPost ?: known, modifier = modifier, label = "firstPost") { post ->
        if (post != null) {
            FirstPostContent(
                post = post,
                title = post.title.ifBlank { preview.title },
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
        }
    }
}
