package com.huanchengfly.tieba.post.model

import androidx.compose.runtime.Immutable

/** One of a thread's pictures, as the picture viewer pages through them. */
@Immutable
data class ThreadPicture(
    val picId: String,
    val originUrl: String,
    /** The large version, when the server offers to show the original instead. */
    val url: String?,
    /** The position among all the thread's pictures, from 1. */
    val overallIndex: Int,
    val postId: Long? = null,
)

/** A run of a thread's pictures, and how many it has in all. */
data class PicturePage(val total: Int, val pictures: List<ThreadPicture>)
