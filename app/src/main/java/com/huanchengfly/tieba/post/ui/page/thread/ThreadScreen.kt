package com.huanchengfly.tieba.post.ui.page.thread

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.ScaffoldState
import androidx.compose.material.Text
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.model.Post
import com.huanchengfly.tieba.post.repository.ThreadSort
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.widgets.compose.ConfirmDialog
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBarFadeBand
import com.huanchengfly.tieba.post.ui.widgets.compose.PromptDialog
import com.huanchengfly.tieba.post.ui.widgets.compose.animateHiddenFraction
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberDialogState
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberHideOnScrollState
import kotlinx.coroutines.launch
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
    sheetState: ModalBottomSheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden, skipHalfExpanded = true),
) {
    val coroutineScope = rememberCoroutineScope()
    val jumpDialog = rememberDialogState()
    val deleteDialog = rememberDialogState()
    var deleting by remember { mutableStateOf<Post?>(null) }
    val newestFirst = state.query.sort == ThreadSort.NewestFirst
    val viewer = state.viewer.user

    val handleEvent: (ThreadEvent) -> Unit = { event ->
        when (event) {
            ThreadEvent.OpenMenu -> coroutineScope.launch {
                if (sheetState.isVisible) sheetState.hide() else sheetState.show()
            }

            is ThreadEvent.DeletePost -> {
                deleting = event.post
                deleteDialog.show()
            }

            else -> onEvent(event)
        }
    }

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        sheetBackgroundColor = ExtendedTheme.colors.windowBackground,
        scrimColor = Color.Transparent,
        sheetContent = {
            ThreadMenu(
                seeLz = state.query.seeLz,
                stored = state.thread?.isStored == true,
                immersive = state.immersive,
                newestFirst = newestFirst,
                canDelete = viewer != null && state.thread?.author?.id == viewer.id,
                onEvent = { event ->
                    coroutineScope.launch { sheetState.hide() }
                    onEvent(event)
                },
                onJumpToPage = {
                    coroutineScope.launch { sheetState.hide() }
                    jumpDialog.show()
                },
                onDelete = {
                    coroutineScope.launch { sheetState.hide() }
                    deleting = null
                    deleteDialog.show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            )
        },
        modifier = modifier,
    ) {
        val hide = rememberHideOnScrollState()
        val hiddenFraction by hide.animateHiddenFraction()
        val statusBarTop = WindowInsets.statusBars.union(WindowInsets.displayCutout).getTop(LocalDensity.current)
        var contentTop by remember { mutableIntStateOf(0) }
        val fadeBand = with(LocalDensity.current) { GlassTopBarFadeBand.roundToPx() }
        val headerPinned by remember(listState, statusBarTop, fadeBand) {
            derivedStateOf {
                // The pinned header sits above the bar's fade band, which slides away with the bar's rows
                val barHiddenBy = ((contentTop - fadeBand - statusBarTop) * hiddenFraction).roundToInt()
                listState.isThreadHeaderPinned(fadeBand + barHiddenBy)
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
                        { ThreadHeader(state, handleEvent, backgroundColor = Color.Transparent) }
                    } else null,
                ) { hiddenFraction }
            },
            bottomBar = {
                val thread = state.thread
                if (thread != null) {
                    ThreadReplyBar(
                        viewer = viewer,
                        agree = thread.agree,
                        hideReply = preferences.hideReply,
                        onEvent = handleEvent,
                    ) { hiddenFraction }
                }
            },
        ) { padding ->
            val density = LocalDensity.current
            SideEffect { contentTop = with(density) { padding.calculateTopPadding().roundToPx() } }
            ThreadContent(state, preferences, listState, padding, handleEvent)
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
