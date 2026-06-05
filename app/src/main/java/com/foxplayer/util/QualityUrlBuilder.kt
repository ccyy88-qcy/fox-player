package com.foxplayer.util

/**
 * 真实画质URL转换器 — 针对国内视频源CDN的常见多路清晰度URL规则
 *
 * 国内视频源（光速/速播/最大）通常提供单流的 HLS 地址（只有一条码流），
 * 但不同CDN支持通过URL变体来切换清晰度。
 *
 * 已知模式：
 * ────────────────
 * 速播/光速类:  https://v.xxx.com/play/abc/index.m3u8
 *   超清:  /index.m3u8  (默认)
 *   高清:  /index.m3u8?height=720  或  /high/index.m3u8
 *   标清:  /index.m3u8?height=480  或  /mid/index.m3u8
 *   流畅:  /index.m3u8?height=360  或  /low/index.m3u8
 *
 * v.gsuus.com 类:  支持 ?height=480 参数
 * play.xluuss.com 类: 支持 ?height=480 参数
 *
 * 最大资源: 通常只有单码流，不支持多画质
 */
object QualityUrlBuilder {

    data class QualityOption(val label: String, val height: Int, val buildUrl: (String) -> String)

    /** 根据原始播放URL生成各个画质的真实地址 */
    fun buildAllQualities(originalUrl: String): List<QualityOption> {
        val base = originalUrl.removeSuffix("/index.m3u8").removeSuffix(".m3u8")

        return listOf(
            QualityOption("超清", 1920, { "${base}/index.m3u8" }),
            QualityOption("高清", 720, { "${base}/index.m3u8?height=720" }),
            QualityOption("标清", 480, { "${base}/index.m3u8?height=480" }),
            QualityOption("流畅", 360, { "${base}/index.m3u8?height=360" }),
        )
    }

    /** 根据画质选择获取播放URL */
    fun getQualityUrl(originalUrl: String, qualityLabel: String): String {
        val options = buildAllQualities(originalUrl)
        return options.find { it.label == qualityLabel }?.buildUrl?.invoke(originalUrl) ?: originalUrl
    }
}
