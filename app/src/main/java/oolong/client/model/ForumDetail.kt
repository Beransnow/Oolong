package oolong.client.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** A forum as its page shows it, and how the viewer stands in it. */
@Immutable
data class ForumDetail(
    val id: Long,
    val name: String,
    val avatar: String,
    /** The forum's one-line introduction, empty when it has none. */
    val slogan: String = "",
    val memberCount: Int = 0,
    val threadCount: Int = 0,
    val postCount: Int = 0,
    /** Whether the viewer follows the forum; the level fields only mean something when they do. */
    val liked: Boolean = false,
    val level: Int = 0,
    val levelName: String = "",
    val score: Int = 0,
    val levelUpScore: Int = 0,
    val signedIn: Boolean = false,
    /** Days in a row the viewer has signed in. */
    val signedDays: Int = 0,
    /** How many have signed in to the forum today. */
    val todaySignCount: Int = 0,
    /** Whether the page shows a star or activity header image, which following it reports. */
    val hasHead: Boolean = false,
    /** The title of the forum's rules, when it has any. */
    val ruleTitle: String? = null,
    /** The classes good threads are filed under; id 0 is all of them. */
    val goodClasses: ImmutableList<GoodClass> = persistentListOf(),
    val tabs: ImmutableList<ForumTab> = ForumTab.Fixed,
) {
    val summary: ForumSummary get() = ForumSummary(id, name, avatar)
}

@Immutable
data class GoodClass(val id: Int, val name: String)

/** A list of the forum's threads. */
@Immutable
sealed interface ForumTab {
    data object Good : ForumTab

    data object Hot : ForumTab

    data object Latest : ForumTab

    /** A tab the forum's moderators set up. */
    data class Custom(val id: Int, val name: String, val type: Int) : ForumTab

    companion object {
        /** The tabs every forum has, in the order they come before its own. */
        val Fixed: ImmutableList<ForumTab> = persistentListOf(Good, Latest, Hot)
    }
}

/** The sort types of the two orders a forum's latest threads can be listed in. */
object ForumSort {
    const val REPLY_TIME = 0
    const val POST_TIME = 1
}
