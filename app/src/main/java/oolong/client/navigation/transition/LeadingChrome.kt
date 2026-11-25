package oolong.client.navigation.transition

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import oolong.client.ui.designsystem.LocalNavigationRailShown

/**
 * Chrome standing at the start of the window, under the pages, such as the navigation rail. Its
 * width doesn't change with the pages: they leave [width] free beside it, and a page it [covers],
 * one over the whole window, goes over it instead, as a cover goes over everything.
 */
@Immutable
class LeadingChrome(val width: Dp, private val coversEntry: (NavEntry<*>) -> Boolean) {
    /** Whether [entry] is a page going over the chrome. */
    fun covers(entry: NavEntry<*>): Boolean = coversEntry(entry)

    /** Whether [scene] is such a page, alone. */
    fun covers(scene: Scene<*>): Boolean = scene is FramedSinglePaneScene<*> && coversEntry(scene.entry)
}

val LocalLeadingChrome = compositionLocalOf<LeadingChrome?> { null }

/**
 * Lays out a scene's [content] beside the [LeadingChrome], or over it for an [entry] it covers.
 * The room is left outside the page's frame, so a page peeked or morphing is the same as without
 * the chrome; the chrome takes the window's insets at that side.
 */
@Composable
fun BesideLeadingChrome(entry: NavEntry<*>?, content: @Composable () -> Unit) {
    val chrome = LocalLeadingChrome.current
    // One layout whichever it is, so a page keeps its state as the chrome is first measured
    val beside = chrome != null && chrome.width > 0.dp && (entry == null || !chrome.covers(entry))
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = if (beside) chrome!!.width else 0.dp)
            .then(if (beside) Modifier.consumeWindowInsets(LeadingInsets) else Modifier),
    ) {
        CompositionLocalProvider(LocalNavigationRailShown provides beside, content = content)
    }
}

private val LeadingInsets: WindowInsets
    @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Start)
