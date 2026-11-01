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

    companion object {
        @Provides
        fun provideVideoThreadFilter(@ApplicationContext context: Context): VideoThreadFilter =
            VideoThreadFilter { context.appPreferences.blockVideo }

        @Provides
        fun provideEmoticonRegistry(): EmoticonRegistry = EmoticonRegistry(EmoticonManager::registerEmoticon)
    }
}
