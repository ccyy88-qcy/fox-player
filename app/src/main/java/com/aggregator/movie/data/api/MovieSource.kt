package com.aggregator.movie.data.api

import com.aggregator.movie.data.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 影视解析引擎 - 多源聚合
 * 
 * 架构说明：
 * - 每个影视源实现 MovieSource 接口
 * - SourceManager 统一管理所有源，支持自动切换
 * - 解析流程：搜索/推荐 → 详情 → 播放页 → 直链提取
 * 
 * 新增影视源只需：
 * 1. 实现 MovieSource 接口
 * 2. 在 SourceManager 中注册
 */
interface MovieSource {
    val sourceId: String
    val sourceName: String
    val baseUrl: String
    
    /** 优先级，越高越优先使用 */
    val priority: Int get() = 0
    
    /** 是否可用（心跳检测） */
    suspend fun isAvailable(): Boolean
    
    /** 获取首页推荐 */
    suspend fun getHomeData(): HomeData
    
    /** 获取分类列表 */
    suspend fun getCategories(): List<Category>
    
    /** 分类筛选 */
    suspend fun getMoviesByCategory(categoryId: String, page: Int): SearchResult
    
    /** 搜索 */
    suspend fun search(keyword: String, page: Int): SearchResult
    
    /** 获取影片详情（含播放源） */
    suspend fun getMovieDetail(movieId: String): Movie?
    
    /** 获取播放地址列表 */
    suspend fun getPlaySources(movieId: String): List<PlaySource>
    
    /** 解析最终播放直链 */
    suspend fun resolvePlayUrl(playUrl: String): PlayUrl
}

/**
 * 影视源管理器 - 多源聚合+自动换源
 */
class SourceManager(private val sources: List<MovieSource>) {
    
    val sortedSources = sources.sortedByDescending { it.priority }
    
    /** 当前活跃源索引 */
    var activeSourceIndex = 0
    
    /** 切换活跃源 */
    fun setActiveSource(index: Int) {
        if (index in sortedSources.indices) activeSourceIndex = index
    }
    
    /** 获取所有源 */
    fun getAllSources(): List<MovieSource> = sortedSources
    
    /** 获取当前活跃源 */
    fun getActiveSource(): MovieSource? = sortedSources.getOrNull(activeSourceIndex)
    
    /** 获取优先级最高的可用源（兜底） */
    fun getPrimarySource(): MovieSource? = sortedSources.firstOrNull()
    
    /** 获取所有可用源 */
    suspend fun getAvailableSources(): List<MovieSource> = coroutineScope {
        val availability = sortedSources.map { source ->
            async {
                try {
                    if (source.isAvailable()) source else null
                } catch (e: Exception) { null }
            }
        }
        availability.awaitAll().filterNotNull()
    }
    
    /** 聚合搜索 - 所有源结果合并去重 */
    suspend fun searchAll(keyword: String, page: Int): SearchResult = coroutineScope {
        val results = sortedSources.map { source ->
            async {
                try {
                    if (source.isAvailable()) source.search(keyword, page) else null
                } catch (e: Exception) { null }
            }
        }.awaitAll().filterNotNull()
        
        val allMovies = results.flatMap { it.movies }
            .distinctBy { it.title + it.year }
        SearchResult(allMovies, 1, page)
    }
    
    /** 聚合首页推荐 */
    suspend fun getHomeData(): HomeData = coroutineScope {
        val available = getAvailableSources()
        if (available.isEmpty()) return@coroutineScope HomeData()
        
        // 取优先级最高的可用源
        val primary = available.first()
        try {
            primary.getHomeData()
        } catch (e: Exception) {
            // 降级到下一个源
            available.drop(1).firstOrNull()?.getHomeData() ?: HomeData()
        }
    }
    
    /** 自动换源解析播放地址 */
    suspend fun resolvePlayUrlWithFallback(
        sources: List<PlaySource>,
        episodeIndex: Int
    ): PlayUrl? {
        for (source in sources) {
            if (episodeIndex >= source.episodes.size) continue
            val episode = source.episodes[episodeIndex]
            try {
                val playUrl = resolvePlayUrl(episode.url)
                if (playUrl.url.isNotBlank()) return playUrl
            } catch (e: Exception) {
                continue // 当前线路失败，尝试下一条
            }
        }
        return null
    }
    
    private suspend fun resolvePlayUrl(url: String): PlayUrl {
        // 如果已经是直链
        if (url.endsWith(".mp4") || url.endsWith(".m3u8") || url.endsWith(".flv")) {
            return PlayUrl(url = url)
        }
        
        // 否则通过各源解析
        for (source in sortedSources) {
            try {
                return source.resolvePlayUrl(url)
            } catch (e: Exception) { continue }
        }
        return PlayUrl(url = url)
    }
}

/**
 * OkHttp 客户端工厂
 */
object HttpClientFactory {
    fun create(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .build()
            chain.proceed(request)
        }
        .build()
}
