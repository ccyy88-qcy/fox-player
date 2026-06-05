package com.foxplayer.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class LiveChannel(
    val name: String = "",
    val url: String = "",
    val group: String = "默认",
    val logo: String = "",
    val epgId: String = "",
)
