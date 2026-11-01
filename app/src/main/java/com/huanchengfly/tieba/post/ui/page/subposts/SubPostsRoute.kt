package com.huanchengfly.tieba.post.ui.page.subposts

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huanchengfly.tieba.post.arch.GlobalEvent
import com.huanchengfly.tieba.post.arch.onGlobalEvent
import com.huanchengfly.tieba.post.model.User
import com.huanchengfly.tieba.post.rememberPreferenceAsState
import com.huanchengfly.tieba.post.ui.navigation.CopyTextKey
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.navigation.ThreadKey
import com.huanchengfly.tieba.post.ui.navigation.UserProfileKey
import com.huanchengfly.tieba.post.ui.page.ProvideNavigator
import com.huanchengfly.tieba.post.ui.page.reply.ReplyArgs
import com.huanchengfly.tieba.post.ui.page.reply.ReplyDialog
import com.huanchengfly.tieba.post.ui.page.thread.ThreadPreferences
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberDialogState
import com.huanchengfly.tieba.post.utils.AccountUtil.LocalAccount
import com.huanchengfly.tieba.post.utils.TiebaUtil
import kotlinx.coroutines.launch

/**
 * The sub-post page: connects [SubPostsScreen] to its view model, navigation and the reply dialog
 * it opens in place.
 */
@Composable
fun SubPostsRoute(
    navigator: Navigator,
    isSheet: Boolean,
    viewModel: SubPostsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val account = LocalAccount.current
    val listState = rememberLazyListState()
    val hideReply by rememberPreferenceAsState(booleanPreferencesKey("hideReply"), false)
    val hideBlocked by rememberPreferenceAsState(booleanPreferencesKey("hideBlockedContent"), false)

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                SubPostsEffect.NavigateUp -> navigator.navigateUp()
                is SubPostsEffect.ScrollToSubPost -> viewModel.state.value.indexOfSubPost(effect.subPostId)
                    ?.let { listState.scrollToItem(it) }
            }
        }
    }

    onGlobalEvent<GlobalEvent.ReplySuccess>(
        filter = { it.threadId == state.threadId && it.postId != null && it.postId == state.post?.id }
    ) { event ->
        event.newSubPostId?.let(viewModel::onReplySent)
    }

    val replyDialog = rememberDialogState()
    var replyArgs by remember { mutableStateOf<ReplyArgs?>(null) }
    replyArgs?.let { ReplyDialog(args = it, state = replyDialog) }

    fun reply(subPostId: Long? = null, replyTo: User? = null) {
        val post = state.post ?: return
        val forum = state.forum?.takeIf { it.name.isNotEmpty() } ?: return
        replyArgs = ReplyArgs(
            forumId = forum.id,
            forumName = forum.name,
            threadId = state.threadId,
            postId = post.id,
            subPostId = subPostId,
            replyUserId = replyTo?.id,
            replyUserName = replyTo?.displayName,
            replyUserPortrait = replyTo?.portrait,
        )
        replyDialog.show()
    }

    val onEvent: (SubPostsEvent) -> Unit = { event ->
        when (event) {
            SubPostsEvent.Back -> navigator.navigateUp()
            SubPostsEvent.Retry -> viewModel.retry()
            SubPostsEvent.LoadMore -> viewModel.loadMore()
            SubPostsEvent.LoadPrevious -> viewModel.loadPrevious()
            SubPostsEvent.OpenThread -> navigator.navigate(
                ThreadKey(
                    threadId = state.threadId,
                    forumId = state.forum?.id,
                    postId = state.post?.id ?: 0L,
                )
            )

            SubPostsEvent.AgreePost -> viewModel.agreePost()
            is SubPostsEvent.AgreeSubPost -> viewModel.agreeSubPost(event.subPostId)
            is SubPostsEvent.OpenUser -> navigator.navigate(UserProfileKey(event.userId))
            is SubPostsEvent.Reply -> reply(event.subPost?.id, event.subPost?.author ?: event.post.author)
            SubPostsEvent.ReplyToPost -> reply()
            is SubPostsEvent.Copy -> navigator.navigate(CopyTextKey(event.text))
            is SubPostsEvent.Report -> coroutineScope.launch {
                TiebaUtil.reportPost(context, navigator, event.postId.toString())
            }

            is SubPostsEvent.Delete -> viewModel.delete(event.subPost)
        }
    }

    ProvideNavigator(navigator = navigator) {
        SubPostsScreen(
            state = state,
            onEvent = onEvent,
            isSheet = isSheet,
            replyAvatar = account?.portrait,
            preferences = ThreadPreferences(hideReply = hideReply, hideBlocked = hideBlocked),
            listState = listState,
        )
    }
}
