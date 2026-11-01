package com.huanchengfly.tieba.post.model

import androidx.compose.runtime.Immutable

/** A user as their profile page shows them. */
@Immutable
data class UserProfile(
    val id: Long,
    val name: String,
    /** Display name, empty when it is the account name. */
    val nameShow: String,
    val portrait: String,
    val intro: String = "",
    /** 1 male, 2 female, anything else unknown. */
    val sex: Int = 0,
    val tiebaUid: String = "",
    val ipLocation: String = "",
    /** Years on Tieba, as the server words it. */
    val tbAge: String = "",
    val followCount: Int = 0,
    val fanCount: Int = 0,
    val agreeCount: Long = 0,
    val threadCount: Int = 0,
    val postCount: Int = 0,
    val forumCount: Int = 0,
    /** Whether the viewer follows them. */
    val followed: Boolean = false,
    /** The moderator title they hold, when they hold one. */
    val bazhuTitle: String? = null,
    /** The field Tieba verified them as an expert in, when it did. */
    val godField: String? = null,
    /** The birthday in Unix seconds, 0 when unset. */
    val birthdayTime: Long = 0,
    val birthdayShown: Boolean = false,
) {
    val displayName: String get() = nameShow.ifEmpty { name }
}

/** A forum a user follows, as their profile lists it. */
@Immutable
data class LikedForum(
    val id: Long,
    val name: String,
    val avatar: String,
    val level: Int,
    val slogan: String,
)
