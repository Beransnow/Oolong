package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.post.utils.EmoticonManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
        fun provideEmoticonRegistry(): EmoticonRegistry = EmoticonRegistry(EmoticonManager::registerEmoticon)
    }
}
