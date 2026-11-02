package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.post.api.models.protos.PollInfo
import com.huanchengfly.tieba.post.model.Poll
import com.huanchengfly.tieba.post.model.PollOption
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

/** Null when the thread has no poll. [now] is in seconds since the epoch. */
internal fun PollInfo.toPoll(now: Long = System.currentTimeMillis() / 1000): Poll? {
    if (options.isEmpty()) return null
    return Poll(
        title = title,
        multiple = is_multi != 0,
        options = options.map { PollOption(it.id, it.text, it.num) }.toImmutableList(),
        voters = total_num,
        hasVoted = is_polled != 0,
        chosen = polled_value.split(',').mapNotNull { it.trim().toIntOrNull() }.toImmutableSet(),
        closed = status != 0 || end_time in 1..now,
    )
}
