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
    val parsers = mutableMapOf<String, ISourceParser>()
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
                    } catch (e: Exception) {
                        android.util.Log.e("SourceManager", "search error: ${parser.sourceKey}", e)
                        emptyList()
                    }
                }
            }.awaitAll().flatten().distinctBy { it.id }
        }
    }

    /** 聚合分类列表 */
    suspend fun getCategoryAll(category: String, page: Int = 1): List<Video> {
        val activeParsers = parsers.values.filter { p ->
            _sources.value.any { it.key == p.sourceKey && it.enabled }
        }
        android.util.Log.d("SourceManager", "getCategoryAll: cat=$page, activeParsers=${activeParsers.size}")
        val result = coroutineScope {
            activeParsers.map { parser ->
                async {
                    try {
                        val list = parser.getCategoryVideos(category, page)
                        android.util.Log.d("SourceManager", "parser ${parser.sourceKey} returned ${list.size}")
                        list
                    } catch (e: Exception) {
                        android.util.Log.e("SourceManager", "getCategory error: ${parser.sourceKey}", e)
                        emptyList()
                    }
                }
            }.awaitAll().flatten().distinctBy { it.id }
        }
        android.util.Log.d("SourceManager", "getCategoryAll result: ${result.size}")
        return result
    }

    /** 获取详情，源失效自动切换备用 */
    suspend fun getDetailWithFallback(video: Video): Video? {
        val parser = parsers[video.sourceKey] ?: return null
        return try {
            parser.getDetail(video)
        } catch (_: Exception) {
            val altResults = searchAll(video.title, timeoutMs = 5000)
            altResults.firstOrNull()?.let { alt ->
                parsers[alt.sourceKey]?.getDetail(alt)
            }
        }
    }

    /** 解析播放地址 */
    suspend fun parsePlayUrl(episode: com.foxplayer.model.Episode, sourceKey: String): String? {
        val parser = parsers[sourceKey] ?: return null
        return try {
            parser.parsePlayUrl(episode)
        } catch (_: Exception) {
            markSourceUnhealthy(sourceKey)
            null
        }
    }

    private val unhealthySources = mutableSetOf<String>()
    private fun markSourceUnhealthy(key: String) { unhealthySources.add(key) }
    fun isSourceHealthy(key: String) = key !in unhealthySources
}
