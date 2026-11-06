package oolong.client.ui.common.settings

import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import oolong.client.ui.navigation.Key

/**
 * A settings page declared as data (docs/settings-declarative.md): its title, the key that opens
 * it, and its groups of rows. [SettingsScreen] draws it, and settings search reads it, so a row
 * declared here is both shown and found.
 *
 * [S] is what the page knows only while it shows, such as what the system allows; rows read it
 * through their lambdas. A page without such a state is a `SettingsPage<Unit>`, see [settingsPage].
 */
abstract class SettingsPage<S>(@StringRes val title: Int, val key: Key) {
    /** The page's state, made as the page shows. Search never calls it. */
    @Composable
    abstract fun rememberState(): S

    /** What the page does besides its rows while it shows, such as rescheduling work as a setting changes. */
    @Composable
    open fun Effects(state: S) {
    }

    protected abstract fun SettingsScope<S>.content()

    /** The page's groups, in order. Declared once: what varies is in the rows' lambdas, not here. */
    val groups: List<SettingsGroup<S>> by lazy { SettingsScope<S>().apply { content() }.groups }
}

/** A page whose rows need nothing but the stored settings. */
fun settingsPage(@StringRes title: Int, key: Key, content: SettingsScope<Unit>.() -> Unit): SettingsPage<Unit> =
    object : SettingsPage<Unit>(title, key) {
        @Composable
        override fun rememberState() = Unit

        override fun SettingsScope<Unit>.content() = content()
    }

/** What a row's availability depends on: the device and whether anyone is signed in. */
class SettingsEnvironment(val context: Context, val signedIn: Boolean)

/**
 * Whether a row exists at all here, whatever the settings are: the system's version, signing in,
 * what the device supports. Both the page and search check it, so search never finds a row the
 * page doesn't have.
 */
fun interface Availability {
    fun isMet(environment: SettingsEnvironment): Boolean

    companion object {
        val Always = Availability { true }
        val SignedIn = Availability { it.signedIn }

        /** On Android [level] and later. */
        fun sdk(level: Int) = Availability { Build.VERSION.SDK_INT >= level }
    }
}

/** What stands above a group's rows. */
sealed interface SettingsHeader<in S> {
    /** A small grey title. */
    class Title(@StringRes val text: Int) : SettingsHeader<Any?>

    /** A gap of [height], for a group with no title. */
    class Gap(val height: Dp) : SettingsHeader<Any?>

    /** Something drawn instead, such as a summary bar. */
    class Custom<S>(val content: @Composable S.() -> Unit) : SettingsHeader<S>
}

/**
 * Rows in one block under their [header], with a gap after them; without a header, rows on their
 * own at the top of a page that has no groups.
 */
class SettingsGroup<S>(
    val header: SettingsHeader<S>?,
    val items: List<SettingsItem<S>>,
    /** Whether the group, its header and its notes exist here at all, as a row's [SettingsRow.available]. */
    val available: Availability = Availability.Always,
)

/** What a group holds: rows, and notes under them. */
sealed interface SettingsItem<S>

/** A note under the rows before it, outside their block. */
class SettingsFooter<S>(val text: @Composable S.() -> String) : SettingsItem<S>

/**
 * One row. What search needs is kept as plain values; how the row looks and acts is [content],
 * which gets the modifier the page sets on it.
 */
class SettingsRow<S>(
    /** Its title, null for a control whose group's title names it. */
    @StringRes val title: Int?,
    val kind: Kind,
    val available: Availability,
    /** Whether it shows now, as when it only matters while another setting is on; search ignores it. */
    val shown: (@Composable S.() -> Boolean)?,
    /** More words search finds it by, comma separated, per language. */
    @StringRes val aliases: Int?,
    val searchable: Boolean,
    val content: @Composable S.(Modifier) -> Unit,
) : SettingsItem<S> {
    enum class Kind { Value, Link, Action, Info, Custom }
}

/** A string resource as a row's text that doesn't change. */
fun <S> res(@StringRes id: Int): @Composable S.() -> String = { stringResource(id = id) }

@DslMarker
annotation class SettingsDsl

/** Declares a page's groups. */
@SettingsDsl
class SettingsScope<S> internal constructor() {
    internal val groups = mutableListOf<SettingsGroup<S>>()

    fun group(@StringRes title: Int, available: Availability = Availability.Always, rows: GroupScope<S>.() -> Unit) =
        group(SettingsHeader.Title(title), available, rows)

    fun group(header: SettingsHeader<S>, available: Availability = Availability.Always, rows: GroupScope<S>.() -> Unit) {
        groups += SettingsGroup(header, GroupScope<S>().apply(rows).items, available)
    }

    /** Rows with no header or gap, for a page too short to group. */
    fun rows(rows: GroupScope<S>.() -> Unit) {
        groups += SettingsGroup(null, GroupScope<S>().apply(rows).items)
    }
}
