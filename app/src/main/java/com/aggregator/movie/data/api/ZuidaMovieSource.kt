package com.aggregator.movie.data.api

import com.aggregator.movie.data.model.*
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

/**
 * 最大资源网影视源 (api.zuidapi.com)
 * 
 * 验证通过: 2026-05-26
 * 首页: ac=list&pg=1         → 热门列表 (total=118360, pagecount=5918)
 * 分类: ac=list&t=6&pg=1     → 按type_id筛选
 * 搜索: ac=detail&wd=keyword  → 搜索结果
 * 详情: ac=detail&ids=id      → 完整详情(含播放地址)
 * 
 * 播放地址格式:
 *   vod_play_from: "zuidam3u8" (线路标识)
 *   vod_play_url: "集名$m3u8url#集名$m3u8url#..."
 * 
 * 分类type_id映射:
 *   6:欧美剧, 25:大陆综艺, 以及其他
 *   实际使用时不硬编码，从首页数据动态获取
 */
class ZuidaMovieSource(
    override val sourceId: String = "zuida_01",
    override val sourceName: String = "最大资源",
    override val baseUrl: String = "https://api.zuidapi.com/api.php/provide/vod/",
    override val priority: Int = 10,
    private val client: okhttp3.OkHttpClient = HttpClientFactory.create()
) : MovieSource {

    override suspend fun isAvailable(): Boolean {
        return try {
            val json = apiCall("ac=list&pg=1")
            json.optInt("code") == 1
        } catch (e: Exception) { false }
    }

    override suspend fun getHomeData(): HomeData {
        val allMovies = mutableListOf<Movie>()
        val bannerList = mutableListOf<Movie>()
        val tvList = mutableListOf<Movie>()
        val animeList = mutableListOf<Movie>()
        val varietyList = mutableListOf<Movie>()

        // 抓取首页多页数据
        for (page in 1..3) {
            val json = apiCall("ac=list&pg=$page")
            val list = parseList(json)
            allMovies.addAll(list)
        }

        // 按类型分流
        for (movie in allMovies) {
            bannerList.add(movie)
            val genre = movie.genre
            when {
                genre.contains("剧") || genre.contains("TV") -> tvList.add(movie)
                genre.contains("动漫") || genre.contains("动画") -> animeList.add(movie)
                genre.contains("综艺") -> varietyList.add(movie)
            }
        }

        return HomeData(
            banners = bannerList.take(5),
            hotMovies = allMovies.take(10),
            hotTv = tvList.take(10),
            hotAnime = animeList.take(10),
            latestMovies = allMovies.takeLast(10)
        )
    }

    override suspend fun getCategories(): List<Category> {
        // 从首页数据提取分类（该API不支持独立分类列表接口）
        return listOf(
            Category("all", "全部", MovieType.MOVIE),
            Category("movie", "电影", MovieType.MOVIE),
            Category("tv", "电视剧", MovieType.TV),
            Category("anime", "动漫", MovieType.ANIME),
            Category("variety", "综艺", MovieType.VARIETY),
        )
    }

    override suspend fun getMoviesByCategory(categoryId: String, page: Int): SearchResult {
        val typeFilter = when (categoryId) {
            "movie" -> "&t=1"
            "tv" -> "&t=2"
            "anime" -> "&t=3"
            "variety" -> "&t=4"
            else -> ""
        }
        val json = apiCall("ac=list$typeFilter&pg=$page")
        val movies = parseList(json)
        val totalPage = json.optInt("pagecount", 1)
        return SearchResult(movies, totalPage, page)
    }

    override suspend fun search(keyword: String, page: Int): SearchResult {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val json = apiCall("ac=detail&wd=$encoded&pg=$page")
        val movies = parseDetailList(json)
        val totalPage = json.optInt("pagecount", 1)
        return SearchResult(movies, totalPage, page)
    }

    override suspend fun getMovieDetail(movieId: String): Movie? {
        return try {
            val json = apiCall("ac=detail&ids=$movieId")
            val list = parseDetailList(json)
            list.firstOrNull()
        } catch (e: Exception) { null }
    }

    override suspend fun getPlaySources(movieId: String): List<PlaySource> {
        val json = apiCall("ac=detail&ids=$movieId")
        val list = json.optJSONArray("list") ?: return emptyList()
        if (list.length() == 0) return emptyList()
        
        val item = list.getJSONObject(0)
        return parsePlaySources(item)
    }

    override suspend fun resolvePlayUrl(playUrl: String): PlayUrl {
        // 该API直接返回m3u8直链
        val format = when {
            playUrl.endsWith(".m3u8") -> VideoFormat.M3U8
            playUrl.endsWith(".mp4") -> VideoFormat.MP4
            playUrl.endsWith(".flv") -> VideoFormat.FLV
            else -> VideoFormat.M3U8 // 默认m3u8
        }
        return PlayUrl(
            url = playUrl,
            headers = mapOf(
                "Referer" to "https://www.zuidapi.com/",
                "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"
            ),
            format = format
        )
    }

    // ===== HTTP =====

    private fun apiCall(params: String): JSONObject {
        val url = "$baseUrl?$params"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: "{}"
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
        return JSONObject(body)
    }

    // ===== 数据解析 =====

    /** 解析列表数据 (ac=list) */
    private fun parseList(json: JSONObject): List<Movie> {
        val list = json.optJSONArray("list") ?: return emptyList()
        val movies = mutableListOf<Movie>()
        for (i in 0 until list.length()) {
            val item = list.getJSONObject(i)
            movies.add(Movie(
                id = "${sourceId}_${item.optInt("vod_id", 0)}",
                sourceId = sourceId,
                title = item.optString("vod_name", ""),
                type = inferType(item.optString("type_name", "")),
                coverUrl = item.optString("vod_pic", ""),
                year = item.optString("vod_year", ""),
                genre = item.optString("type_name", ""),
                score = ""
            ))
        }
        return movies
    }

    /** 解析详情数据 (ac=detail) */
    private fun parseDetailList(json: JSONObject): List<Movie> {
        val list = json.optJSONArray("list") ?: return emptyList()
        val movies = mutableListOf<Movie>()
        for (i in 0 until list.length()) {
            val item = list.getJSONObject(i)
            movies.add(Movie(
                id = "${sourceId}_${item.optInt("vod_id", 0)}",
                sourceId = sourceId,
                title = item.optString("vod_name", ""),
                type = inferType(item.optString("type_name", "")),
                coverUrl = item.optString("vod_pic", ""),
                year = item.optString("vod_year", ""),
                region = item.optString("vod_area", ""),
                genre = item.optString("type_name", ""),
                director = item.optString("vod_director", ""),
                actors = item.optString("vod_actor", ""),
                description = item.optString("vod_content", "")
                    .replace(Regex("<[^>]*>"), "") // 去HTML标签
                    .trim(),
                score = item.optString("vod_score", "")
            ))
        }
        return movies
    }

    /** 解析播放源和选集 */
    private fun parsePlaySources(item: JSONObject): List<PlaySource> {
        val sources = mutableListOf<PlaySource>()
        
        // vod_play_from: "zuidam3u8$$$线路2$$$线路3"
        // vod_play_url: "集名$url#集名$url$$$集名$url#..."
        val playFrom = item.optString("vod_play_from", "")
        val playUrl = item.optString("vod_play_url", "")
        
        if (playFrom.isBlank() || playUrl.isBlank()) return emptyList()
        
        val fromList = playFrom.split("\$\$\$")
        val urlList = playUrl.split("\$\$\$")
        
        for (i in fromList.indices) {
            val sourceName = fromList[i].ifBlank { "线路${i + 1}" }
            val sourceUrls = urlList.getOrNull(i) ?: ""
            
            // "第1集$m3u8url#第2集$m3u8url#..."
            val episodes = sourceUrls.split("#").mapIndexed { idx, epStr ->
                val parts = epStr.split("\$", limit = 2)
                Episode(
                    index = idx,
                    title = parts.getOrElse(0) { "第${idx + 1}集" },
                    url = parts.getOrElse(1) { "" }
                )
            }.filter { it.url.isNotBlank() }
            
            if (episodes.isNotEmpty()) {
                sources.add(PlaySource(name = sourceName, episodes = episodes))
            }
        }
        
        return sources
    }

    private fun inferType(typeName: String): MovieType {
        return when {
            typeName.contains("动漫") || typeName.contains("动画") -> MovieType.ANIME
            typeName.contains("综艺") -> MovieType.VARIETY
            typeName.contains("剧") -> MovieType.TV
            else -> MovieType.MOVIE
        }
    }
}
