package com.huanchengfly.tieba.post.model

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
    ) : HistoryEntry

    @Immutable
    data class Forum(
        override val id: Long,
        override val time: Long,
        override val avatar: String,
        val name: String,
        val title: String,
    ) : HistoryEntry
}
