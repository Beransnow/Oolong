package com.huanchengfly.tieba.post.models.database

import androidx.compose.runtime.Immutable

@Immutable
class SearchPostHistory(
    val content: String,
    val forumName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val id: Long = 0,
)
