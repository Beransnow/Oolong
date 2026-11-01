package com.huanchengfly.tieba.post.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * One page on the back stack. [id] tells apart two entries for the same [key], such as a thread
 * opened again from its author's profile, so that each keeps its own state.
 */
@Serializable
data class Page(val key: Key, val id: String = UUID.randomUUID().toString()) : NavKey

private val BackStackConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) { subclass(Page::class, Page.serializer()) }
    }
}

/**
 * The app's back stack, starting at [MainKey] with [linked] above it when the app was opened by
 * a link. It survives process death, when [linked] is not applied again.
 */
@Composable
fun rememberAppBackStack(linked: Key? = null): NavBackStack<NavKey> =
    rememberNavBackStack(BackStackConfiguration, *listOfNotNull(Page(MainKey), linked?.let(::Page)).toTypedArray())

val NavKey.key: Key
    get() = (this as Page).key

/** How a page shows: filling the window, or over the pages below it. */
enum class Presentation { Page, Sheet, Dialog }

val Key.presentation: Presentation
    get() = when (this) {
        is ReplyKey, is SubPostsSheetKey -> Presentation.Sheet
        is CopyTextKey -> Presentation.Dialog
        else -> Presentation.Page
    }

/** Navigates by changing [backStack]. */
class BackStackNavigator(private val backStack: NavBackStack<NavKey>) : Navigator {
    override fun navigate(key: Key) {
        // A page opened from a sheet takes the sheet's place, so back leads past it
        if (key.presentation == Presentation.Page) {
            while (backStack.size > 1 && backStack.last().key.presentation == Presentation.Sheet) {
                backStack.removeAt(backStack.lastIndex)
            }
        }
        backStack.add(Page(key))
    }

    override fun navigateUp() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
}
