package com.foxplayer.source.impl

import com.foxplayer.model.LiveChannel
import com.foxplayer.util.HttpClientManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * M3U/M3U8/TXT 直播源解析器
 * 支持:
 *   - #EXTM3U 格式
 *   - TXT格式 (频道名,URL)
 *   - TVBox live 格式 (组名,#genre#,频道名,URL)
 *
 * 自动去重 + 过滤无效源
 */
object M3uParser {

    /** 已知的无效URL模式 */
    private val INVALID_PATTERNS = listOf(
        "127.0.0.1", "localhost", "0.0.0.0",
        "example.com", "test.com", "null",
        "广告", "ad", "advert",
    )

    /** 真实电视频道名关键词 — 非此列表中的频道将被过滤 */
    private val TV_KEYWORDS = listOf(
        "CCTV", "央视",
        "卫视",
        "频道",
        "电视台", "电视",
        "综合", "新闻", "经济", "都市", "生活", "文艺",
        "影视", "电影", "体育", "娱乐", "少儿", "卡通",
        "科教", "纪录", "戏曲", "音乐", "法治", "公共",
        "国际", "军事", "农业", "少儿",
    )

    /** 已知的影片/非直播内容名 — 过滤掉 */
    private val MOVIE_NAMES = setOf(
        "西游记", "三国演义", "水浒传", "红楼梦",
        "天龙八部", "鹿鼎记", "笑傲江湖", "射雕英雄传",
        "仙剑奇侠传", "封神榜", "闯关东", "新白娘子传奇",
        "庆余年", "狂飙", "三体", "繁花",
        "斗破苍穹", "长相思", "与凤行", "玫瑰的故事",
    )

    /** 已知的常见有效直播域名（用于保留过滤） */
    private val VALID_HOST_KEYWORDS = listOf(
        "cctv", "cntv", "tv.", "live.", "hls.", "stream",
        "m3u8", "play", "cdn", "video", "pull",
        "移动", "联通", "电信", "iptv",
    )

    suspend fun parseFromUrl(url: String): List<LiveChannel> = withContext(Dispatchers.IO) {
        try {
            val body = HttpClientManager.get(url)
            if (body.isNullOrBlank()) return@withContext emptyList()
            val channels = parse(body)
            filterAndDeduplicate(channels)
        } catch (_: Exception) { emptyList() }
    }

    suspend fun parseFromText(text: String): List<LiveChannel> = withContext(Dispatchers.IO) {
        val channels = parse(text)
        filterAndDeduplicate(channels)
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

        val groupPattern = Regex("""group-title="([^"]+)"""")
        val logoPattern = Regex("""tvg-logo="([^"]+)"""")
        val namePattern = Regex(""",(.+)$""")

        text.lines().forEach { line ->
            when {
                line.startsWith("#EXTINF:") -> {
                    currentGroup = groupPattern.find(line)?.groupValues?.get(1) ?: "默认"
                    currentLogo = logoPattern.find(line)?.groupValues?.get(1) ?: ""
                    currentName = namePattern.find(line)?.groupValues?.get(1)?.trim() ?: ""
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
        var prevName = ""

        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            when {
                // TVBox标准: 频道名,URL (下一行可能是组名)
                line.startsWith("http") && prevName.isNotEmpty() -> {
                    channels.add(LiveChannel(name = prevName, url = line, group = group))
                    prevName = ""
                }
                line == "#genre#" -> { }
                line.contains(",") && !line.startsWith("#") -> {
                    // 可能是一行格式: 频道名,URL
                    val parts = line.split(",", limit = 2)
                    val second = parts.getOrElse(1) { "" }.trim()
                    if (second.startsWith("http")) {
                        channels.add(LiveChannel(name = parts[0].trim(), url = second, group = group))
                    } else {
                        // 这是组名+频道名格式: 央视,#genre#\nCCTV-1,http://...
                        prevName = parts[0].trim()
                    }
                }
                else -> {
                    // 单独一行可能是组名
                    if (line != "#genre#") group = line
                }
            }
        }
        return channels
    }

    /** 简单TXT格式: 频道名 URL */
    private fun parseTxt(text: String): List<LiveChannel> {
        return text.lines().map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { line ->
            val parts = line.split(Regex("\\s+"), limit = 2)
            if (parts.size == 2 && parts[1].startsWith("http")) {
                LiveChannel(name = parts[0], url = parts[1])
            } else null
        }
    }

    /** 过滤无效 + 去重 + 只保留真实电视台 */
    private fun filterAndDeduplicate(list: List<LiveChannel>): List<LiveChannel> {
        // 1. 过滤无效URL + 过滤非电视台名称
        val filtered = list.filter { ch ->
            val name = ch.name.trim()
            ch.url.isNotBlank() &&
            ch.url.startsWith("http") &&
            name.isNotBlank() &&
            name.length >= 4 && // 电视台名至少4字
            !INVALID_PATTERNS.any { ch.url.contains(it, ignoreCase = true) } &&
            !MOVIE_NAMES.any { name.contains(it) } &&
            // 必须包含电视台关键词 或 在有效直播组
            (TV_KEYWORDS.any { name.contains(it) } ||
             ch.group.contains("央视") || ch.group.contains("卫视") ||
             ch.group.contains("频道") || ch.group.contains("电视"))
        }

        // 2. 去重：同组+同名只留一个（保留第一个）
        val seen = mutableSetOf<String>()
        val deduped = mutableListOf<LiveChannel>()
        for (ch in filtered) {
            val key = "${ch.group}:${ch.name}"
            if (key !in seen) {
                seen.add(key)
                deduped.add(ch)
            }
        }

        // 3. 频道排序：央视 > 卫视 > 其他
        val sorted = deduped.sortedBy { ch ->
            when {
                ch.group.contains("央视") || ch.group.contains("CCTV") -> 0
                ch.group.contains("卫视") -> 1
                ch.group.contains("省") || ch.group.contains("地方") -> 2
                else -> 3
            }
        }

        return sorted
    }
}
