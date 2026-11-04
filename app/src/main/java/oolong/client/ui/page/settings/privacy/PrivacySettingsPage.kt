package oolong.client.ui.page.settings.privacy

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import oolong.client.R
import oolong.client.dataStore
import oolong.client.ui.common.prefs.PrefsScreen
import oolong.client.ui.common.prefs.widgets.TextPref
import oolong.client.ui.navigation.BlackListKey
import oolong.client.ui.navigation.DislikedForumsKey
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.widgets.compose.BackNavigationIcon
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.GlassTopBarTitle

/** What the signed-in account keeps on the server about whom and what it shuts out. */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PrivacySettingsPage(
    navigator: Navigator = LocalNavigator.current,
) {
    val context = LocalContext.current
    GlassScaffold(
        largeTitle = true,
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = R.string.title_privacy_settings)) },
                navigationIcon = { BackNavigationIcon(onBackPressed = { navigator.navigateUp() }) },
            )
        },
    ) { paddingValues ->
        PrefsScreen(
            dataStore = context.dataStore,
            dividerThickness = 0.dp,
            modifier = Modifier.fillMaxSize(),
            contentPadding = paddingValues,
        ) {
            prefsGroup(header = { Spacer(modifier = Modifier.height(8.dp)) }) {
                prefsItem {
                    TextPref(
                        title = stringResource(id = R.string.title_black_list),
                        onClick = { navigator.navigate(BlackListKey) },
                    )
                }
                prefsItem {
                    TextPref(
                        title = stringResource(id = R.string.settings_disliked_forums),
                        onClick = { navigator.navigate(DislikedForumsKey) },
                    )
                }
            }
        }
    }
}
