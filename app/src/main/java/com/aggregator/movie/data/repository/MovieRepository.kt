package com.aggregator.movie.data.repository

import com.aggregator.movie.data.api.*
import com.aggregator.movie.data.local.MovieDao
import com.aggregator.movie.data.model.*
import kotlinx.coroutines.flow.Flow

/**
 * 影视数据仓库 - 统一数据入口
 * 聚合多个影视源，提供统一API给ViewModel
 */
class MovieRepository(
    private val sourceManager: SourceManager,
    private val dao: MovieDao
) {
    // ===== 影视数据 =====
    
    suspend fun getHomeData(): Result<HomeData> = runCatching {
        sourceManager.getHomeData()
    }
    
    suspend fun getCategories(): Result<List<Category>> = runCatching {
        sourceManager.getPrimarySource()?.getCategories() ?: emptyList()
    }
    
    suspend fun getMoviesByCategory(categoryId: String, page: Int): Result<SearchResult> = runCatching {
        sourceManager.getPrimarySource()?.getMoviesByCategory(categoryId, page)
            ?: SearchResult(emptyList(), 0, page)
    }
    
    suspend fun search(keyword: String, page: Int): Result<SearchResult> = runCatching {
        sourceManager.searchAll(keyword, page)
    }
    
    suspend fun getMovieDetail(movieId: String, sourceId: String): Result<Movie?> = runCatching {
        sourceManager.getPrimarySource()?.getMovieDetail(movieId)
    }
    
    suspend fun getPlaySources(movieId: String, sourceId: String): Result<List<PlaySource>> = runCatching {
        sourceManager.getPrimarySource()?.getPlaySources(movieId) ?: emptyList()
    }
    
    /**
     * 解析播放地址（含自动换源）
     */
    suspend fun resolvePlayUrl(
        playSources: List<PlaySource>,
        episodeIndex: Int
    ): Result<PlayUrl> = runCatching {
        sourceManager.resolvePlayUrlWithFallback(playSources, episodeIndex)
            ?: throw Exception("所有线路均无法播放")
    }
    
    // ===== 观看历史 =====
    
    suspend fun saveHistory(movie: Movie, episodeIndex: Int, episodeTitle: String, position: Long, duration: Long) {
        dao.insertHistory(WatchHistoryEntity(
            movieId = movie.id,
            sourceId = movie.sourceId,
            title = movie.title,
            coverUrl = movie.coverUrl,
            episodeIndex = episodeIndex,
            episodeTitle = episodeTitle,
            position = position,
            duration = duration
        ))
    }
    
    fun getWatchHistory(): Flow<List<WatchHistoryEntity>> = dao.getWatchHistory()
    
    suspend fun getHistoryByMovieId(movieId: String): WatchHistoryEntity? = dao.getHistoryByMovieId(movieId)
    
    suspend fun clearHistory() = dao.clearHistory()
    
    // ===== 收藏 =====
    
    suspend fun addFavorite(movie: Movie) {
        dao.insertFavorite(FavoriteEntity(
            movieId = movie.id,
            sourceId = movie.sourceId,
            title = movie.title,
            coverUrl = movie.coverUrl,
            score = movie.score,
            year = movie.year,
            genre = movie.genre
        ))
    }
    
    suspend fun removeFavorite(movieId: String) = dao.deleteFavorite(movieId)
    
    fun isFavorite(movieId: String): Flow<Boolean> = dao.isFavorite(movieId)
    
    fun getFavorites(): Flow<List<FavoriteEntity>> = dao.getFavorites()
    
    suspend fun clearFavorites() = dao.clearFavorites()
}
