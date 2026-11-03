package oolong.client.ui.page.reply

import android.os.CancellationSignal
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.compose.animation.core.Animatable
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsAnimationControlListenerCompat
import androidx.core.view.WindowInsetsAnimationControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import oolong.client.findActivity
import oolong.client.ui.common.theme.compose.Motion
import kotlin.math.roundToInt

/**
 * Moves the keyboard with a finger, so pulling the composer down takes the keyboard along. The
 * system hands the keyboard over only from Android 11; before that [lower] does nothing and
 * [controlled] stays false.
 */
internal class KeyboardDrag(private val view: View) {
    private var controller: WindowInsetsAnimationControllerCompat? = null
    private var signal: CancellationSignal? = null

    /** How far the keyboard should be lowered, kept until the system hands it over. */
    private var lowered = 0f

    /** Whether the keyboard follows [lower]. */
    val controlled: Boolean
        get() = controller != null

    /** Asks the system for the keyboard. It arrives a frame or two later. */
    fun start() {
        if (signal != null) return
        val window = view.context.findActivity()?.window ?: return
        val cancel = CancellationSignal()
        signal = cancel
        WindowInsetsControllerCompat(window, view).controlWindowInsetsAnimation(
            WindowInsetsCompat.Type.ime(),
            -1,
            LinearInterpolator(),
            cancel,
            object : WindowInsetsAnimationControlListenerCompat {
                override fun onReady(controller: WindowInsetsAnimationControllerCompat, types: Int) {
                    this@KeyboardDrag.controller = controller
                    lower(lowered)
                }

                override fun onFinished(controller: WindowInsetsAnimationControllerCompat) = reset()

                override fun onCancelled(controller: WindowInsetsAnimationControllerCompat?) = reset()
            },
        )
    }

    /** Lowers the keyboard [by] pixels from where it shows. */
    fun lower(by: Float) {
        lowered = by
        val controller = controller ?: return
        val shown = controller.shownStateInsets.bottom
        val hidden = controller.hiddenStateInsets.bottom
        place(controller, (shown - by).coerceIn(hidden.toFloat(), shown.toFloat()))
    }

    /**
     * Lets go of the keyboard at [velocity], in pixels a second downwards: it goes the rest of the
     * way up when [show], or down, and is handed back to the system there.
     */
    suspend fun release(show: Boolean, velocity: Float) {
        val controller = controller
        if (controller == null) {
            signal?.cancel()
            reset()
            return
        }
        val shown = controller.shownStateInsets.bottom.toFloat()
        val hidden = controller.hiddenStateInsets.bottom.toFloat()
        val bottom = Animatable(controller.currentInsets.bottom.toFloat())
        bottom.animateTo(
            targetValue = if (show) shown else hidden,
            animationSpec = if (show) Motion.settle() else Motion.dismiss(),
            initialVelocity = -velocity,
        ) {
            if (this@KeyboardDrag.controller === controller) place(controller, value.coerceIn(hidden, shown))
        }
        if (this.controller === controller && controller.isReady) controller.finish(show)
    }

    private fun place(controller: WindowInsetsAnimationControllerCompat, bottom: Float) {
        val shown = controller.shownStateInsets.bottom
        val hidden = controller.hiddenStateInsets.bottom
        val fraction = if (shown == hidden) 1f else (shown - bottom) / (shown - hidden)
        controller.setInsetsAndAlpha(Insets.of(0, 0, 0, bottom.roundToInt()), 1f, fraction.coerceIn(0f, 1f))
    }

    private fun reset() {
        controller = null
        signal = null
        lowered = 0f
    }
}
