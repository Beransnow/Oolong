package com.huanchengfly.tieba.post.ui.page.reply

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.api.retrofit.exception.getErrorCode
import com.huanchengfly.tieba.post.api.retrofit.exception.getErrorMessage
import com.huanchengfly.tieba.post.arch.Actions
import com.huanchengfly.tieba.post.arch.ApplicationScope
import com.huanchengfly.tieba.post.arch.GlobalEvent
import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.arch.emitGlobalEvent
import com.huanchengfly.tieba.post.database.DraftDao
import com.huanchengfly.tieba.post.database.DraftEntity
import com.huanchengfly.tieba.post.repository.PostRepository
import com.huanchengfly.tieba.post.repository.PostedReply
import com.huanchengfly.tieba.post.repository.ReplyTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReplyPanelType {
    NONE,
    EMOJI,
    IMAGE,
    VOICE
}

@Immutable
data class ReplyState(
    val uploading: Boolean = false,
    val sending: Boolean = false,
    /** Whether the reply went through, after which the draft is no longer saved. */
    val sent: Boolean = false,
    val panel: ReplyPanelType = ReplyPanelType.NONE,
    val images: ImmutableList<String> = persistentListOf(),
    /** Whether to upload the images as they are rather than compressed. */
    val originalImages: Boolean = false,
) {
    val busy: Boolean get() = uploading || sending
}

/**
 * Writing a reply: the panel shown under the editor, the pictures picked, and sending, which
 * uploads the pictures first and appends them to the text.
 */
@HiltViewModel
class ReplyViewModel @Inject constructor(
    private val repository: PostRepository,
    private val drafts: DraftDao,
    private val messages: MessageController,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val actions = Actions(viewModelScope)
    private val _state = MutableStateFlow(ReplyState())
    val state: StateFlow<ReplyState> = _state.asStateFlow()

    private val _sent = Channel<PostedReply>(Channel.BUFFERED)

    /** Each reply that went through, once. */
    val sent: Flow<PostedReply> = _sent.receiveAsFlow()

    fun switchPanel(panel: ReplyPanelType) = _state.update { it.copy(panel = panel) }

    fun addImages(uris: List<String>) = _state.update { it.copy(images = (it.images + uris).toImmutableList()) }

    fun removeImage(index: Int) = _state.update { state ->
        state.copy(images = state.images.filterIndexed { i, _ -> i != index }.toImmutableList())
    }

    fun setOriginalImages(original: Boolean) = _state.update { it.copy(originalImages = original) }

    /** The unsent reply saved under [hash], a hash of what it replies to. */
    suspend fun draft(hash: String): String? = drafts.content(hash)

    fun saveDraft(hash: String, text: String) {
        appScope.launch { drafts.upsert(DraftEntity(hash, text)) }
    }

    fun deleteDraft(hash: String) {
        appScope.launch { drafts.delete(hash) }
    }

    /**
     * Uploads the picked pictures, if any, and posts [text] with them to [target].
     *
     * @param tbs a fresher `tbs` than the account's, such as the one the thread page returned
     */
    fun send(target: ReplyTarget, text: String, tbs: String?) = actions.launch("send") {
        val images = _state.value.images
        val content = if (images.isEmpty()) {
            text
        } else {
            _state.update { it.copy(uploading = true) }
            val pictures = try {
                repository.uploadPictures(target.forumName, images, _state.value.originalImages)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                messages.showError(R.string.toast_upload_image_failed, e)
                null
            } finally {
                _state.update { it.copy(uploading = false) }
            }
            pictures ?: return@launch
            "$text\n${pictures.joinToString("\n")}"
        }

        _state.update { it.copy(sending = true) }
        try {
            val reply = repository.reply(target, content, tbs)
            _state.update { it.copy(sending = false, sent = true) }
            announce(target, reply)
            _sent.send(reply)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _state.update { it.copy(sending = false) }
            messages.show(R.string.toast_reply_failed, e.getErrorCode(), e.getErrorMessage())
        }
    }

    /** Tells the thread and floor pages about the new reply, so that they can show it. */
    private fun announce(target: ReplyTarget, reply: PostedReply) {
        val event = if (target.postId != null) {
            GlobalEvent.ReplySuccess(target.threadId, target.postId, target.postId, target.subPostId, reply.postId)
        } else {
            GlobalEvent.ReplySuccess(target.threadId, reply.postId)
        }
        // The page closes on success, so the event must outlive this view model
        appScope.launch { emitGlobalEvent(event) }
    }
}
