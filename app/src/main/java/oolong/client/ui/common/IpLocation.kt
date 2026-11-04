package oolong.client.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.booleanPreferencesKey
import oolong.client.rememberPreferenceAsState
import oolong.client.utils.appPreferences

val HideIpLocationKey = booleanPreferencesKey("hideIpLocation")

/** [ipLocation] as floors and profiles show it: null when it is empty or the viewer hides it. */
@Composable
fun shownIpLocation(ipLocation: String): String? {
    val context = LocalContext.current
    // Starts from what is stored, so a floor coming into the list doesn't show it for a frame
    val stored = remember(context) { context.appPreferences.hideIpLocation }
    val hidden by rememberPreferenceAsState(HideIpLocationKey, stored)
    return ipLocation.takeUnless { hidden || it.isEmpty() }
}
