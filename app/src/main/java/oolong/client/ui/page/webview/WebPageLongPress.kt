package oolong.client.ui.page.webview

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.MotionEvent
import android.webkit.WebView
import androidx.compose.ui.geometry.Offset

/** What was pressed and held on a page: a picture, a link, or a picture that is a link. */
internal data class PageTarget(
    val image: String? = null,
    val link: String? = null,
    /** Where the finger was, in the window, for a menu to grow out of. */
    val at: Offset,
)

/**
 * Hands [onTarget] the picture or link pressed and held on the page. Anything else, as text, is left
 * to the web view, which selects it.
 */
@SuppressLint("ClickableViewAccessibility")
internal fun WebView.onLongPressTarget(onTarget: (PageTarget) -> Unit) {
    var down = Offset.Zero
    // Only noted, never taken: the view goes on handling the touch
    setOnTouchListener { _, event ->
        if (event.actionMasked == MotionEvent.ACTION_DOWN) down = Offset(event.rawX, event.rawY)
        false
    }
    setOnLongClickListener { view ->
        val hit = (view as WebView).hitTestResult
        val extra = hit.extra?.takeIf { it.startsWith("http", ignoreCase = true) }
        val at = windowPoint(view, down)
        when (hit.type) {
            WebView.HitTestResult.IMAGE_TYPE -> extra?.let { onTarget(PageTarget(image = it, at = at)) } != null

            WebView.HitTestResult.SRC_ANCHOR_TYPE -> extra?.let { onTarget(PageTarget(link = it, at = at)) } != null

            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                if (extra == null) return@setOnLongClickListener false
                // The link around the picture comes back on its own, once the page has looked it up
                val reply = Handler(Looper.getMainLooper()) { message ->
                    val link = message.data.getString("url")?.takeIf { it.startsWith("http", ignoreCase = true) }
                    onTarget(PageTarget(image = extra, link = link, at = at))
                    true
                }
                view.requestFocusNodeHref(Message.obtain(reply))
                true
            }

            else -> false
        }
    }
}

/** [screen], a point on the screen, in the window [view] is in. */
private fun windowPoint(view: WebView, screen: Offset): Offset {
    val onScreen = IntArray(2).also { view.getLocationOnScreen(it) }
    val inWindow = IntArray(2).also { view.getLocationInWindow(it) }
    return Offset(screen.x - onScreen[0] + inWindow[0], screen.y - onScreen[1] + inWindow[1])
}
