package com.foxplayer.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE videoId = :vid AND sourceKey = :sk)")
    suspend fun isFavorite(vid: String, sk: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE videoId = :vid AND sourceKey = :sk")
    suspend fun delete(vid: String, sk: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY updatedAt DESC LIMIT 100")
    fun getRecent(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HistoryEntity)

    @Query("DELETE FROM history WHERE videoId = :vid AND sourceKey = :sk")
    suspend fun delete(vid: String, sk: String)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY sortOrder ASC")
    fun getAll(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources WHERE enabled = 1 ORDER BY sortOrder ASC")
    fun getEnabled(): Flow<List<SourceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SourceEntity>)

    @Query("DELETE FROM sources WHERE key = :key")
    suspend fun delete(key: String)

    @Query("UPDATE sources SET enabled = :enabled WHERE key = :key")
    suspend fun setEnabled(key: String, enabled: Boolean)
}
