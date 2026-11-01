package com.huanchengfly.tieba.post.repository

import android.content.Context
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.ApplicationScope
import com.huanchengfly.tieba.post.database.HistoryDao
import com.huanchengfly.tieba.post.database.HistoryEntity
import com.huanchengfly.tieba.post.model.ForumDetail
import com.huanchengfly.tieba.post.models.ForumHistoryExtra
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** The browsing history of forums. */
fun interface ForumHistory {
    fun save(forum: ForumDetail)
}

class DatabaseForumHistory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: HistoryDao,
    @ApplicationScope private val scope: CoroutineScope,
) : ForumHistory {
    override fun save(forum: ForumDetail) {
        val entry = HistoryEntity(
            type = HistoryEntity.TYPE_FORUM,
            data = forum.name,
            title = context.getString(R.string.title_forum, forum.name),
            extras = Json.encodeToString(ForumHistoryExtra(forum.id)),
            avatar = forum.avatar,
        )
        scope.launch { dao.visit(entry, System.currentTimeMillis()) }
    }
}
