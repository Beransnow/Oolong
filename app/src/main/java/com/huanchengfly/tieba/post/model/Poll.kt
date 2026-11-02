package com.huanchengfly.tieba.post.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

/** A thread's poll. */
@Immutable
data class Poll(
    val title: String,
    /** More than one option may be chosen. */
    val multiple: Boolean,
    val options: ImmutableList<PollOption>,
    /** How many people have voted. */
    val voters: Long,
    val hasVoted: Boolean,
    /** The options the viewer chose, when the server says which. */
    val chosen: ImmutableSet<Int>,
    /** Voting has ended or was closed. */
    val closed: Boolean,
) {
    val votes: Long get() = options.sumOf { it.votes }
}

@Immutable
data class PollOption(val id: Int, val text: String, val votes: Long)
