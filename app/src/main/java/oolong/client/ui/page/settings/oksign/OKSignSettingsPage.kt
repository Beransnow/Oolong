package oolong.client.ui.page.settings.oksign

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.dataStore
import oolong.client.services.AutoSign
import oolong.client.ui.common.prefs.PrefsExternal
import oolong.client.ui.common.prefs.PrefsScreen
import oolong.client.ui.common.prefs.depend
import oolong.client.ui.common.prefs.widgets.SwitchPref
import oolong.client.ui.common.prefs.widgets.TextPref
import oolong.client.ui.common.prefs.widgets.TimePickerPerf
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.widgets.compose.BackNavigationIcon
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.GlassTopBarTitle
import oolong.client.ui.widgets.compose.LocalSnackbarHostState
import oolong.client.utils.BackgroundSettings
import oolong.client.utils.PermissionUtils
import oolong.client.utils.isIgnoringBatteryOptimizations
import oolong.client.utils.launchUrl
import oolong.client.utils.requestIgnoreBatteryOptimizations
import oolong.client.utils.requestPermission
import java.util.Date

@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeUiApi::class)
@Composable
fun OKSignSettingsPage(
    navigator: Navigator = LocalNavigator.current,
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
                .collect { AutoSign.schedule(context) }
        }
        // What the system allows is read again on coming back from its settings
        var resumes by remember { mutableIntStateOf(0) }
        LifecycleResumeEffect(Unit) {
            resumes++
            onPauseOrDispose {}
        }
        val state = remember(resumes) { RunConditions.read(context) }
        // The system's own prompt, after a word on what the notifications are for; its settings
        // only once it no longer asks
        val requestNotifications: () -> Unit = {
            context.requestPermission {
                permissions = listOf(PermissionUtils.POST_NOTIFICATIONS)
                description = context.getString(R.string.desc_permission_post_notifications)
                onGranted = { resumes++ }
                onDeniedForever = { BackgroundSettings.openNotifications(context) }
            }
        }
        // Turning signing in automatically on is when its results start to need notifications
        LaunchedEffect(dataStore) {
            dataStore.data
                .map { it[booleanPreferencesKey("auto_sign")] == true }
                .distinctUntilChanged()
                .drop(1)
                .collect { on ->
                    if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !RunConditions.read(context).notifications) {
                        requestNotifications()
                    }
                }
        }
        val snackbarHostState = LocalSnackbarHostState.current
        val oneKeyGroup = stringResource(id = R.string.settings_group_sign_mode)
        val autoGroup = stringResource(id = R.string.title_auto_sign)
        val conditionsGroup = stringResource(id = R.string.settings_group_auto_sign_conditions)
        val allowed = stringResource(id = R.string.value_allowed)
        val notAllowed = stringResource(id = R.string.value_not_allowed)
        val lastRun = lastRunText(state.lastRun)
        val vendorTip = vendorTip()
        val testScheduled = stringResource(id = R.string.toast_auto_sign_test_scheduled)
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
                        title = stringResource(id = R.string.title_auto_sign_last),
                        darkenOnDisable = false,
                        trailingContent = { Text(text = lastRun) },
                    )
                }
            }
            prefsGroup(conditionsGroup) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    prefsItem {
                        TextPref(
                            title = stringResource(id = R.string.title_exact_alarms),
                            trailingContent = { PrefsExternal(text = if (state.exactAlarms) allowed else notAllowed) },
                            onClick = { BackgroundSettings.openExactAlarms(context) },
                        )
                    }
                }
                prefsItem {
                    TextPref(
                        title = stringResource(id = R.string.title_ignore_battery_optimization),
                        // The system's state, and the system's dialog to change it; on Xiaomi's
                        // systems this opens its own battery saver page
                        trailingContent = {
                            PrefsExternal(
                                text = stringResource(
                                    id = if (state.batteryIgnored) R.string.summary_battery_optimization_ignored else R.string.value_battery_optimization_on,
                                ),
                            )
                        },
                        onClick = {
                            if (state.batteryIgnored) BackgroundSettings.openAppDetails(context)
                            else context.requestIgnoreBatteryOptimizations()
                        },
                    )
                }
                if (BackgroundSettings.vendor != BackgroundSettings.Vendor.Other) {
                    prefsItem {
                        TextPref(
                            title = stringResource(id = R.string.title_auto_start),
                            trailingContent = {
                                PrefsExternal(
                                    text = when (state.autoStart) {
                                        BackgroundSettings.State.On -> allowed
                                        BackgroundSettings.State.Off -> notAllowed
                                        BackgroundSettings.State.Unknown -> null
                                    },
                                )
                            },
                            onClick = { BackgroundSettings.openAutoStart(context) },
                        )
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    prefsItem {
                        TextPref(
                            title = stringResource(id = R.string.title_system_notifications),
                            summary = stringResource(id = R.string.summary_auto_sign_notifications),
                            trailingContent = { PrefsExternal(text = if (state.notifications) allowed else notAllowed) },
                            onClick = { if (state.notifications) BackgroundSettings.openNotifications(context) else requestNotifications() },
                        )
                    }
                }
                prefsItem {
                    TextPref(
                        title = stringResource(id = R.string.title_auto_sign_test),
                        summary = stringResource(id = R.string.summary_auto_sign_test),
                        trailingContent = null,
                        onClick = {
                            val time = AutoSign.test(context, TestDelayMillis)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(testScheduled.format(DateFormat.getTimeFormat(context).format(Date(time))))
                            }
                        },
                    )
                }
            }
            prefsFooter { vendorTip }
            prefsGroup(header = {}) {
                prefsItem {
                    TextPref(
                        title = stringResource(id = R.string.title_background_guide),
                        trailingContent = { PrefsExternal() },
                        onClick = {
                            launchUrl(context, navigator, "https://dontkillmyapp.com/${BackgroundSettings.vendor.dontKillMyApp}")
                        },
                    )
                }
            }
        }
    }
}

/** What the system lets the daily sign-in do, as it stands now. */
private class RunConditions(
    val exactAlarms: Boolean,
    val batteryIgnored: Boolean,
    val autoStart: BackgroundSettings.State,
    val notifications: Boolean,
    val lastRun: AutoSign.LastRun?,
) {
    companion object {
        fun read(context: android.content.Context) = RunConditions(
            exactAlarms = AutoSign.canScheduleExactAlarms(context),
            batteryIgnored = context.isIgnoringBatteryOptimizations(),
            autoStart = BackgroundSettings.autoStart(context),
            notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            lastRun = AutoSign.lastRun(context),
        )
    }
}

@Composable
private fun lastRunText(run: AutoSign.LastRun?): String {
    if (run == null) return stringResource(id = R.string.value_auto_sign_never)
    val context = LocalContext.current
    val time = DateUtils.formatDateTime(
        context,
        run.time,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
    )
    return when {
        !run.success -> stringResource(id = R.string.value_auto_sign_failed, time)
        else -> stringResource(id = R.string.value_auto_sign_done, time, run.signed, run.total)
    }
}

/** What else this phone's maker wants allowed, where the rows above cannot show it. */
@Composable
private fun vendorTip(): String = stringResource(
    id = when (BackgroundSettings.vendor) {
        BackgroundSettings.Vendor.Xiaomi -> R.string.tip_auto_sign_xiaomi
        BackgroundSettings.Vendor.Huawei, BackgroundSettings.Vendor.Honor -> R.string.tip_auto_sign_huawei
        BackgroundSettings.Vendor.Oppo -> R.string.tip_auto_sign_oppo
        BackgroundSettings.Vendor.Vivo -> R.string.tip_auto_sign_vivo
        BackgroundSettings.Vendor.Other -> R.string.tip_auto_sign_other
    },
)

private const val TestDelayMillis = 2 * 60 * 1000L
