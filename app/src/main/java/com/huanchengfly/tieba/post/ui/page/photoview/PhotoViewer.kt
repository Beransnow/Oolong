package com.huanchengfly.tieba.post.ui.page.photoview

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import coil3.memory.MemoryCache
import com.huanchengfly.tieba.post.models.PhotoViewData
import com.huanchengfly.tieba.post.rememberPreferenceAsState
import com.huanchengfly.tieba.post.ui.widgets.compose.ReduceTransparencyKey

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

/** A picture held up out of its thumbnail by a long press, with what can be done with it. */
@Immutable
internal data class PhotoPeek(
    val data: PhotoViewData,
    /** The thumbnail's bounds in the window. */
    val from: Rect,
    val thumbnail: MemoryCache.Key?,
    val aspect: Float,
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

    internal var peeking by mutableStateOf<PhotoPeek?>(null)
        private set

    /** Where the finger that held the picture up is, in the window, while it stays down. */
    internal var peekFinger by mutableStateOf<Offset?>(null)
        private set

    /** Where that finger last lifted, counted so each lift is heard once. */
    internal var peekLift by mutableStateOf<Pair<Int, Offset>?>(null)
        private set

    /** How far the page under a held-up picture is blurred, from 0 to 1. */
    internal var backdrop by mutableFloatStateOf(0f)

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
     * Holds the picture of [data] up out of its thumbnail at [from], over the blurred page, with
     * what can be done with it below. The finger that pressed stays in charge until it lifts:
     * report it with [moveFinger] and [liftFinger].
     */
    fun peek(data: PhotoViewData, from: Rect, thumbnail: MemoryCache.Key? = null, aspect: Float = 0f) {
        peekFinger = null
        peeking = PhotoPeek(data, from, thumbnail, aspect, ++opened)
    }

    /** The finger holding a picture up moved to [position] in the window. */
    fun moveFinger(position: Offset) {
        if (peeking != null) peekFinger = position
    }

    /** The finger holding a picture up lifted at [position]; over an action, that action runs. */
    fun liftFinger(position: Offset) {
        if (peeking == null) return
        peekFinger = null
        peekLift = ((peekLift?.first ?: 0) + 1) to position
    }

    internal fun closePeek() {
        peeking = null
        peekFinger = null
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
        val reduceTransparency by rememberPreferenceAsState(ReduceTransparencyKey, false)
        val blurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !reduceTransparency
        Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
            Box(
                modifier = Modifier.graphicsLayer {
                    val radius = viewer.backdrop * 24.dp.toPx()
                    renderEffect = if (blurs && radius > 0f) BlurEffect(radius, radius, TileMode.Decal) else null
                },
            ) {
                content()
            }
            viewer.peeking?.let { peek ->
                key(peek.id) {
                    PhotoPeekOverlay(viewer = viewer, peek = peek, origin = origin)
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
