package com.aggregator.movie.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 影视条目 - 统一数据模型
 * 所有解析源的数据都映射为此模型
 */
data class Movie(
    val id: String,              // 唯一ID (源ID+原始ID哈希)
    val sourceId: String,        // 来源标识
    val title: String,           // 标题
    val coverUrl: String = "",   // 封面图
    val score: String = "",      // 评分
    val year: String = "",       // 年份
    val region: String = "",     // 地区
    val genre: String = "",      // 类型
    val director: String = "",   // 导演
    val actors: String = "",     // 演员
    val description: String = "",// 简介
    val type: MovieType = MovieType.MOVIE // 影视类型
)

enum class MovieType {
    MOVIE, TV, ANIME, VARIETY
}

/**
 * 播放源 - 一部影片可能有多个播放源
 */
data class PlaySource(
    val name: String,            // 线路名称 (如 "线路A", "极速云")
    val episodes: List<Episode> = emptyList()
)

data class Episode(
    val index: Int,              // 集序号
    val title: String,           // 集标题
    val url: String              // 播放地址（可能是页面URL，需要进一步解析）
)

/**
 * 解析结果 - 最终播放地址
 */
data class PlayUrl(
    val url: String,             // 最终直链
    val headers: Map<String, String> = emptyMap(), // 需要携带的请求头
    val format: VideoFormat = VideoFormat.MP4
)

enum class VideoFormat {
    MP4, M3U8, FLV, OTHER
}

/**
 * 分类信息
 */
data class Category(
    val id: String,
    val name: String,
    val type: MovieType
)

/**
 * 搜索结果
 */
data class SearchResult(
    val movies: List<Movie>,
    val totalPage: Int,
    val currentPage: Int
)

/**
 * 首页推荐数据
 */
data class HomeData(
    val banners: List<Movie> = emptyList(),
    val hotMovies: List<Movie> = emptyList(),
    val hotTv: List<Movie> = emptyList(),
    val hotAnime: List<Movie> = emptyList(),
    val latestMovies: List<Movie> = emptyList()
)

// ===== Room 实体 =====

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val movieId: String,
    val sourceId: String,
    val title: String,
    val coverUrl: String,
    val episodeIndex: Int = 0,
    val episodeTitle: String = "",
    val position: Long = 0,      // 播放位置(ms)
    val duration: Long = 0,      // 总时长(ms)
    val watchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorite")
data class FavoriteEntity(
    @PrimaryKey val movieId: String,
    val sourceId: String,
    val title: String,
    val coverUrl: String,
    val score: String,
    val year: String,
    val genre: String,
    val addedAt: Long = System.currentTimeMillis()
)
