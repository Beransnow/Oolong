package oolong.client.ui.page.settings.block

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.persistentListOf
import oolong.client.R
import oolong.client.collectPreferenceAsState
import oolong.client.dataStore
import oolong.client.putBoolean
import oolong.client.putString
import oolong.client.repository.DefaultLowLevel
import oolong.client.repository.HideBlockedContentKey
import oolong.client.repository.LowLevelEnabledKey
import oolong.client.repository.LowLevelKey
import oolong.client.ui.common.prefs.PrefsLinkValue
import oolong.client.ui.common.prefs.PrefsScreen
import oolong.client.ui.common.prefs.widgets.SwitchPref
import oolong.client.ui.common.prefs.widgets.TextPref
import oolong.client.ui.navigation.BlockListKey
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.widgets.compose.BackNavigationIcon
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.GlassTopBarTitle
import oolong.client.ui.widgets.compose.PromptDialog
import oolong.client.ui.widgets.compose.SegmentedControl
import oolong.client.ui.widgets.compose.Stepper
import oolong.client.ui.widgets.compose.rememberDialogState

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
    val hideBlocked by context.dataStore.collectPreferenceAsState(booleanPreferencesKey(HideBlockedContentKey), false)
    val lowLevelBelow by context.dataStore.collectPreferenceAsState(stringPreferencesKey(LowLevelKey), DefaultLowLevel.toString())
    val foldLabel = stringResource(id = R.string.settings_blocked_fold)
    val hideLabel = stringResource(id = R.string.settings_blocked_hide)
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
                        val level = lowLevelBelow.toIntOrNull() ?: DefaultLowLevel
                        TextPref(
                            title = stringResource(id = R.string.settings_block_low_level_below),
                            enabled = true,
                            trailingContent = {
                                Stepper(
                                    value = level,
                                    range = LevelRange,
                                    text = stringResource(id = R.string.rule_level, level),
                                    down = stringResource(id = R.string.rule_level_down),
                                    up = stringResource(id = R.string.rule_level_up),
                                    onChange = { context.dataStore.putString(LowLevelKey, it.toString()) },
                                )
                            },
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
                    SegmentedControl(
                        options = persistentListOf(foldLabel, hideLabel),
                        selected = if (hideBlocked) 1 else 0,
                        onSelect = { context.dataStore.putBoolean(HideBlockedContentKey, it == 1) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
            prefsFooter { stringResource(id = R.string.settings_blocked_content_note) }
            prefsGroup(rulesGroup) {
                prefsItem {
                    TextPref(
                        title = stringResource(id = R.string.title_block_rules),
                        onClick = { navigator.navigate(BlockListKey) },
                        trailingContent = {
                            PrefsLinkValue(text = activeRules?.let { pluralStringResource(id = R.plurals.rules_count, count = it, it) }.orEmpty())
                        },
                    )
                }
                prefsItem {
                    TextPref(
                        title = stringResource(id = R.string.settings_block_export),
                        onClick = { exporter.launch(BackupFileName) },
                        trailingContent = null,
                    )
                }
                prefsItem {
                    TextPref(
                        title = stringResource(id = R.string.settings_block_import),
                        onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        trailingContent = null,
                    )
                }
            }
        }
    }
}

private const val BackupFileName = "tblite-block-rules.json"
