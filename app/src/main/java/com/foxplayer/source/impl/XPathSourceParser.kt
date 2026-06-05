package com.foxplayer.source.impl

import com.foxplayer.model.Episode
import com.foxplayer.model.Video
import com.foxplayer.source.ISourceParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * XPath/Jsoup 网页源解析器 — 适用于非标准API的影视站
 * 通过 CSS 选择器提取视频信息
 */
class XPathSourceParser(
    override val sourceKey: String,
    override val sourceName: String,
    private val baseUrl: String,
    private val selectors: SelectorConfig,
) : ISourceParser {

    data class SelectorConfig(
        val listUrl: String = "",          // 列表页模板 {cateId}/{page}
        val searchUrl: String = "",        // 搜索页模板 {wd}
        val detailUrl: String = "",        // 详情页模板 {id}
        val videoItem: String = "",        // 列表项选择器
        val title: String = "",            // 标题
        val cover: String = "",            // 封面
        val link: String = "",             // 链接
        val episodeItem: String = "",      // 剧集项
        val episodeTitle: String = "",     // 剧集标题
        val episodeUrl: String = "",       // 剧集URL
    )

    private suspend fun fetchDoc(url: String): Document? = withContext(Dispatchers.IO) {
        try {
            Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .timeout(15000).get()
        } catch (_: Exception) { null }
    }

    override suspend fun getCategoryVideos(category: String, page: Int): List<Video> {
        val cateMap = mapOf("MOVIE" to "1", "TV" to "2", "ANIME" to "3", "VARIETY" to "4")
        val cateId = cateMap[category] ?: "1"
        val url = "$baseUrl/${selectors.listUrl}"
            .replace("{cateId}", cateId).replace("{page}", page.toString())
        val doc = fetchDoc(url) ?: return emptyList()
        return parseVideoList(doc)
    }

    override suspend fun search(keyword: String, page: Int): List<Video> {
        val url = "$baseUrl/${selectors.searchUrl}".replace("{wd}", keyword)
        val doc = fetchDoc(url) ?: return emptyList()
        return parseVideoList(doc)
    }

    override suspend fun getDetail(video: Video): Video {
        val url = "$baseUrl/${selectors.detailUrl}".replace("{id}", video.id)
        val doc = fetchDoc(url) ?: return video
        val episodes = doc.select(selectors.episodeItem).map { el ->
            Episode(
                name = el.select(selectors.episodeTitle).text(),
                url = el.select(selectors.episodeUrl).attr("href").let {
                    if (it.startsWith("http")) it else "$baseUrl$it"
                }
            )
        }
        return video.copy(episodes = episodes)
    }

    override suspend fun parsePlayUrl(episode: Episode): String = episode.url

    override suspend fun getLatest(page: Int): List<Video> {
        val url = "$baseUrl/${selectors.listUrl}"
            .replace("{cateId}", "0").replace("{page}", page.toString())
        val doc = fetchDoc(url) ?: return emptyList()
        return parseVideoList(doc)
    }

    private fun parseVideoList(doc: Document): List<Video> {
        return doc.select(selectors.videoItem).map { el ->
            Video(
                id = el.select(selectors.link).attr("href").removePrefix("/"),
                title = el.select(selectors.title).text(),
                cover = el.select(selectors.cover).attr("data-src")
                    .ifEmpty { el.select(selectors.cover).attr("src") },
                sourceKey = sourceKey,
            )
        }
    }
}
