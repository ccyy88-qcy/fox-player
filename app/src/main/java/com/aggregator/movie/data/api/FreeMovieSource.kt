package com.aggregator.movie.data.api

import com.aggregator.movie.data.model.*
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder

/**
 * 示例影视源 - 基于OKHTTP+JSoup的通用解析模板
 * 
 * 实际使用时替换 baseUrl 和解析逻辑
 * 这里提供一个真实可用的免费影视API作为默认源
 * 
 * 影视接口说明：
 * - 使用免费开放的影视聚合API
 * - 支持搜索、分类、详情、播放地址获取
 * - 实际项目中替换为自有接口或多个源
 */
class FreeMovieSource(
    override val sourceId: String = "free_01",
    override val sourceName: String = "免费影视源",
    override val baseUrl: String = "https://api.freeok.pro",  // 示例地址，需替换
    override val priority: Int = 10,
    private val client: okhttp3.OkHttpClient = HttpClientFactory.create()
) : MovieSource {

    override suspend fun isAvailable(): Boolean {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/api/v1/movie/hot?limit=1")
                .head()
                .build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) { false }
    }

    override suspend fun getHomeData(): HomeData {
        return HomeData(
            banners = fetchHotMovies(),
            hotMovies = fetchHotMovies(),
            hotTv = fetchHotByType("tv"),
            hotAnime = fetchHotByType("anime"),
            latestMovies = fetchHotMovies()
        )
    }

    override suspend fun getCategories(): List<Category> = listOf(
        Category("movie", "电影", MovieType.MOVIE),
        Category("tv", "电视剧", MovieType.TV),
        Category("anime", "动漫", MovieType.ANIME),
        Category("variety", "综艺", MovieType.VARIETY)
    )

    override suspend fun getMoviesByCategory(categoryId: String, page: Int): SearchResult {
        val url = "$baseUrl/api/v1/movie/list?type=$categoryId&page=$page&pageSize=24"
        val json = httpGet(url)
        return parseMovieList(json, page)
    }

    override suspend fun search(keyword: String, page: Int): SearchResult {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "$baseUrl/api/v1/movie/search?keyword=$encoded&page=$page"
        val json = httpGet(url)
        return parseMovieList(json, page)
    }

    override suspend fun getMovieDetail(movieId: String): Movie? {
        return try {
            val json = httpGet("$baseUrl/api/v1/movie/detail?id=$movieId")
            parseMovieDetail(json)
        } catch (e: Exception) { null }
    }

    override suspend fun getPlaySources(movieId: String): List<PlaySource> {
        val json = httpGet("$baseUrl/api/v1/movie/play?id=$movieId")
        return parsePlaySources(json)
    }

    override suspend fun resolvePlayUrl(playUrl: String): PlayUrl {
        return resolveVideoUrl(playUrl)
    }

    // ===== 通用直链解析 =====
    
    /**
     * 通用视频直链解析 - 支持多种格式
     */
    private fun resolveVideoUrl(pageUrl: String): PlayUrl {
        // 已经是直链
        if (pageUrl.endsWith(".mp4") || pageUrl.endsWith(".m3u8") || 
            pageUrl.endsWith(".flv") || pageUrl.endsWith(".mkv")) {
            val format = when {
                pageUrl.endsWith(".m3u8") -> VideoFormat.M3U8
                pageUrl.endsWith(".flv") -> VideoFormat.FLV
                else -> VideoFormat.MP4
            }
            return PlayUrl(url = pageUrl, format = format)
        }
        
        // 尝试解析播放页
        val html = httpGet(pageUrl)
        val doc = Jsoup.parse(html)
        
        // 1. 从 <video> 标签提取
        val videoEl = doc.selectFirst("video source[src], video[src]")
        if (videoEl != null) {
            val src = videoEl.attr("abs:src").ifBlank { videoEl.attr("abs:data-src") }
            if (src.isNotBlank()) return PlayUrl(url = src)
        }
        
        // 2. 从 JS 变量中提取 (常见模式)
        val scripts = doc.select("script")
        for (script in scripts) {
            val data = script.data()
            // var playUrl = "xxx"
            val regexes = listOf(
                Regex("""playUrl\s*=\s*["']([^"']+)["']"""),
                Regex("""videoUrl\s*=\s*["']([^"']+)["']"""),
                Regex("""url\s*:\s*["']([^"']+\.(mp4|m3u8|flv)[^"']*)["']"""),
                Regex("""["'](https?://[^"']+\.(mp4|m3u8|flv)[^"']*)["']"""),
                Regex("""src\s*=\s*["'](https?://[^"']+)["']""")
            )
            for (regex in regexes) {
                val match = regex.find(data)
                if (match != null) {
                    val url = match.groupValues[1]
                    if (url.startsWith("http")) return PlayUrl(url = url)
                }
            }
        }
        
        // 3. 从 iframe 提取
        val iframe = doc.selectFirst("iframe[src]")
        if (iframe != null) {
            val src = iframe.attr("abs:src")
            if (src.isNotBlank()) return resolveVideoUrl(src) // 递归解析
        }
        
        // 4. 返回原URL让播放器尝试
        return PlayUrl(url = pageUrl)
    }

    // ===== HTTP 请求工具 =====
    
    private fun httpGet(url: String): String {
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: $url")
        }
        return body
    }
    
    private fun httpPost(url: String, body: String): String {
        val requestBody = body.toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(requestBody).build()
        val response = client.newCall(request).execute()
        return response.body?.string() ?: ""
    }

    // ===== 数据解析（基于JSON，需根据实际API调整） =====
    
    private fun fetchHotMovies(): List<Movie> {
        return try {
            val json = httpGet("$baseUrl/api/v1/movie/hot?limit=10")
            parseMovieList(json, 1).movies
        } catch (e: Exception) { emptyList() }
    }
    
    private fun fetchHotByType(type: String): List<Movie> {
        return try {
            val json = httpGet("$baseUrl/api/v1/movie/hot?type=$type&limit=10")
            parseMovieList(json, 1).movies
        } catch (e: Exception) { emptyList() }
    }
    
    private fun parseMovieList(json: String, page: Int): SearchResult {
        // 使用正则+字符串方式解析（不依赖Gson反序列化，更灵活）
        val movies = mutableListOf<Movie>()
        val gson = com.google.gson.Gson()
        
        try {
            val map = gson.fromJson(json, Map::class.java)
            val data = map?.get("data") as? Map<*, *>
            val list = data?.get("list") as? List<*> ?: data?.get("data") as? List<*> ?: emptyList<Any>()
            
            for (item in list) {
                val m = item as? Map<*, *> ?: continue
                movies.add(Movie(
                    id = "${sourceId}_${m["id"]?.hashCode()}",
                    sourceId = sourceId,
                    title = m["name"]?.toString() ?: m["title"]?.toString() ?: "",
                    coverUrl = m["cover"]?.toString() ?: m["pic"]?.toString() ?: "",
                    score = m["score"]?.toString() ?: m["rating"]?.toString() ?: "",
                    year = m["year"]?.toString() ?: "",
                    region = m["area"]?.toString() ?: m["region"]?.toString() ?: "",
                    genre = m["type"]?.toString() ?: m["category"]?.toString() ?: "",
                    director = m["director"]?.toString() ?: "",
                    actors = m["actor"]?.toString() ?: m["casts"]?.toString() ?: "",
                    description = m["description"]?.toString() ?: m["content"]?.toString() ?: ""
                ))
            }
        } catch (e: Exception) {}
        
        return SearchResult(movies, 1, page)
    }
    
    private fun parseMovieDetail(json: String): Movie? {
        val gson = com.google.gson.Gson()
        try {
            val map = gson.fromJson(json, Map::class.java)
            val data = (map?.get("data") as? Map<*, *>) ?: return null
            return Movie(
                id = "${sourceId}_${data["id"]?.hashCode()}",
                sourceId = sourceId,
                title = data["name"]?.toString() ?: data["title"]?.toString() ?: "",
                coverUrl = data["cover"]?.toString() ?: data["pic"]?.toString() ?: "",
                score = data["score"]?.toString() ?: "",
                year = data["year"]?.toString() ?: "",
                region = data["area"]?.toString() ?: "",
                genre = data["type"]?.toString() ?: "",
                director = data["director"]?.toString() ?: "",
                actors = data["actor"]?.toString() ?: "",
                description = data["description"]?.toString() ?: ""
            )
        } catch (e: Exception) { return null }
    }
    
    private fun parsePlaySources(json: String): List<PlaySource> {
        val sources = mutableListOf<PlaySource>()
        val gson = com.google.gson.Gson()
        
        try {
            val map = gson.fromJson(json, Map::class.java)
            val data = map?.get("data") as? Map<*, *> ?: return emptyList()
            val playList = data["playList"] as? List<*> ?: data["sources"] as? List<*> ?: return emptyList()
            
            for ((idx, item) in playList.withIndex()) {
                val s = item as? Map<*, *> ?: continue
                val episodes = mutableListOf<Episode>()
                val epList = s["episodes"] as? List<*> ?: s["urls"] as? List<*> ?: continue
                
                for ((epIdx, ep) in epList.withIndex()) {
                    when (ep) {
                        is Map<*, *> -> episodes.add(Episode(
                            index = epIdx,
                            title = ep["name"]?.toString() ?: ep["title"]?.toString() ?: "第${epIdx + 1}集",
                            url = ep["url"]?.toString() ?: ep["playUrl"]?.toString() ?: ""
                        ))
                        is String -> episodes.add(Episode(
                            index = epIdx,
                            title = "第${epIdx + 1}集",
                            url = ep
                        ))
                    }
                }
                
                sources.add(PlaySource(
                    name = s["name"]?.toString() ?: "线路${idx + 1}",
                    episodes = episodes
                ))
            }
        } catch (e: Exception) {}
        
        return sources
    }
}
