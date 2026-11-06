package oolong.client.ui.common.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import oolong.client.ui.common.prefs.PrefsChevron
import oolong.client.ui.common.prefs.PrefsExternal
import oolong.client.ui.common.prefs.PrefsLinkValue
import oolong.client.ui.common.prefs.widgets.EditTextPref
import oolong.client.ui.common.prefs.widgets.ListPref
import oolong.client.ui.common.prefs.widgets.SwitchPref
import oolong.client.ui.common.prefs.widgets.TextPref
import oolong.client.ui.common.prefs.widgets.TimePickerPerf
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.navigation.Key
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.page.settings.LeadingIcon

/** What a row's action has at hand: the page's state, and what to act with. */
class ActionScope<S>(val state: S, val context: Context, val navigator: Navigator)

/**
 * Declares a group's rows. Every row takes, besides its own parameters:
 * - `available`: whether it exists here at all (see [Availability]); search obeys it too
 * - `shown`: whether it shows now, given the other settings; search ignores it
 * - `aliases`: a string of more words search finds it by
 * - `searchable`: off for rows search shouldn't offer (docs/settings-declarative.md §4)
 */
@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeUiApi::class)
@SettingsDsl
class GroupScope<S> internal constructor() {
    internal val items = mutableListOf<SettingsItem<S>>()

    private fun row(
        @StringRes title: Int?,
        kind: SettingsRow.Kind,
        available: Availability,
        shown: (@Composable S.() -> Boolean)?,
        @StringRes aliases: Int?,
        searchable: Boolean,
        content: @Composable S.(Modifier) -> Unit,
    ) {
        items += SettingsRow(title, kind, available, shown, aliases, searchable, content)
    }

    /** A setting that is on or off, stored as [key]. */
    fun switch(
        key: String,
        @StringRes title: Int,
        default: Boolean,
        summary: (@Composable S.(on: Boolean) -> String?)? = null,
        enabled: @Composable S.() -> Boolean = { true },
        onChange: (ActionScope<S>.(Boolean) -> Unit)? = null,
        available: Availability = Availability.Always,
        shown: (@Composable S.() -> Boolean)? = null,
        @StringRes aliases: Int? = null,
        searchable: Boolean = true,
    ) = row(title, SettingsRow.Kind.Value, available, shown, aliases, searchable) { modifier ->
        val state = this
        val scope = actionScope(state)
        SwitchPref(
            key = key,
            title = stringResource(id = title),
            modifier = modifier,
            summary = { on -> summary?.invoke(state, on) },
            defaultChecked = default,
            onCheckedChange = onChange?.let { change -> { on -> scope.change(on) } },
            enabled = enabled(state),
        )
    }

    /** One of [options], stored as [key]: values with their names. */
    fun choice(
        key: String,
        @StringRes title: Int,
        default: String,
        vararg options: Pair<String, Int>,
        enabled: @Composable S.() -> Boolean = { true },
        available: Availability = Availability.Always,
        shown: (@Composable S.() -> Boolean)? = null,
        @StringRes aliases: Int? = null,
        searchable: Boolean = true,
    ) = row(title, SettingsRow.Kind.Value, available, shown, aliases, searchable) { modifier ->
        ListPref(
            key = key,
            title = stringResource(id = title),
            modifier = modifier,
            entries = options.associate { (value, name) -> value to stringResource(id = name) },
            useSelectedAsSummary = true,
            defaultValue = default,
            enabled = enabled(this),
        )
    }

    /** A row that opens page [to], with the [value] it is set to beside it, if any. */
    fun link(
        @StringRes title: Int,
        to: Key,
        icon: ImageVector? = null,
        value: (@Composable S.() -> String?)? = null,
        available: Availability = Availability.Always,
        shown: (@Composable S.() -> Boolean)? = null,
        @StringRes aliases: Int? = null,
        searchable: Boolean = true,
    ) = row(title, SettingsRow.Kind.Link, available, shown, aliases, searchable) { modifier ->
        val navigator = LocalNavigator.current
        val shownValue = value?.invoke(this)
        TextPref(
            title = stringResource(id = title),
            modifier = modifier,
            leadingIcon = icon?.let { { LeadingIcon { Icon(imageVector = it, contentDescription = null) } } },
            onClick = { navigator.navigate(to) },
            trailingContent = if (shownValue != null) ({ PrefsLinkValue(text = shownValue) }) else ({ PrefsChevron() }),
        )
    }

    /**
     * A row that does something: [onClick]. [external] marks one that leaves the app, for the
     * system's settings or a web page; [destructive] colours one that deletes.
     */
    fun action(
        @StringRes title: Int,
        summary: (@Composable S.() -> String?)? = null,
        value: (@Composable S.() -> String?)? = null,
        external: Boolean = false,
        destructive: Boolean = false,
        enabled: @Composable S.() -> Boolean = { true },
        available: Availability = Availability.Always,
        shown: (@Composable S.() -> Boolean)? = null,
        @StringRes aliases: Int? = null,
        searchable: Boolean = true,
        onClick: ActionScope<S>.() -> Unit,
    ) = row(title, SettingsRow.Kind.Action, available, shown, aliases, searchable) { modifier ->
        val scope = actionScope(this)
        val shownValue = value?.invoke(this)
        TextPref(
            title = stringResource(id = title),
            modifier = modifier,
            summary = summary?.invoke(this),
            onClick = { scope.onClick() },
            enabled = enabled(this),
            textColor = if (destructive) MaterialTheme.colors.error else ExtendedTheme.colors.text,
            trailingContent = when {
                external -> ({ PrefsExternal(text = shownValue) })
                shownValue != null -> ({ Text(text = shownValue) })
                else -> null
            },
        )
    }

    /** What something is, shown and not set: the last time something ran, a size. */
    fun info(
        @StringRes title: Int,
        value: @Composable S.() -> String,
        available: Availability = Availability.Always,
        shown: (@Composable S.() -> Boolean)? = null,
        searchable: Boolean = false,
    ) = row(title, SettingsRow.Kind.Info, available, shown, aliases = null, searchable) { modifier ->
        val shownValue = value(this)
        TextPref(
            title = stringResource(id = title),
            modifier = modifier,
            darkenOnDisable = false,
            trailingContent = { Text(text = shownValue) },
        )
    }

    /** A time of day, stored as [key] as "HH:mm". */
    fun time(
        key: String,
        @StringRes title: Int,
        default: String,
        enabled: @Composable S.() -> Boolean = { true },
        available: Availability = Availability.Always,
        shown: (@Composable S.() -> Boolean)? = null,
        @StringRes aliases: Int? = null,
        searchable: Boolean = true,
    ) = row(title, SettingsRow.Kind.Value, available, shown, aliases, searchable) { modifier ->
        val text = stringResource(id = title)
        TimePickerPerf(
            key = key,
            title = text,
            modifier = modifier,
            defaultValue = default,
            dialogTitle = text,
            enabled = enabled(this),
        )
    }

    /** A line of text, stored as [key], with a [summary] under its title. */
    fun text(
        key: String,
        @StringRes title: Int,
        summary: (@Composable S.() -> String?)? = null,
        default: String = "",
        available: Availability = Availability.Always,
        shown: (@Composable S.() -> Boolean)? = null,
        @StringRes aliases: Int? = null,
        searchable: Boolean = true,
    ) = row(title, SettingsRow.Kind.Value, available, shown, aliases, searchable) { modifier ->
        val text = stringResource(id = title)
        EditTextPref(
            key = key,
            title = text,
            modifier = modifier,
            summary = summary?.invoke(this),
            dialogTitle = text,
            defaultValue = default,
        )
    }

    /**
     * A row drawn its own way, as a slider or a picker. Its [title] names it for search; without
     * one it isn't searched.
     */
    fun custom(
        @StringRes title: Int?,
        available: Availability = Availability.Always,
        shown: (@Composable S.() -> Boolean)? = null,
        @StringRes aliases: Int? = null,
        searchable: Boolean = title != null,
        content: @Composable S.(Modifier) -> Unit,
    ) = row(title, SettingsRow.Kind.Custom, available, shown, aliases, searchable, content)

    /** A note under the rows before it, outside their block. */
    fun footer(text: @Composable S.() -> String) {
        items += SettingsFooter(text)
    }
}

@Composable
private fun <S> actionScope(state: S) = ActionScope(state, LocalContext.current, LocalNavigator.current)
