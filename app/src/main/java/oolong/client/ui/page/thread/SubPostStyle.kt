package oolong.client.ui.page.thread

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.datastore.preferences.core.stringPreferencesKey
import oolong.client.rememberPreferenceAsState

/** How a floor shows the replies inside it. */
enum class SubPostStyle {
    /** Each reply on a branch off the floor's rule, in full, more of them opening in place. */
    Timeline,

    /** The first replies a few lines each, in a block under the floor, the rest on their own page. */
    Classic,
}

val SubPostStyleKey = stringPreferencesKey("subPostStyle")

@Composable
fun rememberSubPostStyle(): SubPostStyle {
    val name by rememberPreferenceAsState(SubPostStyleKey, SubPostStyle.Timeline.name)
    return SubPostStyle.entries.firstOrNull { it.name == name } ?: SubPostStyle.Timeline
}
