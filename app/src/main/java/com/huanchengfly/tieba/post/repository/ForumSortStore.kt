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

/** The sort type each forum's latest threads were last ordered by, or the one set in the settings. */
interface ForumSortStore {
    suspend fun sortType(forumName: String): Int

    suspend fun setSortType(forumName: String, sortType: Int)
}

/**
 * Keeps each forum's sort type in the preferences the old forum page used; the settings page
 * offers 0 (reply time) and 1 (post time) as the default.
 */
class DataStoreForumSortStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : ForumSortStore {
    override suspend fun sortType(forumName: String): Int {
        val default = context.appPreferences.defaultSortType?.toIntOrNull() ?: ForumSort.REPLY_TIME
        return context.dataStore.data.first()[key(forumName)] ?: default
    }

    override suspend fun setSortType(forumName: String, sortType: Int) {
        context.dataStore.edit { it[key(forumName)] = sortType }
    }

    private fun key(forumName: String) = intPreferencesKey("${forumName}_sort_type")
}
