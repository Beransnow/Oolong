package oolong.client.ui.common.settings

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import oolong.client.ui.navigation.Key

/**
 * Something search finds: a setting, or a page. [path] names the pages on the way to it from the
 * hub; [page] is the page to open and [row] the title of the row to show there, null for a page.
 */
class SettingsEntry(
    val title: String,
    val aliases: List<String>,
    val path: List<String>,
    val page: Key,
    @StringRes val row: Int?,
)

/**
 * Every setting and page search can find, going down from [root] through the pages its links open
 * among [pages]. Only what exists here counts (see [Availability]); a link to a page that isn't
 * declared, as a list of data, is found as that page.
 */
fun settingsIndex(
    root: SettingsPage<*>,
    pages: List<SettingsPage<*>>,
    environment: SettingsEnvironment,
    resources: Resources,
): List<SettingsEntry> {
    val byKey = pages.associateBy { it.key }
    val entries = mutableListOf<SettingsEntry>()
    fun aliasesOf(@StringRes id: Int?) = id?.let { resources.getString(it).split(',', '，').map(String::trim).filter(String::isNotEmpty) }.orEmpty()

    fun visit(page: SettingsPage<*>, path: List<String>) {
        page.groups.filter { it.available.isMet(environment) }.forEach { group ->
            group.items.filterIsInstance<SettingsRow<*>>().forEach { row ->
                val titleId = row.title ?: return@forEach
                if (!row.available.isMet(environment)) return@forEach
                val title = resources.getString(titleId)
                val target = row.target
                if (target != null) {
                    // A link is found as the page it opens, and what is on that page under it
                    if (row.searchable) entries += SettingsEntry(title, aliasesOf(row.aliases), path, target, row = null)
                    byKey[target]?.let { visit(it, path + title) }
                } else if (row.searchable) {
                    entries += SettingsEntry(title, aliasesOf(row.aliases), path, page.key, titleId)
                }
            }
        }
    }
    visit(root, emptyList())
    return entries
}

/**
 * The entries that have [query], best first: those whose title starts with it, then those whose
 * title has it, then those with an alias that does, then those on a page whose name does.
 */
fun List<SettingsEntry>.search(query: String): List<SettingsEntry> {
    val words = query.trim()
    if (words.isEmpty()) return emptyList()
    fun rank(entry: SettingsEntry): Int? = when {
        entry.title.startsWith(words, ignoreCase = true) -> 0
        entry.title.contains(words, ignoreCase = true) -> 1
        entry.aliases.any { it.contains(words, ignoreCase = true) } -> 2
        entry.path.any { it.contains(words, ignoreCase = true) } -> 3
        else -> null
    }
    return mapNotNull { entry -> rank(entry)?.let { entry to it } }
        .sortedWith(compareBy({ it.second }, { it.first.path.size }))
        .map { it.first }
}

/**
 * The row a search result opened its page for, which the page scrolls to and lights up once,
 * then lets go.
 */
object SettingsHighlight {
    var target: Pair<Key, Int>? by mutableStateOf(null)
}
