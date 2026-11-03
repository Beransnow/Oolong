package oolong.client.ui.widgets.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import oolong.client.dataStore

/** How the app's bars are laid out, chosen in Settings › Appearance. */
enum class ChromeStyle {
    /** Glass capsules floating over the page, and a large title that docks into the top bar. */
    Floating,

    /**
     * The bars of before: bands across the top and the bottom of the window, their buttons bare
     * on them, tabs underlined, and the title in the bar.
     */
    Classic,
}

val ChromeStyleKey = stringPreferencesKey("chromeStyle")

val LocalChromeStyle = compositionLocalOf { ChromeStyle.Floating }

/** Provides the chosen [ChromeStyle] to [content]. */
@Composable
fun ProvideChromeStyle(content: @Composable () -> Unit) {
    val dataStore = LocalContext.current.dataStore
    // Read before the first frame: a page drawn in one style and redrawn in the other keeps
    // things it laid out for the first, such as a large title
    val initial = remember { runBlocking { dataStore.data.first()[ChromeStyleKey] } ?: ChromeStyle.Floating.name }
    val chosen by remember { dataStore.data.map { it[ChromeStyleKey] ?: ChromeStyle.Floating.name } }.collectAsState(initial)
    val style = ChromeStyle.entries.firstOrNull { it.name == chosen } ?: ChromeStyle.Floating
    CompositionLocalProvider(LocalChromeStyle provides style, content = content)
}

/**
 * Whether what is drawn sits on a classic bar, whose band is its ground: floating glass then
 * draws nothing of its own, so a bar's buttons and title are bare on it.
 */
internal val LocalOnClassicBar = staticCompositionLocalOf { false }
