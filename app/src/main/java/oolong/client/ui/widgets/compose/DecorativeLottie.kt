package oolong.client.ui.widgets.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieConstants
import oolong.client.ui.common.theme.compose.LocalReduceMotion

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
        LottieAnimation(
            composition = composition,
            iterations = if (loops) LottieConstants.IterateForever else 1,
            modifier = modifier,
        )
    }
}
