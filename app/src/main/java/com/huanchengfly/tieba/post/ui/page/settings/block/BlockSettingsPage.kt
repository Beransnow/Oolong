package com.huanchengfly.tieba.post.ui.page.settings.block

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.collectPreferenceAsState
import com.huanchengfly.tieba.post.dataStore
import com.huanchengfly.tieba.post.repository.DefaultLowLevel
import com.huanchengfly.tieba.post.repository.HideBlockedContentKey
import com.huanchengfly.tieba.post.repository.LowLevelEnabledKey
import com.huanchengfly.tieba.post.repository.LowLevelKey
import com.huanchengfly.tieba.post.ui.common.prefs.PrefsLinkValue
import com.huanchengfly.tieba.post.ui.common.prefs.PrefsScreen
import com.huanchengfly.tieba.post.ui.common.prefs.widgets.DropDownPref
import com.huanchengfly.tieba.post.ui.common.prefs.widgets.SwitchPref
import com.huanchengfly.tieba.post.ui.common.prefs.widgets.TextPref
import com.huanchengfly.tieba.post.ui.navigation.BlockListKey
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBar
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBarTitle
import com.huanchengfly.tieba.post.ui.widgets.compose.PromptDialog
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberDialogState

/**
 * Blocking: what is often wanted as switches, how blocked content shows, and the rules. The
 * rarer rules are made in the list.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun BlockSettingsPage(
    navigator: Navigator = LocalNavigator.current,
    viewModel: BlockSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activeRules by viewModel.activeRules.collectAsStateWithLifecycle()
    val lowLevel by context.dataStore.collectPreferenceAsState(booleanPreferencesKey(LowLevelEnabledKey), false)
    val spoilerDialog = rememberDialogState()
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::export)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }
    PromptDialog(
        dialogState = spoilerDialog,
        title = { Text(text = stringResource(id = R.string.settings_block_spoiler)) },
        confirmText = stringResource(id = R.string.button_block_7_days),
        onConfirm = viewModel::blockSpoiler,
    ) {
        Text(text = stringResource(id = R.string.settings_block_spoiler_hint))
    }
    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = R.string.title_block_settings)) },
                navigationIcon = {
                    BackNavigationIcon(onBackPressed = { navigator.navigateUp() })
                }
            )
        },
    ) { paddingValues ->
        val commonGroup = stringResource(id = R.string.settings_block_common)
        val blockedGroup = stringResource(id = R.string.settings_blocked_content)
        val rulesGroup = stringResource(id = R.string.settings_block_rules_group)
        val levels = (2..10).associate { it.toString() to stringResource(id = R.string.rule_level, it) }
        PrefsScreen(
            dataStore = context.dataStore,
            dividerThickness = 0.dp,
            modifier = Modifier.fillMaxSize(),
            contentPadding = paddingValues,
        ) {
            prefsGroup(commonGroup) {
                prefsItem {
                    SwitchPref(key = "blockVideo", title = stringResource(id = R.string.settings_block_video))
                }
                prefsItem {
                    SwitchPref(key = LowLevelEnabledKey, title = stringResource(id = R.string.settings_block_low_level))
                }
                if (lowLevel) {
                    prefsItem {
                        DropDownPref(
                            key = LowLevelKey,
                            title = stringResource(id = R.string.settings_block_low_level_below),
                            defaultValue = DefaultLowLevel.toString(),
                            useSelectedAsSummary = true,
                            entries = levels,
                        )
                    }
                }
                prefsItem {
                    TextPref(
                        title = stringResource(id = R.string.settings_block_spoiler),
                        summary = stringResource(id = R.string.settings_block_spoiler_summary),
                        onClick = { spoilerDialog.show() },
                    )
                }
            }
            prefsGroup(blockedGroup) {
                prefsItem {
                    SwitchPref(key = HideBlockedContentKey, title = stringResource(id = R.string.settings_hide_blocked_content))
                }
            }
            prefsFooter { stringResource(id = R.string.settings_blocked_content_note) }
            prefsGroup(rulesGroup) {
                prefsItem {
                    TextPref(
                        title = stringResource(id = R.string.title_block_rules),
                        onClick = { navigator.navigate(BlockListKey) },
                        trailingContent = { PrefsLinkValue(text = activeRules?.toString().orEmpty()) },
                    )
                }
                prefsItem {
                    TextPref(
                        title = stringResource(id = R.string.settings_block_export),
                        onClick = { exporter.launch(BackupFileName) },
                    )
                }
                prefsItem {
                    TextPref(
                        title = stringResource(id = R.string.settings_block_import),
                        onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    )
                }
            }
        }
    }
}

private const val BackupFileName = "tblite-block-rules.json"
