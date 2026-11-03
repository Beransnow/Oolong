package oolong.client.ui.page.thread

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ScaffoldState
import androidx.compose.material.Text
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import oolong.client.R
import oolong.client.model.Post
import oolong.client.repository.ThreadSort
import oolong.client.ui.page.reply.ChromeSurface
import oolong.client.ui.page.reply.Content
import oolong.client.ui.page.reply.Panel
import oolong.client.ui.page.reply.ReplyCapsuleContent
import oolong.client.ui.page.reply.ReplyChrome
import oolong.client.ui.page.reply.ReplyChromeBarHeight
import oolong.client.ui.page.reply.ReplyChromeState
import oolong.client.ui.page.reply.ReplyComposer
import oolong.client.ui.page.reply.panelShown
import oolong.client.ui.page.reply.rememberReplyChromeState
import oolong.client.ui.widgets.compose.ConfirmDialog
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.PromptDialog
import oolong.client.ui.widgets.compose.animateHiddenFraction
import oolong.client.ui.widgets.compose.rememberDialogState
import oolong.client.ui.widgets.compose.rememberHideOnScrollState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ThreadScreen(
    state: ThreadState,
    onEvent: (ThreadEvent) -> Unit,
    modifier: Modifier = Modifier,
    preferences: ThreadPreferences = ThreadPreferences(),
    listState: LazyListState = rememberLazyListState(),
    scaffoldState: ScaffoldState = rememberScaffoldState(),
    chrome: ReplyChromeState = rememberReplyChromeState(),
    reply: ReplyComposer? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    val jumpDialog = rememberDialogState()
    val deleteDialog = rememberDialogState()
    var deleting by remember { mutableStateOf<Post?>(null) }
    val newestFirst = state.query.sort == ThreadSort.NewestFirst
    val viewer = state.viewer.user

    val handleEvent: (ThreadEvent) -> Unit = { event ->
        when (event) {
            ThreadEvent.OpenMenu -> chrome.openMenu()

            is ThreadEvent.DeletePost -> {
                deleting = event.post
                deleteDialog.show()
            }

            else -> onEvent(event)
        }
    }

    Box(modifier = modifier) {
        val hide = rememberHideOnScrollState()
        val hiddenFraction by hide.animateHiddenFraction()
        val statusBarTop = WindowInsets.statusBars.union(WindowInsets.displayCutout).getTop(LocalDensity.current)
        var contentTop by remember { mutableIntStateOf(0) }
        var pinnedHeaderHeight by remember { mutableIntStateOf(0) }
        val headerPinned by remember(listState, statusBarTop) {
            derivedStateOf {
                listState.isThreadHeaderPinned(((contentTop - statusBarTop) * hiddenFraction).roundToInt())
            }
        }
        // A thread that can't scroll the bar's height away would leave an empty band where the bar was
        val tooShortToHide by remember(listState) {
            derivedStateOf {
                !listState.canScrollForward &&
                    listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset < contentTop
            }
        }
        LaunchedEffect(hide) {
            snapshotFlow { hide.hidden && tooShortToHide }.collect { if (it) hide.show() }
        }
        GlassScaffold(
            modifier = Modifier.nestedScroll(hide.nestedScrollConnection),
            scaffoldState = scaffoldState,
            topBar = {
                ThreadTopBar(
                    forum = state.thread?.forum,
                    onEvent = handleEvent,
                    pinnedContent = if (state.thread != null && headerPinned) {
                        {
                            ThreadHeader(
                                state,
                                handleEvent,
                                backgroundColor = Color.Transparent,
                                modifier = Modifier.onSizeChanged { pinnedHeaderHeight = it.height },
                            )
                        }
                    } else null,
                ) { hiddenFraction }
            },
            // The capsules are drawn by the overlay, so the composer and the menu can grow out of
            // them; this keeps the content clear of them at rest
            bottomBar = {
                if (state.thread != null) {
                    Spacer(
                        modifier = Modifier
                            .windowInsetsPadding(
                                WindowInsets.navigationBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Bottom)
                            )
                            .height(ReplyChromeBarHeight)
                    )
                }
            },
            overlay = {
                val thread = state.thread
                if (thread != null) {
                    val draft = reply?.editor?.text.orEmpty()
                    ReplyChrome(
                        state = chrome,
                        replyCapsule = if (viewer != null && !preferences.hideReply) {
                            { ReplyCapsuleContent(viewer.portrait, draft) }
                        } else null,
                        actions = { ThreadActions(thread.agree, handleEvent) },
                        menu = {
                            ThreadMenu(
                                seeLz = state.query.seeLz,
                                stored = thread.isStored,
                                newestFirst = newestFirst,
                                canDelete = viewer != null && thread.author.id == viewer.id,
                                currentPage = state.currentPage,
                                totalPage = state.totalPage,
                                onEvent = { event ->
                                    chrome.close()
                                    onEvent(event)
                                },
                                onJumpToPage = {
                                    chrome.close()
                                    jumpDialog.show()
                                },
                                onDelete = {
                                    chrome.close()
                                    deleting = null
                                    deleteDialog.show()
                                },
                            )
                        },
                        menuFooter = { ThreadMenuFooter(thread.agree, handleEvent, onClose = chrome::close) },
                        composer = { handle -> reply?.Content(handle) },
                        panel = { reply?.Panel() },
                        panelShown = reply?.panelShown() ?: false,
                        onOpenComposer = { handleEvent(ThreadEvent.Reply(post = null)) },
                        onDismiss = { if (reply != null && chrome.surface == ChromeSurface.Composer) reply.close() else chrome.close() },
                        onExitFullScreen = { chrome.fullScreen = false },
                        onEnterFullScreen = { chrome.fullScreen = true },
                        hiddenFraction = { hiddenFraction },
                    )
                }
            },
        ) { padding ->
            val density = LocalDensity.current
            SideEffect { contentTop = with(density) { padding.calculateTopPadding().roundToPx() } }
            // Where the visible chrome ends, from the end of the top padding: the bar slides up as it
            // hides, and the pinned header hangs below it
            val chromeEnd = {
                val barHiddenBy = ((contentTop - statusBarTop) * hiddenFraction).roundToInt()
                -barHiddenBy + if (headerPinned) pinnedHeaderHeight else 0
            }
            ThreadContent(state, preferences, listState, padding, handleEvent, chromeEnd)
        }
    }

    ConfirmDialog(
        dialogState = deleteDialog,
        onConfirm = {
            val post = deleting
            onEvent(if (post == null) ThreadEvent.DeleteThread else ThreadEvent.DeletePost(post))
        },
    ) {
        Text(
            text = stringResource(
                id = R.string.message_confirm_delete,
                deleting?.let { stringResource(id = R.string.tip_post_floor, it.floor) }
                    ?: stringResource(id = R.string.this_thread)
            )
        )
    }

    PromptDialog(
        onConfirm = { it.toIntOrNull()?.let { page -> onEvent(ThreadEvent.JumpToPage(page)) } },
        dialogState = jumpDialog,
        onValueChange = { newVal, _ -> "^[0-9]*$".toRegex().matches(newVal) },
        title = { Text(text = stringResource(id = R.string.title_jump_page)) },
    ) {
        Text(text = stringResource(id = R.string.tip_jump_page, state.currentPage, state.totalPage))
    }
}
