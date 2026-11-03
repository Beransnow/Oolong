package com.huanchengfly.tieba.post.ui.page.reader

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Where the reader was left in each thread, told to the thread page under it so it can follow. */
@Singleton
class ReaderPositions @Inject constructor() {
    private val left = MutableSharedFlow<Pair<Long, Long>>(extraBufferCapacity = 8)

    /** The reader of thread [threadId] closed at post [postId]. */
    fun leave(threadId: Long, postId: Long) {
        left.tryEmit(threadId to postId)
    }

    /** The post the reader of thread [threadId] closes at, each time it does. */
    fun left(threadId: Long): Flow<Long> = left.filter { it.first == threadId }.map { it.second }
}
