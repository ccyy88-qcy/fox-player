package com.foxplayer.model

enum class Category(val label: String, val icon: String) {
    MOVIE("电影", "🎬"),
    TV("电视剧", "📺"),
    ANIME("动漫", "🎭"),
    VARIETY("综艺", "🎤"),
    LIVE("直播", "📡"),
    FAVORITE("收藏", "⭐"),
}
