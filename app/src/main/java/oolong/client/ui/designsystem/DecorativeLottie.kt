package oolong.client.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieConstants
import oolong.core.designsystem.theme.LocalReduceMotion

/**
 * An illustration drawn by Lottie, such as an empty or failed page's, played once or, when it
 * [loops], over and over. With motion reduced (design-system.md §7.2) it holds still instead: one
 * played once on its last frame, where it comes to rest, and a loop on its first, which it starts
 * and ends on. Not for a loader, which shows that something is under way and keeps moving.
 */
@Composable
fun DecorativeLottie(composition: LottieComposition?, modifier: Modifier = Modifier, loops: Boolean = false) {
    if (LocalReduceMotion.current) {
        LottieAnimation(composition = composition, progress = { if (loops) 0f else 1f }, modifier = modifier)
    } else {
        // Only played while it can be seen: on screen, as a page beside the one shown in a pager
        // is not, and with the app in front
        var onScreen by remember { mutableStateOf(true) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val resumed by lifecycle.currentStateFlow.collectAsState()
        LottieAnimation(
            composition = composition,
            iterations = if (loops) LottieConstants.IterateForever else 1,
            isPlaying = onScreen && resumed.isAtLeast(Lifecycle.State.RESUMED),
            modifier = modifier.onGloballyPositioned { onScreen = !it.boundsInWindow().isEmpty },
        )
    }
}
