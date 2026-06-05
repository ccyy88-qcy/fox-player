package com.foxplayer.source.impl

import com.foxplayer.model.Episode
import com.foxplayer.model.Video
import com.foxplayer.model.VideoSource
import com.foxplayer.source.ISourceParser
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/**
 * JSON API 源解析器 — 兼容苹果CMS V10 / 影视CMS 等标准JSON接口
 * API格式: /api.php/provide/vod/?ac=videolist&pg=1&t=1&wd=关键词
 */
class JsonSourceParser(
    override val sourceKey: String,
    override val sourceName: String,
    private val apiUrl: String,
    private val playParseUrl: String = "",
) : ISourceParser {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    private suspend fun fetchJson(url: String): JsonObject? = withContext(Dispatchers.IO) {
        try {
            val resp = client.newCall(Request.Builder().url(url).build()).execute()
            gson.fromJson(resp.body?.string(), JsonObject::class.java)
        } catch (_: Exception) { null }
    }

    override suspend fun getCategoryVideos(category: String, page: Int): List<Video> {
        val typeMap = mapOf(
            "MOVIE" to 1, "TV" to 2, "ANIME" to 3, "VARIETY" to 4
        )
        val typeId = typeMap[category] ?: 1
        val json = fetchJson("$apiUrl?ac=videolist&pg=$page&t=$typeId") ?: return emptyList()
        return parseVideoList(json)
    }

    override suspend fun search(keyword: String, page: Int): List<Video> {
        val wd = URLEncoder.encode(keyword, "UTF-8")
        val json = fetchJson("$apiUrl?ac=videolist&wd=$wd&pg=$page") ?: return emptyList()
        return parseVideoList(json)
    }

    override suspend fun getDetail(video: Video): Video {
        val json = fetchJson("$apiUrl?ac=detail&ids=${video.id}") ?: return video
        val list = json.getAsJsonArray("list") ?: return video
        if (list.size() == 0) return video
        val detail = list[0].asJsonObject
        return parseVideoDetail(detail, video)
    }

    override suspend fun parsePlayUrl(episode: Episode): String {
        if (playParseUrl.isBlank()) return episode.url
        val json = fetchJson("$playParseUrl?url=${URLEncoder.encode(episode.url, "UTF-8")}")
        return json?.get("url")?.asString ?: episode.url
    }

    override suspend fun getLatest(page: Int): List<Video> {
        val json = fetchJson("$apiUrl?ac=videolist&pg=$page") ?: return emptyList()
        return parseVideoList(json)
    }

    private fun parseVideoList(json: JsonObject): List<Video> {
        val list = json.getAsJsonArray("list") ?: return emptyList()
        return list.map { elem ->
            val obj = elem.asJsonObject
            Video(
                id = obj.get("vod_id")?.asString ?: "",
                title = obj.get("vod_name")?.asString ?: "",
                cover = obj.get("vod_pic")?.asString ?: "",
                desc = obj.get("vod_content")?.asString?.take(100) ?: "",
                year = obj.get("vod_year")?.asString ?: "",
                area = obj.get("vod_area")?.asString ?: "",
                type = obj.get("type_name")?.asString ?: "",
                rating = obj.get("vod_score")?.asString?.toFloatOrNull() ?: 0f,
                sourceKey = sourceKey,
            )
        }
    }

    private fun parseVideoDetail(detail: JsonObject, base: Video): Video {
        val vodPlayUrl = detail.get("vod_play_url")?.asString ?: ""
        val vodPlayFrom = detail.get("vod_play_from")?.asString ?: ""

        // 解析多线路剧集: "第01集$url1#第02集$url2$$$线路2$第01集$url3#..."
        val lines = vodPlayUrl.split("$$$")
        val lineNames = vodPlayFrom.split("$$$")
        val episodes = mutableListOf<Episode>()

        lines.forEachIndexed { idx, line ->
            val lineName = lineNames.getOrElse(idx) { "线路${idx + 1}" }
            line.split("#").forEach { epStr ->
                val parts = epStr.split("$")
                if (parts.size == 2) {
                    episodes.add(Episode(name = "[${lineName}] ${parts[0]}", url = parts[1]))
                }
            }
        }

        return base.copy(
            desc = detail.get("vod_content")?.asString ?: base.desc,
            episodes = episodes,
        )
    }
}
