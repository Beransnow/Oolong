package oolong.client.ui.common.prefs.widgets

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.ui.common.prefs.LocalPrefsDataStore
import oolong.client.ui.common.prefs.PrefsChoiceValue
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.widgets.compose.Dialog
import oolong.client.ui.widgets.compose.DialogNegativeButton
import oolong.client.ui.widgets.compose.choiceMenuItems
import oolong.client.ui.widgets.compose.clickMenu
import oolong.client.ui.widgets.compose.menuAnchor
import oolong.client.ui.widgets.compose.picker.ListSinglePicker
import oolong.client.ui.widgets.compose.rememberDialogState
import oolong.client.ui.widgets.compose.rememberMenuTriggerState

/**
 * Preference that shows a list of entries in a Dialog where a single entry can be selected at one time.
 *
 * @param key Key used to identify this Pref in the DataStore
 * @param title Main text which describes the Pref. Shown above the summary and in the Dialog.
 * @param modifier Modifier applied to the Text aspect of this Pref
 * @param summary Used to give some more information about what this Pref is for
 * @param defaultValue Default selected key if this Pref hasn't been saved already. Otherwise the value from the dataStore is used.
 * @param onValueChange Will be called with the selected key when an item is selected
 * @param useSelectedAsSummary If true, uses the current selected item as the summary
 * @param dialogBackgroundColor Background color of the Dialog
 * @param contentColor Preferred content color passed to dialog's children
 * @param textColor Text colour of the [title], [summary] and [entries]
 * @param selectionColor Colour of the radiobutton of the selected item
 * @param buttonColor Colour of the cancel button
 * @param enabled If false, this Pref cannot be clicked and the Dialog cannot be shown.
 * @param entries Map of keys to values for entries that should be shown in the Dialog.
 */
@ExperimentalMaterialApi
@ExperimentalComposeUiApi
@Composable
fun ListPref(
    key: String,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    defaultValue: String? = null,
    onValueChange: ((String) -> Unit)? = null,
    useSelectedAsSummary: Boolean = false,
    textColor: Color = ExtendedTheme.colors.text,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    entries: Map<String, String> = emptyMap(), // TODO: Change to List?
    icons: Map<String, @Composable () -> Unit> = emptyMap(),
) {
    val dialogState = rememberDialogState()
    val selectionKey = stringPreferencesKey(key)
    val scope = rememberCoroutineScope()

    val datastore = LocalPrefsDataStore.current
    val prefs by remember { datastore.data }.collectAsState(initial = null)

    var selected = defaultValue
    prefs?.get(selectionKey)?.also { selected = it } // starting value if it exists in datastore

    fun edit(current: Pair<String, String>) = run {
        selected = current.first
        onValueChange?.invoke(current.first)
        scope.launch {
            try {
                datastore.edit { preferences ->
                    preferences[selectionKey] = current.first
                }
            } catch (e: Exception) {
                Log.e("ListPref", "Could not write pref $key to database. ${e.printStackTrace()}")
            }
        }
    }

    val chosen = entries[selected] ?: "Not Set"
    if (icons.isEmpty()) {
        // Chosen in a menu growing out of the value, the way a forum's sort is
        val menu = rememberMenuTriggerState()
        val choice = @Composable {
            TextPref(
                title = title,
                summary = summary.takeUnless { useSelectedAsSummary },
                leadingIcon = leadingIcon,
                modifier = modifier,
                textColor = textColor,
                enabled = enabled,
                trailingContent = { PrefsChoiceValue(text = chosen, modifier = Modifier.menuAnchor(menu)) },
            )
        }
        if (enabled) {
            Box(
                modifier = Modifier.clickMenu(
                    items = { choiceMenuItems(entries.toList(), selected) { value -> if (value != null) edit(value to entries.getValue(value)) } },
                    state = menu,
                ),
            ) {
                choice()
            }
        } else {
            choice()
        }
        return
    }
    TextPref(
        title = title,
        summary = summary.takeUnless { useSelectedAsSummary },
        leadingIcon = leadingIcon,
        modifier = modifier,
        textColor = textColor,
        enabled = enabled,
        onClick = { dialogState.show() },
        trailingContent = { PrefsChoiceValue(text = chosen) },
    )
    val itemTitle = remember(entries) { entries.map { it.value }.toImmutableList() }
    val itemValues = remember(entries) { entries.map { it.key }.toImmutableList() }

    Dialog(
        dialogState = dialogState,
        title = { Text(text = title) },
        buttons = {
            DialogNegativeButton(text = stringResource(id = R.string.button_cancel))
        }
    ) {
        ListSinglePicker(
            itemTitles = itemTitle,
            itemValues = itemValues,
            selectedPosition = entries.keys.indexOf(selected),
            onItemSelected = { _, title, value, _ ->
                edit(current = value to title)
                dismiss()
            },
            modifier = Modifier.padding(bottom = 16.dp),
            itemIcons = icons.toImmutableMap()
        )
    }
//    if (showDialog) {
//        AlertDialog(
//            onDismissRequest = { showDialog = false },
//            text = {
//                Column() {
//                    Text(modifier = Modifier.padding(vertical = 16.dp), text = title)
//                    LazyColumn {
//                        items(entryList) { current ->
//
//                            val isSelected = selected == current.first
//                            val onSelected = {
//                                edit(current)
//                            }
//                            Row(
//                                modifier = Modifier
//                                    .fillMaxWidth()
//                                    .selectable(
//                                        selected = isSelected,
//                                        onClick = { if (!isSelected) onSelected() }
//                                    ),
//                                verticalAlignment = CenterVertically,
//                            ) {
//                                RadioButton(
//                                    selected = isSelected,
//                                    onClick = { if (!isSelected) onSelected() },
//                                    colors = RadioButtonDefaults.colors(selectedColor = selectionColor)
//                                )
//                                Text(
//                                    text = current.second,
//                                    style = MaterialTheme.typography.body2,
//                                    color = textColor
//                                )
//                            }
//                        }
//                    }
//                }
//            },
//            confirmButton = {
//                TextButton(
//                    onClick = { showDialog = false },
//                ) {
//                    Text("Cancel", style = MaterialTheme.typography.body1, color = buttonColor)
//                }
//
//            },
//            backgroundColor = dialogBackgroundColor,
//            contentColor = contentColor,
//            properties = DialogProperties(
//                usePlatformDefaultWidth = true
//            ),
//        )
//    }
}
