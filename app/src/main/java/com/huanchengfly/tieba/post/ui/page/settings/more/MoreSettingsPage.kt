package com.huanchengfly.tieba.post.ui.page.settings.more

import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.dataStore
import com.huanchengfly.tieba.post.ui.common.prefs.PrefsChoiceValue
import com.huanchengfly.tieba.post.ui.common.prefs.PrefsExternal
import com.huanchengfly.tieba.post.ui.common.prefs.PrefsScreen
import com.huanchengfly.tieba.post.ui.common.prefs.widgets.TextPref
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBar
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBarTitle
import com.huanchengfly.tieba.post.ui.widgets.compose.LocalSnackbarHostState
import com.huanchengfly.tieba.post.ui.widgets.compose.choiceMenuItems
import com.huanchengfly.tieba.post.ui.widgets.compose.clickMenu
import com.huanchengfly.tieba.post.utils.ImageCacheUtil
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.concurrent.thread

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MoreSettingsPage(
    navigator: Navigator = LocalNavigator.current,
) {
    val coroutineScope = rememberCoroutineScope()
    GlassScaffold(
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
        val snackbarHostState = LocalSnackbarHostState.current
        val context = LocalContext.current
        val linksGroup = stringResource(id = R.string.settings_group_links)
        val storageGroup = stringResource(id = R.string.settings_group_storage)
        var cacheSize by remember { mutableStateOf("0.0B") }
        LaunchedEffect(Unit) {
            thread {
                cacheSize = ImageCacheUtil.getCacheSize(context)
            }
        }
        PrefsScreen(
            dataStore = LocalContext.current.dataStore,
            dividerThickness = 0.dp,
            modifier = Modifier.fillMaxSize(),
            contentPadding = paddingValues,
        ) {
            prefsGroup(linksGroup) {
                prefsItem { LinkOpeningPref() }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Links to an unverified domain only open in the app once the user allows them
                prefsGroup(header = { Spacer(modifier = Modifier.height(8.dp)) }) {
                    prefsItem { TiebaLinksPref() }
                }
                prefsFooter { stringResource(id = R.string.tip_tieba_links) }
            }
            prefsGroup(storageGroup) {
                prefsItem {
                    TextPref(
                        enabled = true,
                        title = stringResource(id = R.string.title_clear_picture_cache),
                        onClick = {
                            coroutineScope.launch {
                                ImageCacheUtil.clearImageAllCache(context)
                                cacheSize = "0.0B"
                                snackbarHostState.showSnackbar(context.getString(R.string.toast_clear_picture_cache_success))
                            }
                        },
                        trailingContent = { Text(text = cacheSize) },
                    )
                }
            }
        }
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
    Box(
        modifier = Modifier.clickMenu(
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
            trailingContent = { PrefsChoiceValue(text = options[current]) },
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
                        Uri.parse("package:${context.packageName}")
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
