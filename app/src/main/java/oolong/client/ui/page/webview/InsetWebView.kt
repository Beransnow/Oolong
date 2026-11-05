package oolong.client.ui.page.webview

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebView
import androidx.core.animation.doOnEnd
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A web view whose page starts [topInset] below its top, as a list's content padding would put it,
 * and which slides up under what is above it as the page is scrolled.
 *
 * A web view can't pad its own page, so the view itself is moved instead: it sits [topInset] down
 * while the inset shows, and a drag spends that room first, moving the view rather than the page,
 * before the page scrolls. A drag back down gives the room back first, wherever the page is, as a
 * browser's toolbar comes back. While the inset moves, the page's touch is called off, so it never
 * takes a drag it did not see for a tap; what the inset can't take starts the page's touch again
 * from where the finger is.
 */
@SuppressLint("ViewConstructor")
class InsetWebView(context: Context) : WebView(context) {
    /** How far down the page starts at rest, in pixels. */
    var topInset: Int = 0
        set(value) {
            if (field == value) return
            field = value
            setConsumed(consumed.coerceAtMost(value))
        }

    /** How much of [topInset] the page has moved up into, from 0 to [topInset]. */
    var consumed: Int = 0
        private set

    /** Called with how far the page is from its resting place: what of the inset it took, and its scroll. */
    var onScrolled: (consumed: Int, scrollY: Int) -> Unit = { _, _ -> }

    private var lastRawY = 0f
    private var downRawX = 0f
    private var downRawY = 0f

    /** Null until the drag shows which way it goes; only an up-and-down one moves the inset. */
    private var vertical: Boolean? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var snap: ValueAnimator? = null

    /**
     * Whether the view reaches the bottom of its room. At rest with the inset showing, it is that much
     * shorter, so the page's bottom isn't pushed off screen; it grows as soon as the page moves up,
     * so the page lays out again once a gesture rather than every frame.
     */
    private var expanded = false
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }
    private var touching = false

    /** Gives back the whole inset, as when a new page opens. */
    fun showInset() {
        snap?.cancel()
        setConsumed(0)
        if (!touching) expanded = false
    }

    /** Whether the page follows the current touch; it is called off while the touch moves the inset. */
    private var pageFollowing = false

    /** Whether the page's touch was started again by the view after the inset moved, and where. */
    private var pageRestarted = false
    private var pageDownRawY = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                snap?.cancel()
                touching = true
                lastRawY = event.rawY
                downRawX = event.rawX
                downRawY = event.rawY
                vertical = null
                pageFollowing = true
                pageRestarted = false
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = lastRawY - event.rawY
                lastRawY = event.rawY
                if (vertical == null) {
                    val x = abs(event.rawX - downRawX)
                    val y = abs(event.rawY - downRawY)
                    // A swipe across, as through a carousel, leaves the inset be
                    if (x > touchSlop || y > touchSlop) vertical = y > x
                }
                // Zooming with two fingers moves the page, not the inset
                if (vertical == true && event.pointerCount == 1 && topInset > 0) {
                    val before = consumed
                    // Up spends the inset before the page scrolls; down gives it back first
                    val target = (consumed + dy).roundToInt().coerceIn(0, topInset)
                    val moved = (target - before).toFloat()
                    if (moved != 0f) {
                        setConsumed(target)
                        // A touch the page saw stand still would end as a tap on it
                        if (pageFollowing) {
                            sendToPage(event, MotionEvent.ACTION_CANCEL, 0f)
                            pageFollowing = false
                        }
                    }
                    if (!pageFollowing) {
                        // What the inset could not take scrolls the page, from where the finger is now:
                        // the view just moved under it by [moved]
                        if (abs(dy - moved) >= 1f) {
                            sendToPage(event, MotionEvent.ACTION_DOWN, moved)
                            pageFollowing = true
                            pageRestarted = true
                            pageDownRawY = event.rawY
                        }
                        return true
                    }
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> lastRawY = event.rawY

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touching = false
                settle()
                if (consumed == 0) expanded = false
                if (!pageFollowing) return true
                // Started again by the view and barely moved since: the page must not take it for a tap
                if (pageRestarted && abs(event.rawY - pageDownRawY) < touchSlop) {
                    sendToPage(event, MotionEvent.ACTION_CANCEL, 0f)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    /** Hands the page a copy of [event] as [action], moved down by [shift]. */
    private fun sendToPage(event: MotionEvent, action: Int, shift: Float) {
        val copy = MotionEvent.obtain(event)
        copy.action = action
        copy.offsetLocation(0f, shift)
        super.onTouchEvent(copy)
        copy.recycle()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val shrink = if (expanded) 0 else topInset
        val height = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY && shrink > 0) {
            MeasureSpec.makeMeasureSpec((MeasureSpec.getSize(heightMeasureSpec) - shrink).coerceAtLeast(0), MeasureSpec.EXACTLY)
        } else heightMeasureSpec
        super.onMeasure(widthMeasureSpec, height)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        onScrolled(consumed, t)
    }

    /** Let go halfway, the inset goes to whichever end is nearer. */
    private fun settle() {
        if (consumed == 0 || consumed == topInset) return
        val end = if (consumed > topInset / 2) topInset else 0
        snap = ValueAnimator.ofInt(consumed, end).apply {
            duration = (SnapMillisPerInset * abs(end - consumed) / topInset.coerceAtLeast(1)).toLong().coerceAtLeast(MinSnapMillis)
            addUpdateListener { setConsumed(it.animatedValue as Int) }
            doOnEnd {
                snap = null
                if (consumed == 0 && !touching) expanded = false
            }
            start()
        }
    }

    private fun setConsumed(value: Int) {
        consumed = value
        if (value > 0) expanded = true
        translationY = (topInset - value).toFloat()
        onScrolled(value, scrollY)
    }

    private companion object {
        const val SnapMillisPerInset = 250f
        const val MinSnapMillis = 80L
    }
}
