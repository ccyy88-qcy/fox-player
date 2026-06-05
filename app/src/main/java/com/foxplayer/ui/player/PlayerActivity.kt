package com.foxplayer.ui.player

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.foxplayer.R
import com.foxplayer.player.FoxPlayer

class PlayerActivity : AppCompatActivity() {
    private var player: FoxPlayer? = null
    private var playUrl = ""
    private var title = ""
    private var isFullscreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playUrl = intent.getStringExtra("url") ?: ""
        title = intent.getStringExtra("title") ?: ""

        if (playUrl.isBlank()) {
            Toast.makeText(this, "无播放地址", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 初始化播放器
        val surfaceView = findViewById<SurfaceView>(R.id.playerSurface)
        player = FoxPlayer(this).apply {
            play(playUrl)
            onError = { msg -> Toast.makeText(this@PlayerActivity, "播放错误: $msg", Toast.LENGTH_LONG).show() }
        }
        player?.setSurface(surfaceView)

        // 标题
        findViewById<TextView>(R.id.tvTitle).text = title

        // 返回键
        findViewById<View>(R.id.ivBack).setOnClickListener {
            finish()
            // 恢复竖屏
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        // 全屏按钮
        findViewById<View>(R.id.ivFullscreen).setOnClickListener { toggleFullscreen() }

        // 播放/暂停
        findViewById<View>(R.id.ivPlayPause).setOnClickListener {
            player?.let { p -> if (p.isPlaying()) p.pause() else p.play() }
        }

        // 点击画面显示/隐藏控制栏
        surfaceView.setOnClickListener { toggleControls() }

        // 进度更新
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.postDelayed(object : Runnable {
            override fun run() {
                val p = player ?: return
                val pos = p.getCurrentPosition()
                val dur = p.getDuration()
                if (dur > 0) {
                    findViewById<SeekBar>(R.id.seekBar).progress = ((pos * 100) / dur).toInt()
                    findViewById<TextView>(R.id.tvProgress).text = formatMs(pos)
                    findViewById<TextView>(R.id.tvDuration).text = formatMs(dur)
                }
                handler.postDelayed(this, 500)
            }
        }, 500)

        // SeekBar拖动
        findViewById<SeekBar>(R.id.seekBar).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                val dur = player?.getDuration() ?: 0
                if (dur > 0) {
                    val pos = (findViewById<SeekBar>(R.id.seekBar).progress.toLong() * dur) / 100
                    player?.seekTo(pos)
                }
            }
        })

        showControls()
    }

    private fun toggleFullscreen() {
        if (isFullscreen) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
        isFullscreen = !isFullscreen
    }

    private fun showControls() {
        findViewById<LinearLayout>(R.id.topBar).visibility = View.VISIBLE
        findViewById<LinearLayout>(R.id.bottomBar).visibility = View.VISIBLE
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            findViewById<LinearLayout>(R.id.topBar).visibility = View.GONE
            findViewById<LinearLayout>(R.id.bottomBar).visibility = View.GONE
        }, 3000)
    }

    private fun toggleControls() {
        val topBar = findViewById<LinearLayout>(R.id.topBar)
        if (topBar.visibility == View.VISIBLE) {
            topBar.visibility = View.GONE
            findViewById<LinearLayout>(R.id.bottomBar).visibility = View.GONE
        } else {
            showControls()
        }
    }

    private fun formatMs(ms: Long): String {
        if (ms <= 0) return "00:00"
        val s = ms / 1000
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, sec)
        else String.format("%02d:%02d", m, sec)
    }

    /** 小窗口(PiP)模式 */
    fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        } else {
            Toast.makeText(this, "需要Android 8.0以上", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // 用户按Home键时自动进入PiP
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        if (isInPictureInPictureMode) {
            // PiP模式：隐藏控制栏
            findViewById<LinearLayout>(R.id.topBar).visibility = View.GONE
            findViewById<LinearLayout>(R.id.bottomBar).visibility = View.GONE
        } else {
            // 退出PiP：恢复控制栏
            showControls()
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
