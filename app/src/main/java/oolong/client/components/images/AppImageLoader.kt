package oolong.client.components.images

import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.intercept.Interceptor
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

/**
 * The app's image loader: downloads through OkHttp, reporting progress to [ImageDownloadProgress],
 * and plays GIFs, and on newer systems animated WebP and HEIF too. Pictures are cached under their
 * address without the [ExpiringParameter], so one fetched again with a new one is not downloaded again.
 */
fun newAppImageLoader(context: Context): ImageLoader {
    val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val agent = System.getProperty("http.agent")
            val request = if (agent.isNullOrEmpty()) chain.request() else chain.request().newBuilder().header("User-Agent", agent).build()
            chain.proceed(request)
        }
        .addInterceptor(ImageDownloadProgress.interceptor)
        .build()
    return ImageLoader.Builder(context)
        .crossfade(true)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { client }))
            add(
                Interceptor { chain ->
                    val key = (chain.request.data as? String)?.let(::diskCacheKey)
                    if (key == null || chain.request.diskCacheKey != null) {
                        chain.proceed()
                    } else {
                        chain.withRequest(chain.request.newBuilder().diskCacheKey(key).build()).proceed()
                    }
                },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(AnimatedImageDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .build()
}

/** Pictures' addresses carry a signature that expires and changes between responses. */
private const val ExpiringParameter = "tbpicau"

/** The address without the [ExpiringParameter], or null when it has none. */
internal fun diskCacheKey(url: String): String? {
    if (!url.contains(ExpiringParameter)) return null
    return url.toHttpUrlOrNull()?.newBuilder()?.removeAllQueryParameters(ExpiringParameter)?.build()?.toString()
}
