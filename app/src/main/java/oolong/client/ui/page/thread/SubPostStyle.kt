package oolong.client.ui.page.thread

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import oolong.client.rememberPreferenceAsState
import oolong.client.utils.appPreferences

/** How a floor shows the replies inside it. */
enum class SubPostStyle {
    /** Each reply on a branch off the floor's rule, in full, more of them opening in place. */
    Timeline,

    /** The first replies a few lines each, in a block under the floor, the rest on their own page. */
    Classic,
}

val SubPostStyleKey = stringPreferencesKey("subPostStyle")

/** Whether a floor's replies start folded into a capsule, whichever [SubPostStyle] they have. */
val SubPostsCollapsedKey = booleanPreferencesKey("subPostsCollapsed")

// Each floor reads these as it comes into the list, so they start from what is stored, not from
// the default: read later, a floor would first show the other way for a frame

@Composable
fun rememberSubPostStyle(): SubPostStyle {
    val context = LocalContext.current
    val stored = remember(context) { context.appPreferences.subPostStyle ?: SubPostStyle.Timeline.name }
    val name by rememberPreferenceAsState(SubPostStyleKey, stored)
    return SubPostStyle.entries.firstOrNull { it.name == name } ?: SubPostStyle.Timeline
}

@Composable
fun rememberSubPostsCollapsed(): Boolean {
    val context = LocalContext.current
    val stored = remember(context) { context.appPreferences.subPostsCollapsed }
    val collapsed by rememberPreferenceAsState(SubPostsCollapsedKey, stored)
    return collapsed
}
