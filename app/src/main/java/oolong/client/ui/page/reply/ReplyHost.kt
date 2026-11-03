package oolong.client.ui.page.reply

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import oolong.client.R
import oolong.client.arch.GlobalEvent
import oolong.client.arch.emitGlobalEvent
import oolong.client.arch.onGlobalEvent
import oolong.client.repository.ReplyTarget
import oolong.client.toMD5
import oolong.client.toastShort
import oolong.client.utils.TiebaUtil
import oolong.client.utils.appPreferences
import java.util.UUID

/**
 * Writing replies on a page: opens the composer for a thread, a floor or a reply in a floor,
 * keeps a draft for each, and sends. The page draws it through [ReplyChrome] with [Content] and
 * [Panel].
 */
@Stable
class ReplyComposer internal constructor(
    val chrome: ReplyChromeState,
    internal val editor: ReplyEditorState,
    internal val viewModel: ReplyViewModel,
    private val context: Context,
) {
    internal var target by mutableStateOf<ReplyTarget?>(null)
    internal var subPostAuthor by mutableStateOf<Pair<String, String>?>(null)
    internal var tbs: String? = null
    internal var showNotice by mutableStateOf(context.appPreferences.postOrReplyWarning)
    internal val imagePickerId = UUID.randomUUID().toString()

    /** The draft's key: what the reply answers, so each floor keeps its own. */
    internal val draftKey: String?
        get() = target?.let { "${it.threadId}_${it.postId}_${it.subPostId}".toMD5() }

    /**
     * Opens the composer to reply to [target], shown as [replyTo] at its top.
     *
     * @param subPostAuthor portrait and name of whoever wrote the reply in a floor being answered,
     *   whom the reply names at its start as the official client does
     * @param tbs a fresher `tbs` than the account's, such as the one the thread page returned
     */
    suspend fun open(
        target: ReplyTarget,
        replyTo: ReplyTo?,
        subPostAuthor: Pair<String, String>? = null,
        tbs: String? = null,
    ) {
        val sameTarget = this.target == target && chrome.surface == ChromeSurface.Composer
        this.target = target
        this.subPostAuthor = subPostAuthor
        this.tbs = tbs
        if (!sameTarget) {
            viewModel.begin()
            editor.setText(draftKey?.let { viewModel.draft(it) }.orEmpty())
        }
        chrome.openComposer(replyTo)
    }

    fun close() {
        editor.hideKeyboard()
        viewModel.switchPanel(ReplyPanelType.NONE)
        chrome.close()
    }

    internal fun onEvent(event: ReplyComposerEvent, coroutine: CoroutineScope) {
        val state = viewModel.state.value
        when (event) {
            ReplyComposerEvent.ReplyToThread -> {
                target = target?.copy(postId = null, subPostId = null, replyUserId = null)
                subPostAuthor = null
                chrome.replyTo = null
            }

            ReplyComposerEvent.ToggleFullScreen -> chrome.fullScreen = !chrome.fullScreen
            ReplyComposerEvent.DismissNotice -> {
                showNotice = false
                context.appPreferences.postOrReplyWarning = false
            }

            ReplyComposerEvent.OpenOfficialClient -> openOfficialClient()
            is ReplyComposerEvent.ShowPanel -> {
                if (event.panel == ReplyPanelType.NONE) editor.showKeyboard() else editor.hideKeyboard()
                viewModel.switchPanel(event.panel)
            }

            ReplyComposerEvent.PickImages -> coroutine.emitGlobalEvent(
                GlobalEvent.StartSelectImages(imagePickerId, MaxImages - state.images.size)
            )

            is ReplyComposerEvent.RemoveImage -> viewModel.removeImage(event.index)
            is ReplyComposerEvent.SetOriginalImages -> viewModel.setOriginalImages(event.original)
            is ReplyComposerEvent.SetWithTail -> viewModel.setWithTail(event.withTail)
            ReplyComposerEvent.EditText -> if (state.panel != ReplyPanelType.NONE) {
                viewModel.switchPanel(ReplyPanelType.NONE)
            }

            ReplyComposerEvent.Send -> {
                val target = target ?: return
                val author = subPostAuthor
                val text = editor.text
                // A reply to a reply in a floor names its author first, as the official client does
                val content = if (author != null) "回复 #(reply, ${author.first}, ${author.second}) :$text" else text
                viewModel.send(target, content, tbs, tail = context.appPreferences.littleTail)
            }
        }
    }

    /** Copies what is written and opens the same thread, at the same floor, in the official client. */
    private fun openOfficialClient() {
        val target = target ?: return
        if (editor.text.isNotBlank()) {
            TiebaUtil.copyText(context, editor.text, context.getString(R.string.reply_copied_for_official))
        }
        openInOfficialClient(context, target.threadId, target.postId)
    }
}

/** Opens thread [threadId] in the official client, at floor [postId] when there is one. */
fun openInOfficialClient(context: Context, threadId: Long, postId: Long?) {
    val uri = if (postId != null) {
        "com.baidu.tieba://unidispatch/pb?obj_locate=comment_lzl_cut_guide&obj_source=wise&obj_name=index&obj_param2=chrome&has_token=0&qd=scheme&refer=tieba.baidu.com&wise_sample_id=3000232_2&hightlight_anchor_pid=$postId&is_anchor_to_comment=1&comment_sort_type=0&fr=bpush&tid=$threadId"
    } else {
        "com.baidu.tieba://unidispatch/pb?obj_locate=pb_reply&obj_source=wise&obj_name=index&obj_param2=chrome&has_token=0&qd=scheme&refer=tieba.baidu.com&wise_sample_id=3000232_2-99999_9&fr=bpush&tid=$threadId"
    }
    val intent = Intent(Intent.ACTION_VIEW).setData(uri.toUri())
    val handlers = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        .filter { it.activityInfo.packageName != context.packageName }
    try {
        if (handlers.isEmpty()) context.toastShort(R.string.toast_official_client_not_install)
        else context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        context.toastShort(R.string.toast_official_client_not_install)
    }
}

/**
 * A composer for this page, its state kept by [viewModel] for as long as the page lives. Sending
 * saves nothing more and closes the composer; the page hears of the new reply through
 * [GlobalEvent.ReplySuccess].
 */
@OptIn(FlowPreview::class)
@Composable
fun rememberReplyComposer(
    chrome: ReplyChromeState = rememberReplyChromeState(),
    viewModel: ReplyViewModel = hiltViewModel(),
): ReplyComposer {
    val context = LocalContext.current
    val editor = rememberReplyEditorState()
    val composer = remember(chrome, viewModel) { ReplyComposer(chrome, editor, viewModel, context) }
    val coroutineScope = rememberCoroutineScope()

    // Keyboard up when the composer opens, down when it closes
    LaunchedEffect(composer) {
        snapshotFlow { chrome.surface == ChromeSurface.Composer }.collect { open ->
            if (open) {
                if (viewModel.state.value.panel == ReplyPanelType.NONE) editor.showKeyboard()
            } else {
                editor.hideKeyboard()
                viewModel.switchPanel(ReplyPanelType.NONE)
            }
        }
    }
    LaunchedEffect(composer) {
        snapshotFlow { editor.text }.drop(1).debounce(500).collect { text ->
            val key = composer.draftKey
            if (key != null && !viewModel.state.value.sent) viewModel.saveDraft(key, text)
        }
    }
    LaunchedEffect(composer) {
        viewModel.sent.collect { reply ->
            if (reply.expInc.isEmpty()) {
                context.toastShort(R.string.toast_reply_success_default)
            } else {
                context.toastShort(R.string.toast_reply_success, reply.expInc)
            }
            composer.draftKey?.let(viewModel::deleteDraft)
            editor.setText("")
            composer.close()
        }
    }
    onGlobalEvent<GlobalEvent.SelectedImages>(coroutineScope, filter = { it.id == composer.imagePickerId }) {
        viewModel.addImages(it.images.map(Uri::toString))
    }
    return composer
}

/** The composer's inside, for [ReplyChrome]'s composer slot. */
@Composable
fun ReplyComposer.Content(handle: @Composable () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val canAttachImages = target?.let { it.postId == null || it.postId == 0L } ?: true
    ReplyComposerContent(
        replyTo = chrome.replyTo,
        editor = editor,
        state = state,
        fullScreen = chrome.fullScreen,
        showNotice = showNotice,
        hasTail = !context.appPreferences.littleTail.isNullOrBlank(),
        canAttachImages = canAttachImages,
        onEvent = { onEvent(it, coroutineScope) },
        handle = handle,
    )
}

/** What shows in place of the keyboard, for [ReplyChrome]'s panel slot. */
@Composable
fun ReplyComposer.Panel() {
    EmoticonPanel(onEmoticonClick = { editor.insert("#(${it.name})") })
}

/** Whether a panel shows in place of the keyboard. */
@Composable
fun ReplyComposer.panelShown(): Boolean {
    val state by viewModel.state.collectAsStateWithLifecycle()
    return state.panel != ReplyPanelType.NONE
}
