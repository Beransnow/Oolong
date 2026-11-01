package com.huanchengfly.tieba.post.models.database

import androidx.compose.runtime.Immutable

@Immutable
data class SearchHistory(
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val id: Long = 0,
)
