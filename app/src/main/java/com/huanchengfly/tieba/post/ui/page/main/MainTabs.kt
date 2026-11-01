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
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.rememberPreferenceAsState
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
        ),
    ).toImmutableList()
}
