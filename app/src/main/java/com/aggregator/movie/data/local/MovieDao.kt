package com.aggregator.movie.data.local

import androidx.room.*
import com.aggregator.movie.data.model.WatchHistoryEntity
import com.aggregator.movie.data.model.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao {
    // === 观看历史 ===
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entity: WatchHistoryEntity)

    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC LIMIT 50")
    fun getWatchHistory(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE movieId = :movieId LIMIT 1")
    suspend fun getHistoryByMovieId(movieId: String): WatchHistoryEntity?

    @Delete
    suspend fun deleteHistory(entity: WatchHistoryEntity)

    @Query("DELETE FROM watch_history")
    suspend fun clearHistory()

    // === 收藏 ===
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(entity: FavoriteEntity)

    @Query("SELECT * FROM favorite ORDER BY addedAt DESC")
    fun getFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite WHERE movieId = :movieId)")
    fun isFavorite(movieId: String): Flow<Boolean>

    @Query("DELETE FROM favorite WHERE movieId = :movieId")
    suspend fun deleteFavorite(movieId: String)

    @Query("DELETE FROM favorite")
    suspend fun clearFavorites()
}

@Database(
    entities = [WatchHistoryEntity::class, FavoriteEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MovieDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao
}
