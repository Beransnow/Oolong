package com.huanchengfly.tieba.post.repository

import android.content.Context
import com.huanchengfly.tieba.post.utils.EmoticonManager
import com.huanchengfly.tieba.post.utils.appPreferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindBlockRuleSource(source: BlockManagerRuleSource): BlockRuleSource

    @Binds
    abstract fun bindThreadHistory(history: HistoryUtilThreadHistory): ThreadHistory

    @Binds
    abstract fun bindForumHistory(history: HistoryUtilForumHistory): ForumHistory

    @Binds
    abstract fun bindForumSortStore(store: DataStoreForumSortStore): ForumSortStore

    @Binds
    abstract fun bindOwnProfileCache(cache: AccountOwnProfileCache): OwnProfileCache

    companion object {
        @Provides
        fun provideVideoThreadFilter(@ApplicationContext context: Context): VideoThreadFilter =
            VideoThreadFilter { context.appPreferences.blockVideo }

        @Provides
        fun provideConcernFeedStore(@ApplicationContext context: Context): ConcernFeedStore = object : ConcernFeedStore {
            override var lastRequestUnix: Long
                get() = context.appPreferences.userLikeLastRequestUnix
                set(value) {
                    context.appPreferences.userLikeLastRequestUnix = value
                }
        }

        @Provides
        fun provideEmoticonRegistry(): EmoticonRegistry = EmoticonRegistry(EmoticonManager::registerEmoticon)
    }
}
