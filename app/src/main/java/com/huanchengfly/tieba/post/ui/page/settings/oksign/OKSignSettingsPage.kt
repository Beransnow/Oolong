package com.huanchengfly.tieba.post.ui.page.settings.oksign

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.dataStore
import com.huanchengfly.tieba.post.ui.common.prefs.PrefsScreen
import com.huanchengfly.tieba.post.ui.common.prefs.depend
import com.huanchengfly.tieba.post.ui.common.prefs.widgets.SwitchPref
import com.huanchengfly.tieba.post.ui.common.prefs.widgets.TextPref
import com.huanchengfly.tieba.post.ui.common.prefs.widgets.TimePickerPerf
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBar
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBarTitle
import com.huanchengfly.tieba.post.ui.widgets.compose.LocalSnackbarHostState
import com.huanchengfly.tieba.post.utils.isIgnoringBatteryOptimizations
import com.huanchengfly.tieba.post.utils.powerManager
import com.huanchengfly.tieba.post.utils.requestIgnoreBatteryOptimizations
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.huanchengfly.tieba.post.utils.TiebaUtil
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import com.huanchengfly.tieba.post.ui.common.prefs.PrefsExternal

@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeUiApi::class)
@Destination
@Composable
fun OKSignSettingsPage(
    navigator: DestinationsNavigator,
) {
    val coroutineScope = rememberCoroutineScope()
    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = R.string.title_oksign)) },
                navigationIcon = {
                    BackNavigationIcon(onBackPressed = { navigator.navigateUp() })
                }
            )
        },
    ) { paddingValues ->
        val context = LocalContext.current
        val dataStore = context.dataStore
        // Reschedule the daily sign-in as soon as it is turned on or off or its time changes
        LaunchedEffect(dataStore) {
            dataStore.data
                .map { it[booleanPreferencesKey("auto_sign")] to it[stringPreferencesKey("auto_sign_time")] }
                .distinctUntilChanged()
                .drop(1)
                .collect { TiebaUtil.initAutoSign(context) }
        }
        val snackbarHostState = LocalSnackbarHostState.current
        val oneKeyGroup = stringResource(id = R.string.settings_group_sign_mode)
        val autoGroup = stringResource(id = R.string.title_auto_sign)
        PrefsScreen(
            dataStore = dataStore,
            dividerThickness = 0.dp,
            modifier = Modifier.fillMaxSize(),
            contentPadding = paddingValues,
        ) {
            prefsGroup(oneKeyGroup) {
                prefsItem {
                    SwitchPref(
                        key = "oksign_slow_mode",
                        title = stringResource(id = R.string.title_oksign_slow_mode),
                        defaultChecked = true,
                        summary = stringResource(id = R.string.summary_oksign_slow_mode),
                    )
                }
                prefsItem {
                    SwitchPref(
                        key = "oksign_use_official_oksign",
                        title = stringResource(id = R.string.title_oksign_use_official_oksign),
                        defaultChecked = true,
                        summary = stringResource(id = R.string.summary_oksign_use_official_oksign),
                    )
                }
            }
            prefsGroup(autoGroup) {
                prefsItem {
                    SwitchPref(
                        key = "auto_sign",
                        title = stringResource(id = R.string.title_auto_sign),
                        defaultChecked = false,
                    )
                }
                prefsItem {
                    TimePickerPerf(
                        key = "auto_sign_time",
                        title = stringResource(id = R.string.title_auto_sign_time),
                        defaultValue = "09:00",
                        dialogTitle = stringResource(id = R.string.title_auto_sign_time),
                        enabled = depend(key = "auto_sign")
                    )
                }
                prefsItem {
                    TextPref(
                        title = stringResource(id = R.string.title_ignore_battery_optimization),
                        // The system's state, and the system's dialog to change it
                        trailingContent = if (context.isIgnoringBatteryOptimizations()) {
                            { Text(text = stringResource(id = R.string.summary_battery_optimization_ignored)) }
                        } else {
                            { PrefsExternal(text = stringResource(id = R.string.value_battery_optimization_on)) }
                        },
                        onClick = {
                            if (!context.powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                                context.requestIgnoreBatteryOptimizations()
                            } else {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.toast_ignore_battery_optimization_already))
                                }
                            }
                        }
                    )
                }
            }
            prefsFooter { stringResource(id = R.string.tip_auto_sign) }
        }
    }
}