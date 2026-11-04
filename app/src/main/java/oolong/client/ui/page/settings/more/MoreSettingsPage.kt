package oolong.client.ui.page.settings.more

import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.components.AppStorage
import oolong.client.components.CrashReports
import oolong.client.dataStore
import oolong.client.migration.LegacyImport
import oolong.client.ui.common.prefs.PrefsChoiceValue
import oolong.client.ui.common.prefs.PrefsExternal
import oolong.client.ui.common.prefs.PrefsLinkValue
import oolong.client.ui.common.prefs.PrefsScreen
import oolong.client.ui.common.prefs.widgets.SwitchPref
import oolong.client.ui.common.prefs.widgets.TextPref
import oolong.client.ui.navigation.CrashLogsKey
import oolong.client.ui.navigation.DiagnosticsKey
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.navigation.StorageKey
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.widgets.compose.BackNavigationIcon
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.GlassTopBarTitle
import oolong.client.ui.widgets.compose.choiceMenuItems
import oolong.client.ui.widgets.compose.clickMenu
import oolong.client.ui.widgets.compose.menuAnchor
import oolong.client.ui.widgets.compose.rememberMenuTriggerState
import oolong.client.utils.AppPreferencesUtils

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MoreSettingsPage(
    navigator: Navigator = LocalNavigator.current,
) {
    val coroutineScope = rememberCoroutineScope()
    GlassScaffold(
        largeTitle = true,
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = R.string.title_settings_more)) },
                navigationIcon = {
                    BackNavigationIcon(onBackPressed = { navigator.navigateUp() })
                }
            )
        },
    ) { paddingValues ->
        val context = LocalContext.current
        val linksGroup = stringResource(id = R.string.settings_group_links)
        val storageGroup = stringResource(id = R.string.settings_group_storage)
        val troubleshootingGroup = stringResource(id = R.string.settings_group_troubleshooting)
        val legacySource = remember { LegacyImport.source(context) }
        // Measured again on coming back, as the storage page may have cleared some
        var storageTotal by remember { mutableStateOf<Long?>(null) }
        LifecycleResumeEffect(Unit) {
            val job = coroutineScope.launch { storageTotal = AppStorage.total(context) }
            onPauseOrDispose { job.cancel() }
        }
        PrefsScreen(
            dataStore = LocalContext.current.dataStore,
            dividerThickness = 0.dp,
            modifier = Modifier.fillMaxSize(),
            contentPadding = paddingValues,
        ) {
            prefsGroup(linksGroup) {
                prefsItem { LinkOpeningPref() }
                prefsItem {
                    SwitchPref(
                        key = "clipboard_links",
                        title = stringResource(id = R.string.settings_clipboard_links),
                        defaultChecked = true,
                    )
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Links to an unverified domain only open in the app once the user allows them
                prefsGroup(header = { Spacer(modifier = Modifier.height(8.dp)) }) {
                    prefsItem { TiebaLinksPref() }
                }
                prefsFooter { stringResource(id = R.string.tip_tieba_links) }
            }
            prefsGroup(storageGroup) {
                if (legacySource != LegacyImport.Source.None) {
                    prefsItem { LegacyImportPref(legacySource) }
                }
                prefsItem {
                    TextPref(
                        title = stringResource(id = R.string.title_storage),
                        onClick = { navigator.navigate(StorageKey) },
                        trailingContent = { PrefsLinkValue(storageTotal?.let { Formatter.formatShortFileSize(context, it) }.orEmpty()) },
                    )
                }
            }
            prefsGroup(troubleshootingGroup) {
                prefsItem {
                    SwitchPref(
                        key = AppPreferencesUtils.UsageStatsKey,
                        title = stringResource(id = R.string.title_usage_stats),
                        defaultChecked = false,
                    )
                }
                prefsItem { CrashReportModePref() }
                prefsItem {
                    TextPref(
                        title = stringResource(id = R.string.title_crash_logs),
                        onClick = { navigator.navigate(CrashLogsKey) },
                    )
                }
                prefsItem {
                    TextPref(
                        title = stringResource(id = R.string.title_diagnostics),
                        onClick = { navigator.navigate(DiagnosticsKey) },
                    )
                }
            }
        }
    }
}

/** Whether a crash log is sent: asked each time, sent on its own, or never (see CrashReports). */
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun CrashReportModePref() {
    val context = LocalContext.current
    val dataStore = context.dataStore
    val coroutineScope = rememberCoroutineScope()
    val key = remember { stringPreferencesKey(CrashReports.ModeKey) }
    val value by remember { dataStore.data.map { it[key] ?: CrashReports.Mode.Ask.value } }.collectAsState(initial = CrashReports.Mode.Ask.value)
    val options = listOf(
        CrashReports.Mode.Ask to stringResource(id = R.string.crash_report_mode_ask),
        CrashReports.Mode.Always to stringResource(id = R.string.crash_report_mode_always),
        CrashReports.Mode.Never to stringResource(id = R.string.crash_report_mode_never),
    )
    val current = options.indexOfFirst { it.first.value == value }.coerceAtLeast(0)
    val menu = rememberMenuTriggerState()
    Box(
        modifier = Modifier.clickMenu(
            state = menu,
            items = {
                choiceMenuItems(options.withIndex().map { (index, option) -> index to option.second }, current) { index ->
                    coroutineScope.launch { dataStore.edit { it[key] = options[index].first.value } }
                }
            },
        ),
    ) {
        TextPref(
            title = stringResource(id = R.string.title_crash_report_mode),
            enabled = true,
            trailingContent = { PrefsChoiceValue(text = options[current].second, modifier = Modifier.menuAnchor(menu)) },
        )
    }
}

/**
 * How links outside Tieba open: in the app's own browser, in Custom Tabs, or in the system's
 * browser. Tieba's own links always open in the app.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun LinkOpeningPref() {
    val context = LocalContext.current
    val dataStore = context.dataStore
    val coroutineScope = rememberCoroutineScope()
    val useWebView by remember { dataStore.data.map { it[UseWebView] ?: true } }.collectAsState(initial = true)
    val useCustomTabs by remember { dataStore.data.map { it[UseCustomTabs] ?: true } }.collectAsState(initial = true)
    val options = listOf(
        stringResource(id = R.string.link_opening_webview),
        stringResource(id = R.string.link_opening_custom_tabs),
        stringResource(id = R.string.link_opening_browser),
    )
    val current = when {
        useWebView -> 0
        useCustomTabs -> 1
        else -> 2
    }
    val menu = rememberMenuTriggerState()
    Box(
        modifier = Modifier.clickMenu(
            state = menu,
            items = {
                choiceMenuItems(options.withIndex().map { (index, name) -> index to name }, current) { index ->
                    coroutineScope.launch {
                        dataStore.edit {
                            it[UseWebView] = index == 0
                            it[UseCustomTabs] = index == 1
                        }
                    }
                }
            },
        ),
    ) {
        TextPref(
            title = stringResource(id = R.string.title_link_opening),
            enabled = true,
            trailingContent = { PrefsChoiceValue(text = options[current], modifier = Modifier.menuAnchor(menu)) },
        )
    }
}

private val UseWebView = booleanPreferencesKey("use_webview")
private val UseCustomTabs = booleanPreferencesKey("use_custom_tabs")

/**
 * Whether Tieba's links open in the app or in a browser. Links to an unverified domain only open
 * in the app once the user allows them in the system's settings, which the row opens; the answer
 * is read again on returning.
 */
@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun TiebaLinksPref() {
    val context = LocalContext.current
    var opensInApp by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        val state = context.getSystemService(DomainVerificationManager::class.java)
            ?.getDomainVerificationUserState(context.packageName)
        opensInApp = state != null && state.isLinkHandlingAllowed &&
            state.hostToStateMap[TiebaHost] in setOf(
                DomainVerificationUserState.DOMAIN_STATE_SELECTED,
                DomainVerificationUserState.DOMAIN_STATE_VERIFIED,
            )
        onPauseOrDispose {}
    }
    TextPref(
        title = stringResource(id = R.string.title_tieba_links),
        onClick = {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                        "package:${context.packageName}".toUri()
                    )
                )
            }
        },
        trailingContent = {
            PrefsExternal(
                text = stringResource(
                    id = if (opensInApp) R.string.value_tieba_links_app else R.string.value_tieba_links_browser
                )
            )
        },
    )
}

private const val TiebaHost = "tieba.baidu.com"
