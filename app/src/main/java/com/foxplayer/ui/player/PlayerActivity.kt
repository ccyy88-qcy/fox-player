package com.foxplayer.ui.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.foxplayer.R
import com.foxplayer.player.FoxPlayer

class PlayerActivity : AppCompatActivity() {
    private var player: FoxPlayer? = null
    private var playUrl = ""
    private var title = ""
    private var videoId = ""
    private var isFullscreen = false
    private var controlsVisible = true
    private var errorRetryCount = 0
    private var isSeeking = false
    private val handler = Handler(Looper.getMainLooper())
    private val progressHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val PREFS_NAME = "fox_player_history"
        private const val KEY_PREFIX = "position_"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playUrl = intent.getStringExtra("url") ?: ""
        title = intent.getStringExtra("title") ?: ""
        videoId = playUrl.hashCode().toString()

        if (playUrl.isBlank()) {
            Toast.makeText(this, "无播放地址", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 标题
        findViewById<TextView>(R.id.tvTitle).text = title

        // 返回
        findViewById<View>(R.id.ivBack).setOnClickListener {
            savePosition()
            finish()
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        // 全屏
        findViewById<View>(R.id.ivFullscreen).setOnClickListener { toggleFullscreen() }

        // 倍速切换
        val tvSpeed = findViewById<TextView>(R.id.tvSpeed)
        val speeds = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        var speedIdx = 2
        tvSpeed.setOnClickListener {
            speedIdx = (speedIdx + 1) % speeds.size
            player?.setSpeed(speeds[speedIdx])
            tvSpeed.text = String.format("%.2gx", speeds[speedIdx])
                .replace("0.", ".").replace("1.0", "1").replace("1.5", "1.5")
            Toast.makeText(this, "倍速: ${tvSpeed.text}", Toast.LENGTH_SHORT).show()
        }

        // 画质切换
        val tvQuality = findViewById<TextView>(R.id.tvQuality)
        tvQuality.setOnClickListener {
            val p = player ?: return@setOnClickListener
            val qualities = p.fetchAvailableQualities()
            if (qualities.isEmpty()) {
                Toast.makeText(this, "当前流无多画质可选", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val labels = qualities.map { it.label }
            android.app.AlertDialog.Builder(this)
                .setTitle("选择画质")
                .setSingleChoiceItems(labels.toTypedArray(), -1) { dialog, which ->
                    dialog.dismiss()
                    if (which >= 0 && which < qualities.size) {
                        p.switchQuality(which)
                        tvQuality.text = qualities[which].label
                    }
                }
                .setNegativeButton("自动", { d, _ -> d.dismiss(); p.switchQuality(-1); tvQuality.text = "自动" })
                .show()
        }

        // 播放/暂停
        val ivPlayPause = findViewById<ImageView>(R.id.ivPlayPause)
        ivPlayPause.setOnClickListener {
            player?.let { p ->
                if (p.isPlaying()) {
                    p.pause()
                    ivPlayPause.setImageResource(android.R.drawable.ic_media_play)
                    ivPlayPause.visibility = View.VISIBLE
                    showControls()
                } else {
                    p.play()
                    ivPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                    autoHideControls()
                }
            }
        }

        // 进度条
        val seekBar = findViewById<SeekBar>(R.id.seekBar)
        val tvProgress = findViewById<TextView>(R.id.tvProgress)
        val tvDuration = findViewById<TextView>(R.id.tvDuration)
        // 进度条（独立progressHandler，不受showControls干扰）
        progressHandler.post(object : Runnable {
            override fun run() {
                val p = player ?: return
                val pos = p.getCurrentPosition()
                val dur = p.getDuration()
                if (dur > 0 && !isSeeking) {
                    seekBar.progress = ((pos * 100) / dur).toInt()
                    tvProgress.text = formatMs(pos)
                    tvDuration.text = formatMs(dur)
                } else if (dur <= 0) {
                    tvProgress.text = formatMs(pos)
                    tvDuration.text = "--:--"
                }
                progressHandler.postDelayed(this, 500)
            }
        })

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    val dur = player?.getDuration() ?: 0
                    tvProgress.text = if (dur > 0) formatMs((p.toLong() * dur) / 100) else "00:00"
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { isSeeking = true }
            override fun onStopTrackingTouch(sb: SeekBar?) {
                isSeeking = false
                val dur = player?.getDuration() ?: 0
                if (dur > 0) player?.seekTo((seekBar.progress.toLong() * dur) / 100)
            }
        })

        // 初始化播放器
        val surfaceView = findViewById<SurfaceView>(R.id.playerSurface)
        player = FoxPlayer(this).apply {
            play(playUrl)
            onBuffering = { b ->
                findViewById<ProgressBar>(R.id.progressBar).visibility =
                    if (b) View.VISIBLE else View.GONE
            }
            onError = { msg ->
                if (errorRetryCount < 2) {
                    errorRetryCount++
                } else {
                    runOnUiThread {
                        android.app.AlertDialog.Builder(this@PlayerActivity)
                            .setTitle("播放失败")
                            .setMessage("$msg\n\nURL: ${playUrl.take(80)}...")
                            .setPositiveButton("重试") { _, _ ->
                                val oldPlayer = player
                                player = FoxPlayer(this@PlayerActivity).apply {
                                    play(playUrl)
                                    onBuffering = { b ->
                                        findViewById<ProgressBar>(R.id.progressBar).visibility =
                                            if (b) View.VISIBLE else View.GONE
                                    }
                                    onError = { msg2 ->
                                        runOnUiThread {
                                            Toast.makeText(this@PlayerActivity, "重试失败: $msg2", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                                player?.setSurface(findViewById(R.id.playerSurface))
                                oldPlayer?.release()
                            }
                            .setNegativeButton("返回") { _, _ -> finish() }
                            .setNeutralButton("用浏览器打开") { _, _ ->
                                try {
                                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(playUrl)))
                                } catch (_: Exception) { }
                                finish()
                            }
                            .setCancelable(false)
                            .show()
                    }
                }
            }
            onPlaybackStateChanged = { state ->
                when (state) {
                    com.google.android.exoplayer2.Player.STATE_READY -> {
                        val savedPos = loadPosition()
                        if (savedPos > 5000) {
                            player?.seekTo(savedPos)
                            Toast.makeText(this@PlayerActivity, "已恢复播放", Toast.LENGTH_SHORT).show()
                        }
                        val dur = player?.getDuration() ?: 0
                        if (dur > 0) tvDuration.text = formatMs(dur)
                        ivPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                        ivPlayPause.visibility = View.GONE
                        showControls()
                    }
                    com.google.android.exoplayer2.Player.STATE_ENDED -> {
                        ivPlayPause.setImageResource(android.R.drawable.ic_media_play)
                        ivPlayPause.visibility = View.VISIBLE
                        showControls()
                    }
                }
            }
        }
        player?.setSurface(surfaceView)

        // 点击画面切换控制栏
        surfaceView.setOnClickListener { toggleControls() }
    }

    private fun savePosition() {
        val pos = player?.getCurrentPosition() ?: return
        if (pos > 3000) {
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putLong("$KEY_PREFIX$videoId", pos).apply()
        }
    }

    private fun loadPosition(): Long {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong("$KEY_PREFIX$videoId", 0L)
    }

    private fun toggleFullscreen() {
        if (isFullscreen) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
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
        controlsVisible = true
        handler.removeCallbacksAndMessages(null)
        if (player?.isPlaying() == true) {
            handler.postDelayed({ autoHideControls() }, 4000)
        }
    }

    private fun autoHideControls() {
        findViewById<LinearLayout>(R.id.topBar).visibility = View.GONE
        findViewById<LinearLayout>(R.id.bottomBar).visibility = View.GONE
        controlsVisible = false
    }

    private fun toggleControls() {
        if (controlsVisible) autoHideControls() else showControls()
    }

    private fun formatMs(ms: Long): String {
        if (ms <= 0) return "00:00"
        val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, sec) else String.format("%02d:%02d", m, sec)
    }

    override fun onPause() {
        super.onPause()
        savePosition()
        player?.pause()
    }

    override fun onDestroy() {
        savePosition()
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        super.onDestroy()
    }
}
