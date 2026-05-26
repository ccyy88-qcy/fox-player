package com.aggregator.movie.data.api

import com.aggregator.movie.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 灵虎影视数据源 (app7.555618.xyz)
 * 基于抓包逆向分析 - 2026-05-26
 * API格式: {host}/api.php/getappapi.index/{endpoint}
 */
class LinghuMovieSource(
    override val sourceId: String = "linghu_03",
    override val sourceName: String = "灵虎影视",
    override val baseUrl: String = "https://app7.555618.xyz/api.php/getappapi.index",
    override val priority: Int = 8,
    private val client: OkHttpClient = HttpClientFactory.create()
) : MovieSource {

    override suspend fun isAvailable(): Boolean = true

    // ===== API 通用请求 =====

    private suspend fun apiGet(path: String, params: Map<String, String> = emptyMap()): JSONObject =
        withContext(Dispatchers.IO) {
            val url = buildString {
                append(baseUrl).append("/").append(path)
                if (params.isNotEmpty()) {
                    append("?")
                    append(params.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" })
                }
            }
        val request = Request.Builder().url(url)
            .header("User-Agent", "okhttp/4.9.3")
            .header("Accept", "application/json")
            .build()
            val resp = client.newCall(request).execute()
            JSONObject(resp.body?.string() ?: "{}")
        }

    private suspend fun apiPost(path: String, params: Map<String, String> = emptyMap()): JSONObject =
        withContext(Dispatchers.IO) {
            val formBody = FormBody.Builder().apply {
                params.forEach { (k, v) -> add(k, URLEncoder.encode(v, "UTF-8")) }
            }.build()
            val request = Request.Builder().url("$baseUrl/$path")
                .post(formBody)
                .header("User-Agent", "okhttp/4.9.3")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .build()
            val resp = client.newCall(request).execute()
            JSONObject(resp.body?.string() ?: "{}")
        }

    // ===== 数据解析工具 =====

    private fun parseMovieList(json: JSONObject): List<Movie> {
        // 灵虎返回格式: { code:1, data: { list:[...] } } 或 { code:1, data: [...] }
        if (json.optInt("code", 0) != 1) return emptyList()
        val data = json.optJSONObject("data") ?: return emptyList()
        val arr = data.optJSONArray("list")
            ?: data.optJSONArray("vod_list")
            ?: data.optJSONArray("slide")
            ?: return emptyList()

        val movies = mutableListOf<Movie>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            movies.add(Movie(
                id = "${sourceId}_${item.optString("vod_id", "0")}",
                sourceId = sourceId,
                title = item.optString("vod_name", ""),
                coverUrl = item.optString("vod_pic", ""),
                score = item.optString("vod_score", "").ifBlank { item.optString("vod_douban_score", "") },
                year = item.optString("vod_year", ""),
                region = item.optString("vod_area", ""),
                genre = item.optString("type_name", ""),
                director = item.optString("vod_director", ""),
                actors = item.optString("vod_actor", ""),
                description = item.optString("vod_content", "")
                    .replace(Regex("<[^>]*>"), "").trim()
            ))
        }
        return movies
    }

    private fun parseMovieDetail(json: JSONObject, vodId: String): Movie? {
        if (json.optInt("code", 0) != 1) return null
        val data = json.optJSONObject("data") ?: return null
        return Movie(
            id = "${sourceId}_${vodId}",
            sourceId = sourceId,
            title = data.optString("vod_name", ""),
            coverUrl = data.optString("vod_pic", ""),
            score = data.optString("vod_score", ""),
            year = data.optString("vod_year", ""),
            region = data.optString("vod_area", ""),
            genre = data.optString("type_name", ""),
            director = data.optString("vod_director", ""),
            actors = data.optString("vod_actor", ""),
            description = data.optString("vod_content", "")
                .replace(Regex("<[^>]*>"), "").trim()
        )
    }

    private fun parsePlaySources(json: JSONObject): List<PlaySource> {
        // 播放信息在详情接口的 vod_play_from / vod_play_url 中
        if (json.optInt("code", 0) != 1) return emptyList()
        val data = json.optJSONObject("data") ?: return emptyList()
        val playFrom = data.optString("vod_play_from", "")
        val playUrlStr = data.optString("vod_play_url", "")
        if (playFrom.isBlank() || playUrlStr.isBlank()) return emptyList()

        val sources = mutableListOf<PlaySource>()
        // 分隔符可能是 $$$ 或 #
        val fromParts = playFrom.split("\\$\\$\\$".toRegex()).filter { it.isNotBlank() }
        val urlParts = playUrlStr.split("\\$\\$\\$".toRegex()).filter { it.isNotBlank() }

        for (i in 0 until minOf(fromParts.size, urlParts.size)) {
            val name = fromParts[i]
            val episodes = urlParts[i].split("#").mapIndexed { idx, ep ->
                // 格式: "第1集$url" 或 "第1集"（URL为空时需要单独解析）
                val parts = ep.split("$", limit = 2)
                Episode(
                    index = idx,
                    title = parts.getOrElse(0) { "第${idx + 1}集" },
                    url = parts.getOrElse(1) { "" }
                )
            }.filter { it.title.isNotBlank() }

            if (episodes.isNotEmpty()) {
                sources.add(PlaySource(name.ifBlank { "线路${i + 1}" }, episodes))
            }
        }
        return sources
    }

    // ===== MovieSource 接口实现 =====

    override suspend fun getHomeData(): HomeData {
        return try {
            // 获取首页数据
            val initJson = apiGet("init")
            val homeVodJson = apiGet("home_vod")
            val typeJson = apiGet("type_list")

            val hotMovies = parseMovieList(homeVodJson)
            val banners = parseMovieList(initJson).take(5)

            // 按type_name分类
            val movies = hotMovies
            val tvList = movies.filter { it.genre.contains("剧") }
            val animeList = movies.filter { it.genre.contains("动漫") || it.genre.contains("动画") }

            HomeData(
                banners = banners.ifEmpty { movies.take(5) },
                hotMovies = movies,
                hotTv = tvList,
                hotAnime = animeList,
                latestMovies = movies.takeLast(10)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            HomeData()
        }
    }

    override suspend fun getCategories(): List<Category> {
        return try {
            val json = apiGet("type_list")
            if (json.optInt("code", 0) != 1) return defaultCategories()

            val data = json.optJSONObject("data")
            val arr = data?.optJSONArray("list") ?: data?.optJSONArray("types")

            if (arr != null && arr.length() > 0) {
                val categories = mutableListOf(Category("all", "全部", MovieType.MOVIE))
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val id = item.optString("type_id", "0")
                    val name = item.optString("type_name", "")
                    if (id.isNotBlank() && name.isNotBlank()) {
                        val type = when {
                            name.contains("剧") -> MovieType.TV
                            name.contains("动漫") || name.contains("动画") -> MovieType.ANIME
                            name.contains("综艺") -> MovieType.VARIETY
                            else -> MovieType.MOVIE
                        }
                        categories.add(Category(id, name, type))
                    }
                }
                if (categories.size > 1) return categories
            }
            defaultCategories()
        } catch (e: Exception) {
            defaultCategories()
        }
    }

    private fun defaultCategories(): List<Category> = listOf(
        Category("all", "全部", MovieType.MOVIE),
        Category("1", "电影", MovieType.MOVIE),
        Category("2", "连续剧", MovieType.TV),
        Category("3", "综艺", MovieType.VARIETY),
        Category("4", "动漫", MovieType.ANIME),
    )

    override suspend fun getMoviesByCategory(categoryId: String, page: Int): SearchResult {
        return try {
            val typeId = if (categoryId == "all") "" else categoryId
            val json = apiPost("vodList", mapOf(
                "type_id" to typeId,
                "page" to page.toString(),
                "limit" to "20",
                "order" to "time"
            ))
            val movies = parseMovieList(json)
            val totalPage = json.optJSONObject("data")?.optInt("pagecount", 1) ?: 1
            SearchResult(movies, totalPage, page)
        } catch (e: Exception) {
            SearchResult(emptyList(), 1, page)
        }
    }

    override suspend fun search(keyword: String, page: Int): SearchResult {
        return try {
            val json = apiPost("vodSearch", mapOf(
                "wd" to keyword,
                "page" to page.toString()
            ))
            SearchResult(parseMovieList(json), 1, page)
        } catch (e: Exception) {
            SearchResult(emptyList(), 1, page)
        }
    }

    override suspend fun getMovieDetail(movieId: String): Movie? {
        return try {
            val rawId = movieId.removePrefix("${sourceId}_")
            val json = apiPost("vodDetail", mapOf("vod_id" to rawId))
            parseMovieDetail(json, rawId)
        } catch (e: Exception) { null }
    }

    override suspend fun getPlaySources(movieId: String): List<PlaySource> {
        return try {
            val rawId = movieId.removePrefix("${sourceId}_")
            val json = apiPost("vodDetail", mapOf("vod_id" to rawId))
            parsePlaySources(json)
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun resolvePlayUrl(playUrl: String): PlayUrl {
        // 灵虎的播放URL需要调用 vodParse 解析
        // playUrl 格式: "vod_id:xxx|from:ffzy" 或直接的 m3u8/mp4 链接
        return when {
            playUrl.startsWith("http") -> {
                // 已经是直链
                val format = when {
                    playUrl.contains(".m3u8") -> VideoFormat.M3U8
                    playUrl.contains(".mp4") -> VideoFormat.MP4
                    playUrl.contains(".flv") -> VideoFormat.FLV
                    else -> VideoFormat.M3U8
                }
                PlayUrl(url = playUrl, format = format)
            }
            playUrl.contains("vod_id:") -> {
                // 需要调用 vodParse 解析
                // 格式: "vod_id:1066|from:ffzy"
                val params = playUrl.split("|").associate {
                    val (k, v) = it.split(":", limit = 2)
                    k to v
                }
                val vodId = params["vod_id"] ?: ""
                val from = params["from"] ?: "ffzy"
                try {
                    val json = apiPost("vodParse", mapOf(
                        "vod_id" to vodId,
                        "from" to from
                    ))
                    val url = json.optJSONObject("data")?.optString("url", "")
                        ?: json.optString("url", "")
                    PlayUrl(url = url, format = VideoFormat.M3U8)
                } catch (e: Exception) {
                    PlayUrl(url = "")
                }
            }
            else -> PlayUrl(url = playUrl)
        }
    }
}
