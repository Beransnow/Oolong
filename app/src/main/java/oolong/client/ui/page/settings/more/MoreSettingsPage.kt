package oolong.client.ui.page.settings.more

import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.components.AppStorage
import oolong.client.components.CrashReports
import oolong.client.migration.LegacyImport
import oolong.client.ui.common.prefs.PrefsExternal
import oolong.client.ui.common.prefs.widgets.TextPref
import oolong.client.ui.common.settings.Availability
import oolong.client.ui.common.settings.SettingsHeader
import oolong.client.ui.common.settings.SettingsPage
import oolong.client.ui.common.settings.SettingsScope
import oolong.client.ui.common.settings.SettingsScreen
import oolong.client.ui.common.settings.res
import oolong.client.ui.common.settings.stored
import oolong.client.ui.navigation.CrashLogsKey
import oolong.client.ui.navigation.DiagnosticsKey
import oolong.client.ui.navigation.MoreSettingsKey
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.navigation.StorageKey
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.page.ProvideNavigator
import oolong.client.utils.AppPreferencesUtils

/** What the other settings page knows only while it shows. */
class MoreState(
    /** How much the app keeps on the device, null until measured. */
    val storageTotal: Long?,
    val legacySource: LegacyImport.Source,
)

/** Everything else: links, storage and troubleshooting. */
object MoreSettings : SettingsPage<MoreState>(R.string.title_settings_more, MoreSettingsKey) {
    @Composable
    override fun rememberState(): MoreState {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val legacySource = remember { LegacyImport.source(context) }
        // Measured again on coming back, as the storage page may have cleared some
        var storageTotal by remember { mutableStateOf<Long?>(null) }
        LifecycleResumeEffect(Unit) {
            val job = coroutineScope.launch { storageTotal = AppStorage.total(context) }
            onPauseOrDispose { job.cancel() }
        }
        return MoreState(storageTotal, legacySource)
    }

    override fun SettingsScope<MoreState>.content() {
        group(R.string.settings_group_links) {
            choice(
                R.string.title_link_opening,
                options = LinkOpening.entries.map { it to it.title },
                value = { LinkOpening.of(useWebView = stored(UseWebView, true), useCustomTabs = stored(UseCustomTabs, true)) },
                onSelect = { opening ->
                    edit {
                        it[UseWebView] = opening == LinkOpening.WebView
                        it[UseCustomTabs] = opening == LinkOpening.CustomTabs
                    }
                },
            )
            switch("clipboard_links", R.string.settings_clipboard_links, default = true)
        }
        // Links to an unverified domain only open in the app once the user allows them
        group(SettingsHeader.Gap(8.dp), available = Availability.sdk(Build.VERSION_CODES.S)) {
            custom(R.string.title_tieba_links) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) TiebaLinksPref() }
            footer(res(R.string.tip_tieba_links))
        }
        group(R.string.settings_group_storage) {
            custom(R.string.title_import_legacy, shown = { legacySource != LegacyImport.Source.None }) { LegacyImportPref(legacySource) }
            link(R.string.title_storage, to = StorageKey, value = {
                val context = LocalContext.current
                storageTotal?.let { Formatter.formatShortFileSize(context, it) }.orEmpty()
            })
        }
        group(R.string.settings_group_troubleshooting) {
            switch(AppPreferencesUtils.UsageStatsKey, R.string.title_usage_stats, default = false)
            choice(
                R.string.title_crash_report_mode,
                options = listOf(
                    CrashReports.Mode.Ask to R.string.crash_report_mode_ask,
                    CrashReports.Mode.Always to R.string.crash_report_mode_always,
                    CrashReports.Mode.Never to R.string.crash_report_mode_never,
                ),
                value = { CrashReports.Mode.entries.firstOrNull { it.value == stored(CrashReportMode, CrashReports.Mode.Ask.value) } ?: CrashReports.Mode.Ask },
                onSelect = { mode -> edit { it[CrashReportMode] = mode.value } },
            )
            link(R.string.title_crash_logs, to = CrashLogsKey)
            link(R.string.title_diagnostics, to = DiagnosticsKey)
        }
    }
}

@Composable
fun MoreSettingsPage(navigator: Navigator = LocalNavigator.current) {
    ProvideNavigator(navigator) { SettingsScreen(MoreSettings) }
}

/**
 * How links outside Tieba open: in the app's own browser, in Custom Tabs, or in the system's
 * browser. Tieba's own links always open in the app.
 */
private enum class LinkOpening(@StringRes val title: Int) {
    WebView(R.string.link_opening_webview),
    CustomTabs(R.string.link_opening_custom_tabs),
    Browser(R.string.link_opening_browser),
    ;

    companion object {
        fun of(useWebView: Boolean, useCustomTabs: Boolean) = when {
            useWebView -> WebView
            useCustomTabs -> CustomTabs
            else -> Browser
        }
    }
}

private val CrashReportMode = stringPreferencesKey(CrashReports.ModeKey)

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
