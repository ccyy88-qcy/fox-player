package com.foxplayer.db

import androidx.room.*
import com.foxplayer.model.Video
import com.foxplayer.model.VideoSource

/* ── 收藏 ── */
@Entity(tableName = "favorites", primaryKeys = ["videoId", "sourceKey"])
data class FavoriteEntity(
    val videoId: String,
    val title: String,
    val cover: String = "",
    val desc: String = "",
    val year: String = "",
    val area: String = "",
    val type: String = "",
    val rating: Float = 0f,
    val sourceKey: String = "",
    val addedAt: Long = System.currentTimeMillis(),
)

fun FavoriteEntity.toVideo() = Video(id = videoId, title = title, cover = cover,
    desc = desc, year = year, area = area, type = type, rating = rating, sourceKey = sourceKey)

fun Video.toFavorite() = FavoriteEntity(videoId = id, title = title, cover = cover,
    desc = desc, year = year, area = area, type = type, rating = rating, sourceKey = sourceKey)

/* ── 历史记录 ── */
@Entity(tableName = "history", primaryKeys = ["videoId", "sourceKey"])
data class HistoryEntity(
    val videoId: String,
    val title: String,
    val cover: String = "",
    val sourceKey: String = "",
    val episodeName: String = "",    // 上次看到哪集
    val position: Long = 0,          // 播放位置 ms
    val duration: Long = 0,          // 总时长 ms
    val updatedAt: Long = System.currentTimeMillis(),
)

/* ── 源配置 ── */
@Entity(tableName = "sources", primaryKeys = ["key"])
data class SourceEntity(
    val key: String,
    val name: String,
    val type: String,       // "json" / "xpath" / "js" / "live"
    val api: String = "",
    val playUrl: String = "",
    val searchUrl: String = "",
    val group: String = "默认",
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

fun SourceEntity.toVideoSource() = VideoSource(key=key, name=name, type=type, api=api,
    playUrl=playUrl, searchUrl=searchUrl, group=group, enabled=enabled)

fun VideoSource.toSourceEntity(order: Int = 0) = SourceEntity(key=key, name=name, type=type,
    api=api, playUrl=playUrl, searchUrl=searchUrl, group=group, enabled=enabled, sortOrder=order)
