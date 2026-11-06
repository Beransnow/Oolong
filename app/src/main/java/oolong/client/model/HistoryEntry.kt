package oolong.client.model

import androidx.compose.runtime.Immutable

/** A thread or forum the user visited. */
@Immutable
sealed interface HistoryEntry {
    val id: Long

    /** When it was last visited, in Unix milliseconds. */
    val time: Long
    val avatar: String

    @Immutable
    data class Thread(
        override val id: Long,
        override val time: Long,
        override val avatar: String,
        val threadId: Long,
        val title: String,
        val authorName: String,
        /** The post the user read up to, 0 if unknown. */
        val postId: Long = 0,
        val seeLz: Boolean = false,
        /** The floor read up to, 0 if unknown. */
        val floor: Int = 0,
        /** Empty when not known, as for visits recorded before it was. */
        val forumName: String = "",
        val forumAvatar: String = "",
        /** A small picture of the thread, empty when it has none or it wasn't recorded. */
        val thumbnail: String = "",
    ) : HistoryEntry

    @Immutable
    data class Forum(
        override val id: Long,
        override val time: Long,
        override val avatar: String,
        val name: String,
        val title: String,
        /** How many times it was visited. */
        val visits: Int = 0,
    ) : HistoryEntry
}
