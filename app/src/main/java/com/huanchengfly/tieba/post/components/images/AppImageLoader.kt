package com.huanchengfly.tieba.post.components.images

import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okhttp3.OkHttpClient

/**
 * The app's image loader: downloads through OkHttp, reporting progress to [ImageDownloadProgress],
 * and plays GIFs, and on newer systems animated WebP and HEIF too.
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(AnimatedImageDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .build()
}
