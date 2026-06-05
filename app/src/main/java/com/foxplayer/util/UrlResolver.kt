package com.foxplayer.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.regex.Pattern

/**
 * 视频流URL解析器 — 解决光速/速播等源返回HTML播放页面的问题
 *
 * 探测策略：
 * 1. 如果URL有已知视频扩展名(.m3u8/.mp4/.mpd/.ts) → 直接使用
 * 2. 尝试 GET 原URL + 检查Content-Type
 * 3. 如果返回HTML → 尝试解析DPlayer配置中的url字段
 * 4. 如果解析不到 → 尝试追加 /index.m3u8
 * 5. 如果还不行 → 尝试追加 .m3u8
 */
object UrlResolver {

    private val VIDEO_EXTS = listOf(".m3u8", ".mp4", ".mpd", ".ts", ".flv", ".webm", ".avi", ".mkv")

    /** 判断URL是否已有已知视频格式后缀 */
    fun hasVideoExtension(url: String): Boolean {
        val path = url.split("?").first().lowercase()
        return VIDEO_EXTS.any { path.endsWith(it) }
    }

    /** 从HTML中提取dplayer/playerjs配置中的视频URL */
    fun extractUrlFromHtml(html: String): String? {
        // 匹配 DPlayer 配置: url: '...'
        val dplayerPattern = Pattern.compile("""url:\s*['"]([^'"]+)['"]""")
        val m1 = dplayerPattern.matcher(html)
        if (m1.find()) {
            val url = m1.group(1)
            if (url.startsWith("http") || url.startsWith("//")) return url
        }

        // 匹配 video src
        val videoPattern = Pattern.compile("""<video[^>]+src\s*=\s*['"]([^'"]+)['"]""")
        val m2 = videoPattern.matcher(html)
        if (m2.find()) return m2.group(1)

        // 匹配 iframe src
        val iframePattern = Pattern.compile("""<iframe[^>]+src\s*=\s*['"]([^'"]+)['"]""")
        val m3 = iframePattern.matcher(html)
        if (m3.find()) return m3.group(1)

        // 匹配 source src
        val sourcePattern = Pattern.compile("""<source[^>]+src\s*=\s*['"]([^'"]+)['"]""")
        val m4 = sourcePattern.matcher(html)
        if (m4.find()) return m4.group(1)

        return null
    }

    /** 探测最佳播放URL */
    suspend fun resolveUrl(originalUrl: String): String = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = originalUrl.trim()
                .replace("\\/", "/").replace("\\u0026", "&")
                .let { if (it.startsWith("//")) "https:$it" else it }

            // 已有视频扩展 → 直接使用
            if (hasVideoExtension(cleanUrl)) return@withContext cleanUrl

            android.util.Log.d("UrlResolver", "探测无后缀URL: $cleanUrl")

            // 1. 先测试 /index.m3u8 (光速/速播常见模式)
            val m3u8Url = "${cleanUrl}/index.m3u8"
            if (probeUrl(m3u8Url)) {
                android.util.Log.d("UrlResolver", "→ 命中 /index.m3u8: $m3u8Url")
                return@withContext m3u8Url
            }

            // 2. 尝试 test.m3u8
            val m3u8Url2 = "${cleanUrl}.m3u8"
            if (probeUrl(m3u8Url2)) {
                android.util.Log.d("UrlResolver", "→ 命中 .m3u8: $m3u8Url2")
                return@withContext m3u8Url2
            }

            // 3. 抓取原URL检查是否是HTML播放页面
            val req = Request.Builder().url(cleanUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Referer", getBaseUrl(cleanUrl))
                .build()
            val resp = HttpClientManager.client.newCall(req).execute()
            val body = resp.body?.string() ?: return@withContext cleanUrl
            val ct = resp.header("Content-Type", "") ?: ""

            // 如果是HTML → 解析提取视频URL
            if (ct.contains("html") || body.trimStart().startsWith("<")) {
                val extractedUrl = extractUrlFromHtml(body)
                if (extractedUrl != null) {
                    val resolved = if (extractedUrl.startsWith("//")) "https:$extractedUrl"
                        else if (!extractedUrl.startsWith("http") && extractedUrl.startsWith("/"))
                            "${getBaseUrl(cleanUrl)}$extractedUrl"
                        else extractedUrl
                    android.util.Log.d("UrlResolver", "→ HTML解析到: $resolved")
                    return@withContext resolved
                }

                // 尝试追加 /index.m3u8 (HTML里没提取到但可能有规律)
                val m3u8Url3 = "${cleanUrl}/index.m3u8"
                if (probeUrl(m3u8Url3)) return@withContext m3u8Url3
            }

            // 是视频流 → 直接返回
            if (!ct.contains("html")) {
                android.util.Log.d("UrlResolver", "→ 非HTML, Content-Type: $ct")
                return@withContext cleanUrl
            }

            cleanUrl
        } catch (_: Exception) {
            originalUrl
        }
    }

    /** 快速探测URL是否可返回视频流 */
    private fun probeUrl(url: String): Boolean {
        return try {
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Referer", getBaseUrl(url))
                .build()
            val resp = HttpClientManager.client.newCall(req).execute()
            val ct = resp.header("Content-Type", "") ?: ""
            resp.isSuccessful && (ct.contains("mpegurl") || ct.contains("m3u8") || ct.contains("mp4") || ct.contains("video"))
        } catch (_: Exception) { false }
    }

    private fun getBaseUrl(url: String): String {
        return try {
            val u = java.net.URL(url)
            "${u.protocol}://${u.host}/"
        } catch (_: Exception) { url }
    }
}
