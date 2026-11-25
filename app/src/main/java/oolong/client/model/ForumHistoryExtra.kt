package oolong.client.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ForumHistoryExtra(
    @SerialName("forum_id")
    val forumId: Long,
)
