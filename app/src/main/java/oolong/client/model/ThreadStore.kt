package oolong.client.model

import androidx.compose.runtime.Immutable

/** A thread in the user's thread store. */
@Immutable
data class StoredThread(
    val threadId: Long,
    val title: String,
    val author: User?,
    /** Whether the thread was deleted since it was stored. */
    val deleted: Boolean,
    /** The post the user read up to. */
    val markPostId: Long,
    val maxPostId: Long,
    /** The floor of the newest post. */
    val latestFloor: Int,
    /** How many posts came after the one read up to. */
    val newPostCount: Int,
) {
    val hasUpdate: Boolean get() = newPostCount > 0 && latestFloor > 0
}
