package com.huanchengfly.tieba.post.ui.page.picker

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.post.model.DeviceAlbum
import com.huanchengfly.tieba.post.model.DeviceImage
import com.huanchengfly.tieba.post.repository.DeviceImageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ImagePickerState(
    /** Whether the pictures have been read at least once. */
    val loaded: Boolean = false,
    /** Every picture first, then each folder, the largest first. */
    val albums: ImmutableList<DeviceAlbum> = persistentListOf(),
    /** The album shown, null for every picture. */
    val albumId: Long? = null,
    /** The pictures of the album shown. */
    val images: ImmutableList<DeviceImage> = persistentListOf(),
    /** The pictures picked, in the order they were picked. */
    val selected: ImmutableList<Uri> = persistentListOf(),
    val maxItems: Int = 1,
) {
    val multiple: Boolean get() = maxItems > 1
}

/**
 * Picking pictures from the device's albums, for when the system photo picker is not there or
 * the user turned it off. Up to [EXTRA_MAX_ITEMS] pictures can be picked; the picks survive the
 * process being killed.
 */
@HiltViewModel
class ImagePickerViewModel @Inject constructor(
    private val repository: DeviceImageRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(
        ImagePickerState(
            selected = savedStateHandle.get<ArrayList<Uri>>(KEY_SELECTED).orEmpty().toImmutableList(),
            maxItems = (savedStateHandle.get<Int>(EXTRA_MAX_ITEMS) ?: 1).coerceAtLeast(1),
        )
    )
    val state: StateFlow<ImagePickerState> = _state.asStateFlow()

    private val _limitReached = Channel<Int>(Channel.CONFLATED)

    /** The limit, each time a pick is refused for going over it. */
    val limitReached: Flow<Int> = _limitReached.receiveAsFlow()

    private var images: List<DeviceImage> = emptyList()
    private var loading: Job? = null

    /** Reads the pictures again, as after the access to them changed. */
    fun load() {
        loading?.cancel()
        loading = viewModelScope.launch {
            images = try {
                repository.images()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Thrown when the access was taken away
                emptyList()
            }
            val readable = images.mapTo(HashSet()) { it.uri }
            _state.update { state ->
                val albums = albumsOf(images)
                val albumId = state.albumId?.takeIf { id -> albums.any { it.id == id } }
                state.copy(
                    loaded = true,
                    albums = albums,
                    albumId = albumId,
                    images = imagesIn(albumId),
                    selected = state.selected.filter { it in readable }.toImmutableList(),
                )
            }
            saveSelection()
        }
    }

    fun showAlbum(albumId: Long?) = _state.update { it.copy(albumId = albumId, images = imagesIn(albumId)) }

    /** Picks [uri], or puts it back if picked already. */
    fun toggle(uri: Uri) {
        val state = _state.value
        val selected = when {
            uri in state.selected -> state.selected - uri
            state.selected.size >= state.maxItems -> {
                _limitReached.trySend(state.maxItems)
                return
            }

            else -> state.selected + uri
        }
        _state.update { it.copy(selected = selected.toImmutableList()) }
        saveSelection()
    }

    private fun imagesIn(albumId: Long?): ImmutableList<DeviceImage> =
        if (albumId == null) images.toImmutableList() else images.filter { it.albumId == albumId }.toImmutableList()

    private fun saveSelection() {
        savedStateHandle[KEY_SELECTED] = ArrayList(_state.value.selected)
    }

    companion object {
        const val EXTRA_MAX_ITEMS = "max_items"
        private const val KEY_SELECTED = "selected"
    }
}

private fun albumsOf(images: List<DeviceImage>): ImmutableList<DeviceAlbum> {
    if (images.isEmpty()) return persistentListOf()
    val all = DeviceAlbum(id = null, name = "", cover = images.first().uri, count = images.size)
    // Grouping keeps each folder's pictures newest first, so the first is the cover
    val folders = images.groupBy { it.albumId }.map { (id, pictures) ->
        DeviceAlbum(id = id, name = pictures.first().albumName, cover = pictures.first().uri, count = pictures.size)
    }
    return (listOf(all) + folders.sortedByDescending { it.count }).toImmutableList()
}
