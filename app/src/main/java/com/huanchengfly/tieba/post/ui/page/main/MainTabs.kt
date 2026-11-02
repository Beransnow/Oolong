package com.huanchengfly.tieba.post.ui.page.main

import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.models.database.Account
import com.huanchengfly.tieba.post.rememberPreferenceAsState
import com.huanchengfly.tieba.post.repository.AccountActions
import com.huanchengfly.tieba.post.ui.common.LocalAccount
import com.huanchengfly.tieba.post.ui.common.LocalAccountActions
import com.huanchengfly.tieba.post.ui.common.LocalAllAccounts
import com.huanchengfly.tieba.post.ui.common.theme.compose.Symbols
import com.huanchengfly.tieba.post.ui.navigation.LoginKey
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.widgets.compose.Avatar
import com.huanchengfly.tieba.post.ui.widgets.compose.ContextMenuItem
import com.huanchengfly.tieba.post.utils.StringUtil
import com.huanchengfly.tieba.post.utils.appPreferences
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Which main tab is chosen and how many notifications its badge counts. Held beside the pages,
 * so the main page and the navigation rail next to the panes show the same thing.
 */
@Stable
class MainTabs(selected: String = HOME, messageCount: Int = 0) {
    var selected by mutableStateOf(selected)
    var messageCount by mutableIntStateOf(messageCount)

    companion object {
        const val HOME = "home"
        const val EXPLORE = "explore"
        const val NOTIFICATIONS = "notification"
        const val USER = "user"

        val Saver: Saver<MainTabs, Any> = listSaver(
            save = { listOf(it.selected, it.messageCount) },
            restore = { MainTabs(it[0] as String, it[1] as Int) },
        )
    }
}

val LocalMainTabs = compositionLocalOf { MainTabs() }

/** Whether the rail beside the pages shows the tabs, so the main page leaves out its own bar. */
val LocalNavigationRailShown = compositionLocalOf { false }

/** The main tabs as the navigation shows them; explore only while it is not hidden. */
@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
fun rememberMainTabItems(tabs: MainTabs): ImmutableList<NavigationItem> {
    val hideExplore by rememberPreferenceAsState(
        key = booleanPreferencesKey("hideExplore"),
        defaultValue = LocalContext.current.appPreferences.hideExplore
    )
    val messageCount = tabs.messageCount
    val navigator = LocalNavigator.current
    val account = LocalAccount.current
    val allAccounts = LocalAllAccounts.current
    val accounts = LocalAccountActions.current
    val newAccount = stringResource(id = R.string.title_new_account)
    val switchAccount = stringResource(id = R.string.title_switch_account)
    return listOfNotNull(
        NavigationItem(
            id = MainTabs.HOME,
            icon = { AnimatedImageVector.animatedVectorResource(id = R.drawable.ic_animated_rounded_inventory_2) },
            title = { stringResource(id = R.string.title_main) },
        ),
        if (hideExplore) null
        else NavigationItem(
            id = MainTabs.EXPLORE,
            icon = { AnimatedImageVector.animatedVectorResource(id = R.drawable.ic_animated_toy_fans) },
            title = { stringResource(id = R.string.title_explore) },
        ),
        NavigationItem(
            id = MainTabs.NOTIFICATIONS,
            icon = { AnimatedImageVector.animatedVectorResource(id = R.drawable.ic_animated_rounded_notifications) },
            title = { stringResource(id = R.string.title_notifications) },
            badge = messageCount > 0,
            badgeText = "$messageCount",
            onClick = { tabs.messageCount = 0 },
        ),
        NavigationItem(
            id = MainTabs.USER,
            icon = { AnimatedImageVector.animatedVectorResource(id = R.drawable.ic_animated_rounded_person) },
            title = { stringResource(id = R.string.title_user) },
            avatar = account?.let { StringUtil.getAvatarUrl(it.portrait) },
            menu = account?.let { current -> accountMenu(current, allAccounts, accounts, newAccount) { navigator.navigate(LoginKey) } },
            menuLabel = switchAccount,
        ),
    ).toImmutableList()
}

/** The accounts to switch to, [current] checked, then adding another. */
private fun accountMenu(
    current: Account,
    all: List<Account>,
    actions: AccountActions,
    newAccount: String,
    onNewAccount: () -> Unit,
): () -> List<ContextMenuItem> = {
    all.map { account ->
        ContextMenuItem(
            icon = null,
            label = account.nameShow ?: account.name,
            leading = { Avatar(data = StringUtil.getAvatarUrl(account.portrait), size = 28.dp, contentDescription = null) },
            selected = account.id == current.id,
        ) { actions.switchTo(account.id) }
    } + ContextMenuItem(Symbols.Add, newAccount, startsGroup = true, onClick = onNewAccount)
}
