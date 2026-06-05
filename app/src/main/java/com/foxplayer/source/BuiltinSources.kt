package com.foxplayer.source

import com.foxplayer.model.VideoSource

object BuiltinSources {
    val videoSources: List<VideoSource> = listOf(
        // ★ 主源 — 已验证 Termux/手机数据均可
        VideoSource(key = "guangsu", name = "光速资源", type = "json",
            api = "https://api.guangsuapi.com/api.php/provide/vod/", group = "主源"),
        VideoSource(key = "subozy", name = "速播资源", type = "json",
            api = "https://www.subozy.com/api.php/provide/vod/", group = "主源"),
        VideoSource(key = "zuidazy", name = "最大资源", type = "json",
            api = "https://zuidazy.com/api.php/provide/vod/", group = "主源"),
        // 备用源
        VideoSource(key = "1080zyk", name = "1080资源库", type = "json",
            api = "https://api.1080zyku.com/inc/apijson.php", group = "备用"),
        VideoSource(key = "lzi", name = "量子资源", type = "json",
            api = "https://cj.lzi.cc/api.php/provide/vod/", group = "备用"),
        VideoSource(key = "tiankong", name = "天空资源", type = "json",
            api = "https://m3u8.tiankongapi.com/api.php/provide/vod/", group = "备用"),
        VideoSource(key = "hniu", name = "红牛资源", type = "json",
            api = "https://www.hniuapi.com/api.php/provide/vod/", group = "备用"),
        VideoSource(key = "feisu", name = "飞速资源", type = "json",
            api = "https://www.feisuzyapi.com/api.php/provide/vod/", group = "备用"),
    )

    val liveSources: List<Pair<String, String>> = listOf(
        "肥羊直播" to "https://raw.githubusercontent.com/Guovin/TV/gd/output/result.m3u",
        "IPTV中国" to "https://iptv-org.github.io/iptv/countries/cn.m3u",
    )

    /** 获取启用的主源列表 */
    fun getPrimarySources(): List<VideoSource> = videoSources.filter { it.group == "主源" }

    /** 获取启用的备用源列表 */
    fun getBackupSources(): List<VideoSource> = videoSources.filter { it.group == "备用" }

    const val DEFAULT_LIVE_URL = "https://raw.githubusercontent.com/Guovin/TV/gd/output/result.m3u"
}
