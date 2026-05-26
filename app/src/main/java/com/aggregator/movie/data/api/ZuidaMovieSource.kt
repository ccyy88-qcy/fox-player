package com.aggregator.movie.data.api

import com.aggregator.movie.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

/**
 * 最大资源网影视源 (api.zuidapi.com) - v2 简化版
 * 
 * 统一使用 ac=detail 获取完整数据（含封面）
 * 首页直接从列表ID映射到详情数据
 */
class ZuidaMovieSource(
    override val sourceId: String = "zuida_01",
    override val sourceName: String = "最大资源",
    override val baseUrl: String = "https://api.zuidapi.com/api.php/provide/vod/",
    override val priority: Int = 10,
    private val client: okhttp3.OkHttpClient = HttpClientFactory.create()
) : MovieSource {

    override suspend fun isAvailable(): Boolean = true

    override suspend fun getHomeData(): HomeData {
        return try {
            // h=24 只返回最近24小时更新的，速度从6秒降到1秒
            val json = apiCall("ac=list&pg=1&h=24")
            val list = json.optJSONArray("list") ?: return HomeData()
            val ids = mutableListOf<String>()
            for (i in 0 until list.length()) {
                val id = list.getJSONObject(i).optString("vod_id", "")
                if (id.isNotBlank()) ids.add(id)
            }
            // 批量获取详情（含封面）
            val movies = fetchDetailBatch(ids.take(10))
            val bannerIds = ids.take(3)
            val banners = if (bannerIds.isNotEmpty()) fetchDetailBatch(bannerIds) else emptyList()

            HomeData(
                banners = banners,
                hotMovies = movies.take(10),
                hotTv = movies.filter { it.genre.contains("剧") }.take(6),
                hotAnime = movies.filter { it.genre.contains("动漫") || it.genre.contains("动画") }.take(6),
                latestMovies = movies.take(10)
            )
        } catch (e: Exception) { 
            e.printStackTrace()
            HomeData() 
        }
    }

    private suspend fun fetchDetailBatch(ids: List<String>): List<Movie> {
        if (ids.isEmpty()) return emptyList()
        return try {
            val idStr = ids.joinToString(",")
            val json = apiCall("ac=detail&ids=$idStr")
            parseDetailList(json)
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun getCategories(): List<Category> {
        return try {
            val classes = fetchClassList()
            // 排除无数据的父分类(1-3)，保留4(动漫)及所有子分类
            val excludeParentIds = setOf(1, 2, 3)
            val filtered = classes.filter { (id, _) -> id !in excludeParentIds }
            val all = Category("all", "全部", MovieType.MOVIE)
            val mapped = filtered.map { (id, name) ->
                val type = when {
                    name.contains("剧") -> MovieType.TV
                    name.contains("动漫") || name.contains("动画") -> MovieType.ANIME
                    name.contains("综艺") -> MovieType.VARIETY
                    else -> MovieType.MOVIE
                }
                Category(id.toString(), name, type)
            }
            listOf(all) + mapped
        } catch (e: Exception) {
            listOf(
                Category("all", "全部", MovieType.MOVIE),
            )
        }
    }

    private suspend fun fetchClassList(): List<Pair<Int, String>> {
        val json = apiCall("ac=list&pg=1")
        val arr = json.optJSONArray("class") ?: return emptyList()
        val result = mutableListOf<Pair<Int, String>>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val id = obj.optInt("type_id", 0)
            val name = obj.optString("type_name", "")
            if (id > 0 && name.isNotBlank()) result.add(id to name)
        }
        return result
    }

    override suspend fun getMoviesByCategory(categoryId: String, page: Int): SearchResult {
        return try {
            val typeParam = if (categoryId == "all") "" else "&t=$categoryId"
            val json = apiCall("ac=list$typeParam&pg=$page&h=24")
            val list = json.optJSONArray("list") ?: return SearchResult(emptyList(), 1, page)
            val ids = mutableListOf<String>()
            for (i in 0 until list.length()) {
                val id = list.getJSONObject(i).optString("vod_id", "")
                if (id.isNotBlank()) ids.add(id)
            }
            val movies = fetchDetailBatch(ids)
            SearchResult(movies, json.optInt("pagecount", 1), page)
        } catch (e: Exception) {
            SearchResult(emptyList(), 1, page)
        }
    }

    override suspend fun search(keyword: String, page: Int): SearchResult {
        return try {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val json = apiCall("ac=detail&wd=$encoded&pg=$page")
            SearchResult(parseDetailList(json), json.optInt("pagecount", 1), page)
        } catch (e: Exception) {
            SearchResult(emptyList(), 1, page)
        }
    }

    override suspend fun getMovieDetail(movieId: String): Movie? {
        return try {
            val rawId = movieId.removePrefix("${sourceId}_")
            val json = apiCall("ac=detail&ids=$rawId")
            parseDetailList(json).firstOrNull()
        } catch (e: Exception) { null }
    }

    override suspend fun getPlaySources(movieId: String): List<PlaySource> {
        return try {
            val rawId = movieId.removePrefix("${sourceId}_")
            val json = apiCall("ac=detail&ids=$rawId")
            val list = json.optJSONArray("list") ?: return emptyList()
            if (list.length() == 0) return emptyList()
            parsePlaySources(list.getJSONObject(0))
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun resolvePlayUrl(playUrl: String): PlayUrl {
        val format = when {
            playUrl.endsWith(".m3u8") -> VideoFormat.M3U8
            playUrl.endsWith(".mp4") -> VideoFormat.MP4
            playUrl.endsWith(".flv") -> VideoFormat.FLV
            else -> VideoFormat.M3U8
        }
        return PlayUrl(url = playUrl, headers = mapOf("Referer" to "https://www.zuidapi.com/"), format = format)
    }

    // ===== HTTP =====
    private suspend fun apiCall(params: String): JSONObject = withContext(Dispatchers.IO) {
        val url = "$baseUrl?$params"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .addHeader("Accept", "application/json,*/*")
            .get()
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: "{}"
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
        JSONObject(body)
    }

    // ===== 解析 =====
    private fun parseDetailList(json: JSONObject): List<Movie> {
        val list = json.optJSONArray("list") ?: return emptyList()
        val movies = mutableListOf<Movie>()
        for (i in 0 until list.length()) {
            val item = list.getJSONObject(i)
            movies.add(Movie(
                id = "${sourceId}_${item.optString("vod_id", "0")}",
                sourceId = sourceId,
                title = item.optString("vod_name", ""),
                type = inferType(item.optString("type_name", "")),
                coverUrl = item.optString("vod_pic", ""),
                score = item.optString("vod_score", ""),
                year = item.optString("vod_year", ""),
                region = item.optString("vod_area", ""),
                genre = item.optString("type_name", ""),
                director = item.optString("vod_director", ""),
                actors = item.optString("vod_actor", ""),
                description = item.optString("vod_content", "").replace(Regex("<[^>]*>"), "").trim()
            ))
        }
        return movies
    }

    private fun parsePlaySources(item: JSONObject): List<PlaySource> {
        val sources = mutableListOf<PlaySource>()
        val playFrom = item.optString("vod_play_from", "")
        val playUrlStr = item.optString("vod_play_url", "")
        if (playFrom.isBlank() || playUrlStr.isBlank()) return emptyList()

        playFrom.split("\$\$\$").zip(playUrlStr.split("\$\$\$")) { name, urls ->
            val episodes = urls.split("#").mapIndexed { idx, ep ->
                val parts = ep.split("\$", limit = 2)
                Episode(idx, parts.getOrElse(0) { "第${idx + 1}集" }, parts.getOrElse(1) { "" })
            }.filter { it.url.isNotBlank() }
            if (episodes.isNotEmpty()) sources.add(PlaySource(name.ifBlank { "线路" }, episodes))
        }
        return sources
    }

    private fun inferType(typeName: String): MovieType = when {
        typeName.contains("动漫") || typeName.contains("动画") -> MovieType.ANIME
        typeName.contains("综艺") -> MovieType.VARIETY
        typeName.contains("剧") -> MovieType.TV
        else -> MovieType.MOVIE
    }
}
