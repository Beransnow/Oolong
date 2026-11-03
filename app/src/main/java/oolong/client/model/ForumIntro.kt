package oolong.client.model

import androidx.compose.runtime.Immutable

/** What a forum's info page shows about it. */
@Immutable
data class ForumIntro(
    val name: String,
    val avatar: String,
    val memberCount: Int,
    val threadCount: Int,
    val slogan: String,
    /** The introduction, as plain text. */
    val intro: String,
)
