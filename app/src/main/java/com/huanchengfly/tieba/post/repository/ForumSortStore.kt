package com.huanchengfly.tieba.post.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.huanchengfly.tieba.post.dataStore
import com.huanchengfly.tieba.post.model.ForumSort
import com.huanchengfly.tieba.post.utils.appPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** How each forum's latest threads were last ordered, or the order set in the settings. */
interface ForumSortStore {
    suspend fun sort(forumName: String): ForumSort

    suspend fun setSort(forumName: String, sort: ForumSort)
}

/**
 * Keeps each forum's order in the preferences the old forum page used, where 0 is reply time and
 * 1 post time, the two values the settings page offers too; smart order is stored as 2.
 */
class DataStoreForumSortStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : ForumSortStore {
    override suspend fun sort(forumName: String): ForumSort {
        val default = context.appPreferences.defaultSortType?.toIntOrNull() ?: 0
        val stored = context.dataStore.data.first()[key(forumName)] ?: default
        return when (stored) {
            1 -> ForumSort.PostTime
            2 -> ForumSort.Smart
            else -> ForumSort.ReplyTime
        }
    }

    override suspend fun setSort(forumName: String, sort: ForumSort) {
        val stored = when (sort) {
            ForumSort.ReplyTime -> 0
            ForumSort.PostTime -> 1
            ForumSort.Smart -> 2
        }
        context.dataStore.edit { it[key(forumName)] = stored }
    }

    private fun key(forumName: String) = intPreferencesKey("${forumName}_sort_type")
}
