package com.huanchengfly.tieba.post.ui.page.thread

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarResult
import androidx.compose.material.Text
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.GlobalEvent
import com.huanchengfly.tieba.post.arch.onGlobalEvent
import com.huanchengfly.tieba.post.model.Post
import com.huanchengfly.tieba.post.rememberPreferenceAsState
import com.huanchengfly.tieba.post.repository.ThreadSort
import com.huanchengfly.tieba.post.ui.navigation.CopyTextKey
import com.huanchengfly.tieba.post.ui.navigation.ForumKey
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.navigation.ReplyKey
import com.huanchengfly.tieba.post.ui.navigation.SubPostsSheetKey
import com.huanchengfly.tieba.post.ui.navigation.ThreadKey
import com.huanchengfly.tieba.post.ui.navigation.UserProfileKey
import com.huanchengfly.tieba.post.ui.page.ProvideNavigator
import com.huanchengfly.tieba.post.ui.page.destinations.ThreadPageDestination
import com.huanchengfly.tieba.post.ui.widgets.compose.ConfirmDialog
import com.huanchengfly.tieba.post.ui.widgets.compose.MyBackHandler
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberDialogState
import com.huanchengfly.tieba.post.utils.TiebaUtil
import kotlinx.coroutines.launch

/**
 * The thread page: connects [ThreadScreen] to its view model, navigation and the things only a
 * page can do, such as sharing.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ThreadRoute(
    navigator: Navigator,
    from: String,
    extra: ThreadPageExtra?,
    viewModel: ThreadViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val scaffoldState = rememberScaffoldState()
    val sheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden, skipHalfExpanded = true)
    val hideReply by rememberPreferenceAsState(booleanPreferencesKey("hideReply"), false)
    val hideBlocked by rememberPreferenceAsState(booleanPreferencesKey("hideBlockedContent"), false)

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                ThreadEffect.NavigateUp -> navigator.navigateUp()
                ThreadEffect.ScrollToFirstReply -> listState.scrollToItem(FIRST_REPLY_INDEX)
                is ThreadEffect.ScrollToPost -> viewModel.state.value.indexOfPost(effect.postId)
                    ?.let { listState.scrollToItem(it) }
            }
        }
    }

    onGlobalEvent<GlobalEvent.ReplySuccess>(filter = { it.threadId == state.query.threadId }) { event ->
        if (event.postId != null) {
            viewModel.onReplySent(event.newSubPostId ?: event.newPostId, inPost = event.postId)
        } else {
            viewModel.onReplySent(event.newPostId)
        }
    }

    // Opened from the thread store while the thread has posts past the stored mark
    var offeredStoreUpdate by rememberSaveable { mutableStateOf(false) }
    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        if (offeredStoreUpdate || from != ThreadPageFrom.FROM_STORE || extra !is ThreadPageFromStoreExtra) {
            return@LaunchedEffect
        }
        offeredStoreUpdate = true
        val result = scaffoldState.snackbarHostState.showSnackbar(
            resources.getString(R.string.message_store_thread_update, extra.maxFloor),
            resources.getString(R.string.button_load_new),
            SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.jumpToPost(extra.maxPid)
    }

    val storeMarkDialog = rememberDialogState()
    var storeMarkUpdate by remember { mutableStateOf<Post?>(null) }
    MyBackHandler(enabled = !sheetState.isVisible, currentScreen = ThreadPageDestination) {
        val update = viewModel.storeMarkUpdate()
        if (update != null) {
            storeMarkUpdate = update
            storeMarkDialog.show()
        } else {
            navigator.navigateUp()
        }
    }
    ConfirmDialog(
        dialogState = storeMarkDialog,
        onConfirm = { storeMarkUpdate?.let(viewModel::leaveUpdatingStoreMark) },
        onCancel = { navigator.navigateUp() },
    ) {
        Text(text = stringResource(R.string.message_update_collect_mark, storeMarkUpdate?.floor ?: 0))
    }

    val onEvent: (ThreadEvent) -> Unit = { event ->
        val thread = state.thread
        when (event) {
            ThreadEvent.Back -> navigator.navigateUp()
            ThreadEvent.Refresh -> viewModel.refresh()
            ThreadEvent.Retry -> viewModel.retry()
            ThreadEvent.LoadMore -> viewModel.loadMore()
            ThreadEvent.LoadLatest -> viewModel.loadLatest()
            ThreadEvent.LoadPrevious -> viewModel.loadPrevious()
            is ThreadEvent.SetSeeLz -> viewModel.setSeeLz(event.seeLz)
            ThreadEvent.ToggleSort -> viewModel.setSort(
                if (state.query.sort == ThreadSort.NewestFirst) ThreadSort.OldestFirst else ThreadSort.NewestFirst
            )

            ThreadEvent.ToggleImmersive -> viewModel.toggleImmersive()
            is ThreadEvent.JumpToPage -> viewModel.jumpToPage(event.page)
            ThreadEvent.OpenMenu -> Unit
            ThreadEvent.AgreeThread -> viewModel.agreeThread()
            is ThreadEvent.AgreePost -> viewModel.agreePost(event.postId)
            ThreadEvent.OpenForum -> thread?.let { navigator.navigate(ForumKey(it.forum.name)) }
            is ThreadEvent.OpenUser -> navigator.navigate(UserProfileKey(event.userId))
            is ThreadEvent.Reply -> if (thread != null) {
                val replyTo = event.subPost?.author ?: event.post?.author
                navigator.navigate(
                    ReplyKey(
                        forumId = thread.forum.id,
                        forumName = thread.forum.name,
                        threadId = thread.id,
                        postId = event.post?.id,
                        subPostId = event.subPost?.id,
                        replyUserId = replyTo?.id,
                        replyUserName = replyTo?.displayName,
                        replyUserPortrait = replyTo?.portrait,
                    )
                )
            }

            is ThreadEvent.OpenSubPosts -> if (thread != null) {
                navigator.navigate(
                    SubPostsSheetKey(
                        forumId = thread.forum.id,
                        threadId = thread.id,
                        postId = event.post.id,
                        subPostId = event.subPostId,
                        loadFromSubPost = false,
                    )
                )
            }

            is ThreadEvent.OpenOriginThread ->
                navigator.navigate(ThreadKey(threadId = event.threadId, forumId = event.forumId))

            is ThreadEvent.Copy -> navigator.navigate(CopyTextKey(event.text))
            is ThreadEvent.Report -> coroutineScope.launch {
                TiebaUtil.reportPost(context, navigator, event.postId.toString())
            }

            ThreadEvent.ReportThread -> thread?.let {
                coroutineScope.launch { TiebaUtil.reportPost(context, navigator, it.firstPostId.toString()) }
            }

            ThreadEvent.Share -> TiebaUtil.shareText(
                context,
                "https://tieba.baidu.com/p/${state.query.threadId}",
                thread?.title ?: state.preview?.title,
            )

            ThreadEvent.CopyLink -> TiebaUtil.copyText(
                context,
                "https://tieba.baidu.com/p/${state.query.threadId}?see_lz=${if (state.query.seeLz) 1 else 0}"
            )

            is ThreadEvent.StoreAt -> viewModel.addStore(event.post)
            ThreadEvent.StoreAtLastRead -> viewModel.addStore()
            ThreadEvent.RemoveStore -> viewModel.removeStore()
            is ThreadEvent.DeletePost -> viewModel.deletePost(event.post)
            ThreadEvent.DeleteThread -> viewModel.deleteThread()
            is ThreadEvent.ReadUpTo -> viewModel.onReadUpTo(event.postId)
        }
    }

    ProvideNavigator(navigator = navigator) {
        ThreadScreen(
            state = state,
            onEvent = onEvent,
            preferences = ThreadPreferences(hideReply = hideReply, hideBlocked = hideBlocked),
            listState = listState,
            scaffoldState = scaffoldState,
            sheetState = sheetState,
        )
    }
}
