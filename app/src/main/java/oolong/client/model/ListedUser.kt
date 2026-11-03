package oolong.client.model

import androidx.compose.runtime.Immutable

/** A user in a list of followers or of those followed: who they are, and the line they wrote about themselves. */
@Immutable
data class ListedUser(
    val user: User,
    val intro: String,
    /** Whether the viewer follows them. */
    val followed: Boolean,
)
