package com.foxplayer.player

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * 播放器手势控制
 * - 左半屏上下滑: 亮度
 * - 右半屏上下滑: 音量
 * - 左右滑: 进度快进/快退
 * - 双击: 播放/暂停
 * - 长按: 倍速播放
 */
class GestureController(
    private val context: Context,
    private val player: FoxPlayer,
    private val onBrightChange: (Float) -> Unit,
    private val onVolumeChange: (Int) -> Unit,
    private val onProgressChange: (Long, String) -> Unit,
    private val onSpeedChange: (Float) -> Unit,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    private var isLongPressing = false
    private var savedSpeed = 1f

    val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (player.isPlaying()) player.pause() else player.play()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            isLongPressing = true
            savedSpeed = player.getSpeed()
            player.setSpeed(2f)
            onSpeedChange(2f)
        }

        override fun onScroll(
            down: MotionEvent, move: MotionEvent, dx: Float, dy: Float
        ): Boolean {
            val viewWidth = (down.source and MotionEvent.TOOL_TYPE_FINGER.inv()).toFloat().coerceAtLeast(1f)
            val viewW = 1080f  // approximate
            val viewH = 1920f

            val isLeftHalf = down.x < viewW / 2
            val isVertical = abs(dy) > abs(dx)

            when {
                isVertical && isLeftHalf -> {
                    // 亮度
                    val delta = -dy / viewH * 255
                    val current = Settings.System.getInt(
                        context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128
                    )
                    val new = (current + delta).coerceIn(0f, 255f)
                    onBrightChange(new / 255f)
                }
                isVertical && !isLeftHalf -> {
                    // 音量
                    val delta = -dy / viewH * maxVolume
                    val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val new = (current + delta).toInt().coerceIn(0, maxVolume)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, new, 0)
                    onVolumeChange(new)
                }
                !isVertical -> {
                    // 进度
                    val deltaSec = dx / viewW * 120  // 满屏=120秒
                    val newPos = (player.getCurrentPosition() + (deltaSec * 1000).toLong())
                        .coerceIn(0, player.getDuration())
                    player.seekTo(newPos)
                    val sec = newPos / 1000
                    onProgressChange(newPos, "${sec / 60}:${String.format("%02d", sec % 60)}")
                }
            }
            return true
        }
    })

    fun onTouchEvent(e: MotionEvent) {
        gestureDetector.onTouchEvent(e)
        if (e.action == MotionEvent.ACTION_UP && isLongPressing) {
            isLongPressing = false
            player.setSpeed(savedSpeed)
            onSpeedChange(savedSpeed)
        }
    }
}
