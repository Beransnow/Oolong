package oolong.client.components.images

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

/**
 * How far the downloads of the images someone watches have got. Only watched URLs are tracked,
 * so that loading the rest costs nothing.
 */
object ImageDownloadProgress {
    private class Watch(val progress: MutableStateFlow<Float?>, var watchers: Int)

    private val watched = mutableMapOf<String, Watch>()

    /**
     * The fraction of [url] downloaded so far, null until bytes arrive or when the server does
     * not say how large it is. Images already cached never report any. Pair with [unwatch].
     */
    fun watch(url: String): StateFlow<Float?> = synchronized(watched) {
        val watch = watched.getOrPut(url) { Watch(MutableStateFlow(null), 0) }
        watch.watchers++
        watch.progress
    }

    fun unwatch(url: String) = synchronized(watched) {
        val watch = watched[url] ?: return@synchronized
        if (--watch.watchers <= 0) watched.remove(url)
    }

    internal val interceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        val progress = synchronized(watched) { watched[chain.request().url.toString()]?.progress }
        if (progress == null) response else response.newBuilder().body(ProgressBody(response.body, progress)).build()
    }

    private class ProgressBody(
        private val body: ResponseBody,
        private val progress: MutableStateFlow<Float?>,
    ) : ResponseBody() {
        private val source: BufferedSource by lazy {
            object : ForwardingSource(body.source()) {
                private var read = 0L

                override fun read(sink: Buffer, byteCount: Long): Long {
                    val count = super.read(sink, byteCount)
                    val total = body.contentLength()
                    if (count == -1L) {
                        progress.value = 1f
                    } else {
                        read += count
                        if (total > 0) progress.value = (read.toFloat() / total).coerceAtMost(1f)
                    }
                    return count
                }
            }.buffer()
        }

        override fun contentType(): MediaType? = body.contentType()

        override fun contentLength(): Long = body.contentLength()

        override fun source(): BufferedSource = source
    }
}

/** How far [url] has downloaded, as [ImageDownloadProgress.watch] reports it, while composed. */
@Composable
fun rememberImageDownloadProgress(url: String): State<Float?> {
    val progress = remember(url) { ImageDownloadProgress.watch(url) }
    DisposableEffect(url) { onDispose { ImageDownloadProgress.unwatch(url) } }
    return progress.collectAsState()
}
