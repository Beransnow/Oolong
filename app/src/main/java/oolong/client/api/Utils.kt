package oolong.client.api

import oolong.client.App
import oolong.client.App.ScreenInfo

/**
 * Used until WebView's own user agent is read: a current WebView's, which no longer names the
 * device or the full Chrome version.
 */
private const val defaultUserAgent: String =
    "Mozilla/5.0 (Linux; Android 10; K; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/135.0.0.0 Mobile Safari/537.36"

fun getUserAgent(appendString: String? = null): String {
    val append = " ${appendString?.trim()}".takeIf { !appendString.isNullOrEmpty() }.orEmpty()
    return "${App.Config.userAgent ?: defaultUserAgent}$append"
}

// ScreenInfo is only filled in once an activity starts; requests sent at startup need it earlier
fun getScreenHeight(): Int =
    ScreenInfo.EXACT_SCREEN_HEIGHT.takeIf { it > 0 } ?: App.INSTANCE.resources.displayMetrics.heightPixels

fun getScreenWidth(): Int =
    ScreenInfo.EXACT_SCREEN_WIDTH.takeIf { it > 0 } ?: App.INSTANCE.resources.displayMetrics.widthPixels

fun getScreenDensity(): Float =
    ScreenInfo.DENSITY.takeIf { it > 0 } ?: App.INSTANCE.resources.displayMetrics.density

fun Boolean.booleanToString(): String = if (this) "1" else "0"
