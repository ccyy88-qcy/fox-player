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
 */
class JsonSourceParser(
    override val sourceKey: String,
    override val sourceName: String,
    private val apiUrl: String,
    private val playParseUrl: String = "",
) : ISourceParser {

    private val gson = Gson()

    private suspend fun fetchJson(url: String): JsonObject? = withContext(Dispatchers.IO) {
        try {
            val body = com.foxplayer.util.HttpClientManager.get(url)
            gson.fromJson(body, JsonObject::class.java)
        } catch (e: Exception) {
            android.util.Log.e("JsonSourceParser", "fetchJson error: $url", e)
            null
        }
    }

    override suspend fun getCategoryVideos(category: String, page: Int): List<Video> {
        // 不传分类参数，拉全部数据，客户端按type_name筛选
        val url = "$apiUrl?ac=videolist&pg=$page"
        val json = fetchJson(url) ?: return emptyList()
        val all = parseVideoList(json)
        // 按分类名筛选
        if (category == "MOVIE") return all.filter { it.type.contains("电影") || it.type.contains("片") }
        if (category == "TV") return all.filter { it.type.contains("剧") || it.type.contains("电视") }
        if (category == "ANIME") return all.filter { it.type.contains("动漫") || it.type.contains("动画") || it.type.contains("番") }
        if (category == "VARIETY") return all.filter { it.type.contains("综艺") || it.type.contains("秀") }
        return all
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
                cover = (obj.get("vod_pic")?.asString ?: "")
                    .replace("\\/", "/").replace("\\u0026", "&"),
                desc = obj.get("vod_content")?.asString?.take(200) ?: "",
                year = obj.get("vod_year")?.asString ?: "",
                area = obj.get("vod_area")?.asString ?: "",
                type = obj.get("type_name")?.asString ?: "",
                rating = obj.get("vod_score")?.asString?.toFloatOrNull() ?: 0f,
                sourceKey = sourceKey,
                remark = obj.get("vod_remarks")?.asString?.take(20) ?: "",
            )
        }
    }

    private fun parseVideoDetail(detail: JsonObject, base: Video): Video {
        val vodPlayUrl = detail.get("vod_play_url")?.asString ?: ""
        val vodPlayFrom = detail.get("vod_play_from")?.asString ?: ""
        val lines = vodPlayUrl.split("$$$")
        val lineNames = vodPlayFrom.split("$$$")
        val episodes = mutableListOf<Episode>()
        lines.forEachIndexed { idx, line ->
            val lineName = lineNames.getOrElse(idx) { "线路${idx + 1}" }
            line.split("#").forEach { epStr ->
                val parts = epStr.split("$")
                if (parts.size >= 2) {
                    val rawUrl = parts[1].replace("\\/", "/").replace("\\u0026", "&").trim()
                    val cleanUrl = if (rawUrl.startsWith("//")) "https:$rawUrl" else rawUrl
                    if (cleanUrl.isNotBlank() && cleanUrl.startsWith("http")) {
                        episodes.add(Episode(name = "[${lineName}] ${parts[0].trim()}", url = cleanUrl))
                    }
                }
            }
        }
        return base.copy(
            desc = detail.get("vod_content")?.asString ?: base.desc,
            episodes = episodes,
        )
    }
}
