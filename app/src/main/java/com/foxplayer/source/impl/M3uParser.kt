package com.foxplayer.source.impl

import com.foxplayer.model.LiveChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI

/**
 * M3U/M3U8/TXT 直播源解析器
 * 支持:
 *   - #EXTM3U 格式
 *   - TXT格式 (频道名,URL)
 *   - TVBox live 格式 (组名,#genre#,频道名,URL)
 */
object M3uParser {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun parseFromUrl(url: String): List<LiveChannel> = withContext(Dispatchers.IO) {
        try {
            val resp = client.newCall(Request.Builder().url(url).build()).execute()
            val text = resp.body?.string() ?: return@withContext emptyList()
            parse(text)
        } catch (_: Exception) { emptyList() }
    }

    suspend fun parseFromText(text: String): List<LiveChannel> = withContext(Dispatchers.IO) {
        parse(text)
    }

    private fun parse(text: String): List<LiveChannel> {
        return when {
            text.trimStart().startsWith("#EXTM3U") -> parseM3u(text)
            text.contains("#genre#") -> parseTvBox(text)
            else -> parseTxt(text)
        }
    }

    /** #EXTM3U 标准格式 */
    private fun parseM3u(text: String): List<LiveChannel> {
        val channels = mutableListOf<LiveChannel>()
        var currentGroup = "默认"
        var currentLogo = ""
        var currentName = ""

        text.lines().forEach { line ->
            when {
                line.startsWith("#EXTINF:") -> {
                    val groupMatch = Regex("group-title="([^"]+)"").find(line)
                    currentGroup = groupMatch?.groupValues?.get(1) ?: "默认"
                    val logoMatch = Regex("tvg-logo="([^"]+)"").find(line)
                    currentLogo = logoMatch?.groupValues?.get(1) ?: ""
                    val nameMatch = Regex(",(.+)$").find(line)
                    currentName = nameMatch?.groupValues?.get(1)?.trim() ?: ""
                }
                line.startsWith("http") && currentName.isNotEmpty() -> {
                    channels.add(LiveChannel(
                        name = currentName,
                        url = line.trim(),
                        group = currentGroup,
                        logo = currentLogo,
                    ))
                    currentName = ""
                    currentLogo = ""
                }
            }
        }
        return channels
    }

    /** TVBox live 格式: 组名,#genre#,频道,URL */
    private fun parseTvBox(text: String): List<LiveChannel> {
        val channels = mutableListOf<LiveChannel>()
        var group = "默认"

        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            when {
                line == "#genre#" -> {}  // next line is group name
                line.startsWith("http") -> {}  // URL of previous channel
                line.contains(",") && !line.startsWith("#") -> {
                    val parts = line.split(",", limit = 2)
                    if (parts.size == 2) {
                        channels.add(LiveChannel(name = parts[0], url = parts[1], group = group))
                    }
                }
                else -> group = line  // this is a group name
            }
        }
        return channels
    }

    /** 简单TXT格式: 频道名 URL */
    private fun parseTxt(text: String): List<LiveChannel> {
        return text.lines().map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { line ->
            val parts = line.split(Regex("\s+"), limit = 2)
            if (parts.size == 2 && parts[1].startsWith("http")) {
                LiveChannel(name = parts[0], url = parts[1])
            } else null
        }
    }
}
