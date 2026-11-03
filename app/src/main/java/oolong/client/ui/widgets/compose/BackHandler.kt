package oolong.client.ui.widgets.compose

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import oolong.client.ui.navigation.LocalIsTopEntry

/**
 * Handles back while [enabled]. Pages stay composed under sheets and dialogs, so by default the
 * handler only takes back while its page is on top; pass [onlyOnTop] false outside navigation.
 */
@Composable
fun MyBackHandler(
    enabled: Boolean,
    onlyOnTop: Boolean = true,
    onBack: () -> Unit,
) {
    val isTop = LocalIsTopEntry.current
    BackHandler(enabled = enabled && (!onlyOnTop || isTop), onBack = onBack)
}
