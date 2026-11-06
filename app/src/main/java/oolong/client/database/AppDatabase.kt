package oolong.client.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountEntity::class,
        DraftEntity::class,
        HistoryEntity::class,
        BlockEntity::class,
        TopForumEntity::class,
        SearchHistoryEntity::class,
        ForumSearchHistoryEntity::class,
    ],
    version = 6,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun draftDao(): DraftDao
    abstract fun historyDao(): HistoryDao
    abstract fun blockDao(): BlockDao
    abstract fun topForumDao(): TopForumDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        const val NAME = "app.db"
    }
}
