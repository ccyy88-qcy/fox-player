package com.foxplayer.source

import com.foxplayer.model.Episode
import com.foxplayer.model.Video
import com.foxplayer.model.VideoSource

/**
 * 视频源解析器统一接口
 * 所有源类型 (JSON/XPath/JS) 均实现此接口
 */
interface ISourceParser {
    val sourceKey: String
    val sourceName: String

    /** 获取分类列表页 */
    suspend fun getCategoryVideos(category: String, page: Int = 1): List<Video>

    /** 搜索 */
    suspend fun search(keyword: String, page: Int = 1): List<Video>

    /** 获取详情（剧集列表） */
    suspend fun getDetail(video: Video): Video

    /** 解析播放地址 */
    suspend fun parsePlayUrl(episode: Episode): String

    /** 获取最新/推荐 */
    suspend fun getLatest(page: Int = 1): List<Video>
}
