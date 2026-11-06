package oolong.client.models

import kotlinx.serialization.Serializable

@Serializable
data class ThreadHistoryInfoBean(
    val isSeeLz: Boolean = false,
    val pid: String? = null,
    val forumName: String? = null,
    val floor: String? = null,
    /** A small picture of the thread: its first picture, or its video's cover. */
    val thumbnail: String? = null,
    val forumAvatar: String? = null,
)
