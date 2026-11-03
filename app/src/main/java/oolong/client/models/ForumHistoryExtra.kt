package oolong.client.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ForumHistoryExtra(
    @SerialName("forum_id")
    val forumId: Long,
)
