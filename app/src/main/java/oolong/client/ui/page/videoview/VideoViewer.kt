package oolong.client.ui.page.videoview

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import oolong.client.api.getUserAgent
import oolong.core.network.TiebaProfile
import java.io.File

/** A video as a card shows it and the viewer plays it. */
@Immutable
data class ViewerVideo(
    val url: String,
    val cover: String,
    val width: Int,
    val height: Int,
    /** In seconds, or 0 when not known. */
    val duration: Int = 0,
    /** Who posted it, shown with [text] under it in the viewer. */
    val author: String? = null,
    val text: String? = null,
) {
    /** Names the video across pages and loads. */
    val key: String get() = url.substringBefore('?')

    /** Height over width, or 0 when not known. */
    val aspect: Float get() = if (width > 0 && height > 0) height.toFloat() / width else 0f
}

@Immutable
internal data class VideoOpening(
    val video: ViewerVideo,
    /** The cover's bounds in the window, or null to open in place. */
    val from: Rect?,
    val id: Long,
)

/** Where a cover is in the window, and whether enough of it shows to close back into. */
@Immutable
internal data class VideoThumbnail(val bounds: Rect, val visible: Boolean)

/**
 * The video viewer over the whole window, and the one player every video plays in. Covers ask it
 * to start loading when pressed and to open when tapped, and tell it where they are, so it can
 * grow out of them and shrink back into them.
 */
@Stable
class VideoViewer internal constructor(private val context: Context) {
    internal var opening by mutableStateOf<VideoOpening?>(null)
        private set

    internal val thumbnails = mutableStateMapOf<String, VideoThumbnail>()

    /** How far into each video it was left, as a fraction, for its cover to show. */
    internal val watched = mutableStateMapOf<String, Float>()

    /** Where each video was left, in milliseconds; the least recent first. */
    private val positions = LinkedHashMap<String, Long>(16, 0.75f, true)

    internal var player: ExoPlayer? = null
        private set

    /** The video [player] holds. */
    private var loaded: String? = null

    private var opened = 0L

    /** Starts loading [video], so it is ready by the time the viewer has grown. */
    fun preload(video: ViewerVideo) {
        val player = player ?: createPlayer().also { player = it }
        if (loaded == video.key) return
        loaded = video.key
        player.setMediaItem(MediaItem.fromUri(video.url), positions[video.key] ?: 0L)
        player.prepare()
    }

    /** Stops loading [video] when the press that started it did not open it. */
    fun cancelPreload(video: ViewerVideo) {
        if (opening == null && loaded == video.key) releasePlayer()
    }

    /** Plays [video], growing out of [from] when given. */
    fun open(video: ViewerVideo, from: Rect? = null) {
        preload(video)
        player?.playWhenReady = true
        opening = VideoOpening(video, from, ++opened)
    }

    /** Notes where [key]'s cover is; [bounds] null when it leaves the screen. */
    fun place(key: String, bounds: Rect?, visible: Boolean = true) {
        if (bounds == null) thumbnails.remove(key) else thumbnails[key] = VideoThumbnail(bounds, visible)
    }

    /** Keeps where [key] was left, [position] of [duration]; a video seen to the end starts over. */
    internal fun leave(key: String, position: Long, duration: Long) {
        if (duration <= 0) return
        val ended = position >= duration - EndMargin
        if (ended || position < StartMargin) {
            positions.remove(key)
            watched.remove(key)
            return
        }
        positions[key] = position
        watched[key] = position.toFloat() / duration
        while (positions.size > Remembered) {
            val oldest = positions.keys.first()
            positions.remove(oldest)
            watched.remove(oldest)
        }
    }

    internal fun closed() {
        opening = null
        releasePlayer()
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        loaded = null
    }

    @OptIn(UnstableApi::class)
    private fun createPlayer(): ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(VideoCache.dataSource(context)))
        // Give way to calls and other players, and stop when headphones are unplugged
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private companion object {
        const val Remembered = 300

        /** Left this close to either end, a video counts as not started or as seen through. */
        const val StartMargin = 3_000L
        const val EndMargin = 3_000L
    }
}

/** Videos seen before, kept on disk so opening one again or seeking back does not download it again. */
@OptIn(UnstableApi::class)
private object VideoCache {
    private const val MaxBytes = 256L * 1024 * 1024

    @Volatile
    private var cache: SimpleCache? = null

    private fun cache(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            File(context.cacheDir, "video"),
            LeastRecentlyUsedCacheEvictor(MaxBytes),
            StandaloneDatabaseProvider(context),
        ).also { cache = it }
    }

    fun dataSource(context: Context): DataSource.Factory {
        val app = context.applicationContext
        // The video servers may refuse requests that do not come from the app
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(getUserAgent("tieba/${TiebaProfile.PROTO_12_52.version}"))
        return CacheDataSource.Factory()
            .setCache(cache(app))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(app, http))
            .setCacheKeyFactory { spec -> spec.uri.buildUpon().clearQuery().build().toString() }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}

/** The window's video viewer; null where there is none, as in previews and tests. */
val LocalVideoViewer = staticCompositionLocalOf<VideoViewer?> { null }

/** Makes the window's video viewer. */
@Composable
internal fun rememberVideoViewer(context: Context): VideoViewer =
    remember(context) { VideoViewer(context.applicationContext) }
