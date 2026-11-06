package oolong.client.ui.common.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import oolong.client.dataStore
import oolong.client.ui.common.LocalAccount
import oolong.client.ui.common.prefs.GroupHeader
import oolong.client.ui.common.prefs.PrefsScope
import oolong.client.ui.common.prefs.PrefsScreen
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.widgets.compose.BackNavigationIcon
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.GlassTopBarTitle

/** [page] as a page of its own: its title large over its groups, and a back button. */
@Composable
fun <S> SettingsScreen(page: SettingsPage<S>) {
    val navigator = LocalNavigator.current
    GlassScaffold(
        largeTitle = true,
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = page.title)) },
                navigationIcon = { BackNavigationIcon(onBackPressed = { navigator.navigateUp() }) },
            )
        },
    ) { padding ->
        SettingsContent(page, contentPadding = padding)
    }
}

/** [page]'s groups, for a page that draws its own frame around them. */
@Composable
fun <S> SettingsContent(page: SettingsPage<S>, modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(0.dp)) {
    val state = page.rememberState()
    page.Effects(state)
    val environment = rememberSettingsEnvironment()
    // Which rows show now, read here: the lists of rows are built outside composition
    val hidden = buildSet {
        page.groups.forEach { group ->
            group.items.forEach { item -> if (item is SettingsRow && item.shown?.invoke(state) == false) add(item) }
        }
    }
    PrefsScreen(
        dataStore = LocalContext.current.dataStore,
        dividerThickness = 0.dp,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        page.groups.forEach { group ->
            if (group.available.isMet(environment)) group(group, state, environment, hidden)
        }
    }
}

@Composable
internal fun rememberSettingsEnvironment() = SettingsEnvironment(LocalContext.current, signedIn = LocalAccount.current != null)

private fun <S> PrefsScope.group(group: SettingsGroup<S>, state: S, environment: SettingsEnvironment, hidden: Set<SettingsRow<S>>) {
    val rows: PrefsScope.() -> Unit = {
        group.items.filterIsInstance<SettingsRow<S>>().forEach { row ->
            if (row.available.isMet(environment) && row !in hidden) prefsItem { row.content(state, Modifier) }
        }
    }
    when (val header = group.header) {
        null -> rows()
        is SettingsHeader.Title -> prefsGroup(header = { GroupHeader(stringResource(id = header.text)) }, items = rows)
        is SettingsHeader.Gap -> prefsGroup(header = { Spacer(modifier = Modifier.height(header.height)) }, items = rows)
        is SettingsHeader.Custom -> prefsGroup(header = { header.content(state) }, items = rows)
    }
    // Notes go under the group's block and the gap after it
    group.items.filterIsInstance<SettingsFooter<S>>().forEach { footer -> prefsFooter { footer.text(state) } }
}
