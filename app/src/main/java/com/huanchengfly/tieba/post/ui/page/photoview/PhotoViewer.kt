package com.huanchengfly.tieba.post.ui.page.photoview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import coil3.memory.MemoryCache
import com.huanchengfly.tieba.post.models.PhotoViewData

/** Pictures opened out of a thumbnail: where it sat, and what it already showed. */
@Immutable
internal data class PhotoOpening(
    val data: PhotoViewData,
    /** The thumbnail's bounds in the window, or null to open in place. */
    val from: Rect?,
    /** The picture the thumbnail showed, to show until the original arrives. */
    val thumbnail: MemoryCache.Key?,
    /** Height over width of the picture, or 0 when not known. */
    val aspect: Float,
    val id: Long,
)

/** What can be done with a picture, offered where a long press on it landed. */
@Immutable
internal data class PhotoMenu(
    /** The picture's original. */
    val url: String,
    /** Where the press landed, in the window. */
    val at: Offset,
    val id: Long,
)

/** Where a thumbnail is in the window, and whether enough of it shows to close back into. */
@Immutable
internal data class PhotoThumbnail(val bounds: Rect, val visible: Boolean)

/**
 * The picture viewer over the whole window. Pages open it from their thumbnails, which tell it
 * where they are so it can grow out of them and shrink back into them.
 */
@Stable
class PhotoViewer internal constructor() {
    internal var opening by mutableStateOf<PhotoOpening?>(null)
        private set

    /** The thumbnails on screen, by picture id. */
    internal val thumbnails = mutableStateMapOf<String, PhotoThumbnail>()

    internal var menu by mutableStateOf<PhotoMenu?>(null)
        private set

    /** Where the finger that opened the menu is, in the window, while it stays down. */
    internal var menuFinger by mutableStateOf<Offset?>(null)
        private set

    /** Where that finger last lifted, counted so each lift is heard once. */
    internal var menuLift by mutableStateOf<Pair<Int, Offset>?>(null)
        private set

    private var opened = 0L

    /**
     * Shows [data], growing out of [from] when given.
     *
     * @param thumbnail what the thumbnail showed, kept on screen until the original arrives
     * @param aspect the picture's height over its width, or 0 when not known
     */
    fun open(data: PhotoViewData, from: Rect? = null, thumbnail: MemoryCache.Key? = null, aspect: Float = 0f) {
        opening = PhotoOpening(data, from, thumbnail, aspect, ++opened)
    }

    /**
     * Offers what can be done with the picture at [url] where a long press landed, [at] in the
     * window. The finger that pressed stays in charge until it lifts: report it with
     * [moveFinger] and [liftFinger].
     */
    fun showMenu(url: String, at: Offset) {
        menuFinger = null
        menu = PhotoMenu(url, at, ++opened)
    }

    /** The finger that opened the menu moved to [position] in the window. */
    fun moveFinger(position: Offset) {
        if (menu != null) menuFinger = position
    }

    /** The finger that opened the menu lifted at [position]; over an action, that action runs. */
    fun liftFinger(position: Offset) {
        if (menu == null) return
        menuFinger = null
        menuLift = ((menuLift?.first ?: 0) + 1) to position
    }

    internal fun closeMenu() {
        menu = null
        menuFinger = null
    }

    /** Notes where picture [picId]'s thumbnail is; [bounds] null when it leaves the screen. */
    fun place(picId: String, bounds: Rect?, visible: Boolean = true) {
        if (bounds == null) thumbnails.remove(picId) else thumbnails[picId] = PhotoThumbnail(bounds, visible)
    }

    internal fun closed() {
        opening = null
    }
}

/** The window's picture viewer; null where there is none, as in previews and tests. */
val LocalPhotoViewer = staticCompositionLocalOf<PhotoViewer?> { null }

/** Draws [content] with the picture viewer over it, ready for pictures to open. */
@Composable
fun PhotoViewerHost(content: @Composable () -> Unit) {
    val viewer = remember { PhotoViewer() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    CompositionLocalProvider(LocalPhotoViewer provides viewer) {
        Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
            content()
            viewer.menu?.let { menu ->
                key(menu.id) {
                    PhotoMenuOverlay(viewer = viewer, menu = menu, origin = origin)
                }
            }
            viewer.opening?.let { opening ->
                key(opening.id) {
                    PhotoViewerOverlay(viewer = viewer, opening = opening, origin = origin)
                }
            }
        }
    }
}
