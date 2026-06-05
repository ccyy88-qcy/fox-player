package com.foxplayer.source

import com.foxplayer.model.VideoSource
import com.foxplayer.source.impl.JsonSourceParser

/**
 * 源初始化 — 注册所有内置源
 */
object SourceInitializer {

    /**
     * 注册内置影视源解析器到 SourceManager
     */
    fun init(sourceManager: SourceManager) {
        // 注册JSON API解析器
        BuiltinSources.videoSources
            .filter { it.type == "json" && it.enabled }
            .forEach { src ->
                sourceManager.registerParser(
                    JsonSourceParser(
                        sourceKey = src.key,
                        sourceName = src.name,
                        apiUrl = src.api,
                        playParseUrl = src.playUrl,
                    )
                )
            }

        // 更新源列表
        sourceManager.updateSources(BuiltinSources.videoSources)
    }
}
