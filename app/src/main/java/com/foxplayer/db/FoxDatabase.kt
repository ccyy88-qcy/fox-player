package com.foxplayer.db

import androidx.room.*
import com.foxplayer.model.Video
import com.foxplayer.model.VideoSource

@Database(
    entities = [FavoriteEntity::class, HistoryEntity::class, SourceEntity::class],
    version = 1,
    exportSchema = false
)
abstract class FoxDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun sourceDao(): SourceDao
}
