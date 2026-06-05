package com.foxplayer.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class Video(
    val id: String = "",
    val title: String = "",
    val cover: String = "",        // poster URL
    val desc: String = "",
    val year: String = "",
    val area: String = "",
    val type: String = "",         // movie / tv / anime / variety
    val rating: Float = 0f,
    val playUrl: String = "",      // direct play URL
    val sourceKey: String = "",    // which source
    val episodes: List<Episode> = emptyList(),
)

@Serializable
@Keep
data class Episode(
    val name: String = "",
    val url: String = "",
)
