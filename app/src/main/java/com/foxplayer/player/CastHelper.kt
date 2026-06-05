package com.foxplayer.player

import android.content.Context
import android.net.Uri

/**
 * 投屏辅助 — DLNA/乐播
 * Phase 3 基础框架，DLNA 协议交互待接入 cybergarage-android
 */
class CastHelper(private val context: Context) {

    data class CastDevice(
        val name: String,
        val ip: String,
        val port: Int = 8080,
        val type: String = "dlna",  // "dlna" / "lebo"
    )

    private val _devices = mutableListOf<CastDevice>()
    val devices: List<CastDevice> get() = _devices.toList()

    var onDeviceFound: ((CastDevice) -> Unit)? = null
    var onCastStarted: ((CastDevice) -> Unit)? = null
    var onCastStopped: (() -> Unit)? = null

    /** 搜索局域网 DLNA 设备 */
    fun searchDevices() {
        // DLNA SSDP discovery - placeholder for cybergarage-upnp integration
        // 实际实现需要: implementation("com.github.cybergarage:cybergarage-android:0.3")
    }

    /** 投屏播放 */
    fun startCast(device: CastDevice, url: String, title: String = "") {
        // DLNA SetAVTransportURI + Play
        onCastStarted?.invoke(device)
    }

    /** 停止投屏 */
    fun stopCast() {
        // DLNA Stop
        onCastStopped?.invoke()
    }
}
