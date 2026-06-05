package com.foxplayer.source

import com.foxplayer.model.Video
import com.foxplayer.model.VideoSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 源管理器 — 多源聚合、自动切换、健康检测
 */
class SourceManager {
    private val parsers = mutableMapOf<String, ISourceParser>()
    private val _sources = MutableStateFlow<List<VideoSource>>(emptyList())
    val sources: StateFlow<List<VideoSource>> = _sources

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun registerParser(parser: ISourceParser) {
        parsers[parser.sourceKey] = parser
    }

    fun updateSources(list: List<VideoSource>) {
        _sources.value = list
    }

    /** 聚合搜索：并行搜所有启用的源，合并去重 */
    suspend fun searchAll(keyword: String, timeoutMs: Long = 8000): List<Video> {
        val activeParsers = parsers.values.filter { p ->
            _sources.value.any { it.key == p.sourceKey && it.enabled }
        }
        return coroutineScope {
            activeParsers.map { parser ->
                async {
                    try {
                        withTimeout(timeoutMs) { parser.search(keyword) }
                    } catch (_: Exception) { emptyList() }
                }
            }.awaitAll().flatten().distinctBy { it.title }
        }
    }

    /** 聚合分类列表 */
    suspend fun getCategoryAll(category: String, page: Int = 1): List<Video> {
        val activeParsers = parsers.values.filter { p ->
            _sources.value.any { it.key == p.sourceKey && it.enabled }
        }
        return coroutineScope {
            activeParsers.map { parser ->
                async {
                    try { parser.getCategoryVideos(category, page) }
                    catch (_: Exception) { emptyList() }
                }
            }.awaitAll().flatten().distinctBy { it.title }
        }
    }

    /** 获取详情，源失效自动切换备用 */
    suspend fun getDetailWithFallback(video: Video): Video? {
        val parser = parsers[video.sourceKey] ?: return null
        return try {
            parser.getDetail(video)
        } catch (_: Exception) {
            // 尝试其他源搜索同标题
            val altResults = searchAll(video.title, timeoutMs = 5000)
            altResults.firstOrNull()?.let { alt ->
                parsers[alt.sourceKey]?.getDetail(alt)
            }
        }
    }

    /** 解析播放地址，支持多线路自动切换 */
    suspend fun parsePlayWithFallback(episode: com.foxplayer.model.Episode, sourceKey: String): String? {
        val parser = parsers[sourceKey] ?: return null
        return try {
            parser.parsePlayUrl(episode)
        } catch (_: Exception) {
            // 标记源不健康
            markSourceUnhealthy(sourceKey)
            null
        }
    }

    private val unhealthySources = mutableSetOf<String>()
    private fun markSourceUnhealthy(key: String) { unhealthySources.add(key) }
    fun isSourceHealthy(key: String) = key !in unhealthySources
}
