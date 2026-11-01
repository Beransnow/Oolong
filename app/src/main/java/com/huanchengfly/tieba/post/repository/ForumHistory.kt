package com.huanchengfly.tieba.post.repository

import android.content.Context
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.models.ForumHistoryExtra
import com.huanchengfly.tieba.post.models.database.History
import com.huanchengfly.tieba.post.model.ForumDetail
import com.huanchengfly.tieba.post.utils.HistoryUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** The browsing history of forums. */
fun interface ForumHistory {
    fun save(forum: ForumDetail)
}

class HistoryUtilForumHistory @Inject constructor(
    @ApplicationContext private val context: Context,
) : ForumHistory {
    override fun save(forum: ForumDetail) {
        HistoryUtil.saveHistory(
            History(
                title = context.getString(R.string.title_forum, forum.name),
                data = forum.name,
                type = HistoryUtil.TYPE_FORUM,
                extras = Json.encodeToString(ForumHistoryExtra(forum.id)),
                avatar = forum.avatar,
            ),
        )
    }
}
