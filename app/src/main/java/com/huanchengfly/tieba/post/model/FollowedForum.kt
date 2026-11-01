package com.huanchengfly.tieba.post.model

import androidx.compose.runtime.Immutable

/** A forum the user follows, as the home page lists it. */
@Immutable
data class FollowedForum(
    val id: Long,
    val name: String,
    val avatar: String,
    val level: Int,
    val signedIn: Boolean,
)

/** A forum the user visited lately. */
@Immutable
data class VisitedForum(
    val name: String,
    /** How the visit was titled, the forum's name followed by 吧. */
    val title: String,
    val avatar: String,
)
