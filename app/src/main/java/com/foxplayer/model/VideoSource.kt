package com.foxplayer.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class VideoSource(
    val key: String = "",
    val name: String = "",
    val type: String = "",     // "json" / "xpath" / "js"
    val api: String = "",      // source URL
    val playUrl: String = "",  // play parse URL
    val searchUrl: String = "",
    val group: String = "默认",
    val enabled: Boolean = true,
)
