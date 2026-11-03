package oolong.client.ui.page.photoview

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import coil3.memory.MemoryCache
import oolong.client.R
import oolong.client.models.PhotoViewData
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.widgets.compose.ContextMenuHost
import oolong.client.ui.widgets.compose.ContextMenuItem
import oolong.client.utils.ImageUtil
import oolong.client.utils.TiebaUtil

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

/** Draws [content] with the picture viewer and context menus over it, ready for pictures to open. */
@Composable
fun PhotoViewerHost(content: @Composable () -> Unit) {
    val viewer = remember { PhotoViewer() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    CompositionLocalProvider(LocalPhotoViewer provides viewer) {
        Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
            ContextMenuHost(content = content)
            viewer.opening?.let { opening ->
                key(opening.id) {
                    PhotoViewerOverlay(viewer = viewer, opening = opening, origin = origin)
                }
            }
        }
    }
}

/** What a long press on the picture at [url], its original, offers: saving, sharing or copying its link. */
@Composable
fun photoMenuItems(url: String): List<ContextMenuItem> {
    val context = LocalContext.current
    val download = stringResource(id = R.string.desc_download_pic)
    val shareLabel = stringResource(id = R.string.title_share_pic)
    val copyLink = stringResource(id = R.string.title_copy_link)
    return remember(url, download, shareLabel, copyLink) {
        listOf(
            ContextMenuItem(Symbols.Download, download) { ImageUtil.download(context, url) },
            ContextMenuItem(Symbols.Share, shareLabel) { share(context, url) },
            ContextMenuItem(Symbols.Link, copyLink) { TiebaUtil.copyText(context, url) },
        )
    }
}
