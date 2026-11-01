package com.huanchengfly.tieba.post.database

import com.huanchengfly.tieba.post.App
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DatabaseEntryPoint {
    fun database(): AppDatabase
}

/** The database, for the singletons that predate injection; injected code takes the DAOs instead. */
val appDatabase: AppDatabase
    get() = EntryPointAccessors.fromApplication(App.INSTANCE, DatabaseEntryPoint::class.java).database()
