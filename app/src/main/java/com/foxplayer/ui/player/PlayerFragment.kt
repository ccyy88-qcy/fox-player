package com.foxplayer.ui.player

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.foxplayer.R
import com.foxplayer.player.FoxPlayer

class PlayerFragment : Fragment(R.layout.fragment_player) {
    private var player: FoxPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isSeeking = false
    private var isFullscreen = false
    private var playUrl = ""
    private var title = ""
    private var videoId = ""
    private var controlsVisible = true
    private var errorRetryCount = 0

    companion object {
        private const val PREFS_NAME = "fox_player_history"
        private const val KEY_PREFIX = "position_"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playUrl = arguments?.getString("url") ?: arguments?.getString("videoId") ?: ""
        title = arguments?.getString("title") ?: arguments?.getString("videoTitle") ?: ""
        videoId = playUrl.hashCode().toString() // 用URL哈希作唯一标识

        if (playUrl.isBlank()) {
            Toast.makeText(requireContext(), "无播放地址", Toast.LENGTH_LONG).show()
            return
        }

        view.findViewById<TextView>(R.id.tvTitle).text = title

        // ── 返回键 ──
        view.findViewById<View>(R.id.ivBack).setOnClickListener {
            savePosition()
            if (isFullscreen) exitFullscreen()
            findNavController().navigateUp()
        }

        // ── 全屏键 ──
        view.findViewById<View>(R.id.ivFullscreen).setOnClickListener {
            if (isFullscreen) exitFullscreen() else enterFullscreen()
        }

        // ── 倍速切换 ──
        val tvSpeed = view.findViewById<TextView>(R.id.tvSpeed)
        val speeds = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        var speedIdx = 2 // default 1.0x
        tvSpeed.setOnClickListener {
            speedIdx = (speedIdx + 1) % speeds.size
            player?.setSpeed(speeds[speedIdx])
            tvSpeed.text = String.format("%.2gx", speeds[speedIdx])
                    .replace("0.", ".").replace("1.0", "1").replace("1.5", "1.5")
            Toast.makeText(requireContext(), "倍速: ${tvSpeed.text}", Toast.LENGTH_SHORT).show()
        }

        // ── 播放/暂停（居中大按钮） ──
        val ivPlayPause = view.findViewById<ImageView>(R.id.ivPlayPause)
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

        // ── 进度条 ──
        val seekBar = view.findViewById<SeekBar>(R.id.seekBar)
        val tvProgress = view.findViewById<TextView>(R.id.tvProgress)
        val tvDuration = view.findViewById<TextView>(R.id.tvDuration)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)

        // 进度定时更新
        handler.post(object : Runnable {
            override fun run() {
                val p = player ?: return
                val pos = p.getCurrentPosition()
                val dur = p.getDuration()
                if (dur > 0 && !isSeeking) {
                    seekBar.progress = ((pos * 100) / dur).toInt()
                    tvProgress.text = formatMs(pos)
                    tvDuration.text = formatMs(dur)
                } else if (dur <= 0) {
                    // 还没获取到时长，显示当前进度
                    tvProgress.text = formatMs(pos)
                    tvDuration.text = "--:--"
                }
                handler.postDelayed(this, 500)
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
                if (dur > 0) {
                    player?.seekTo((seekBar.progress.toLong() * dur) / 100)
                }
            }
        })

        // ── SurfaceView ──
        val surfaceView = view.findViewById<SurfaceView>(R.id.playerSurface)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                player = FoxPlayer(requireContext()).apply {
                    play(playUrl)
                    onBuffering = { b ->
                        progressBar.visibility = if (b) View.VISIBLE else View.GONE
                    }
                    onError = { msg ->
                        if (errorRetryCount < 2) {
                            errorRetryCount++
                        } else {
                            Toast.makeText(requireContext(), "播放错误: $msg", Toast.LENGTH_LONG).show()
                        }
                    }
                    onPlaybackStateChanged = { state ->
                        when (state) {
                            com.google.android.exoplayer2.Player.STATE_READY -> {
                                // 恢复播放位置
                                val savedPos = loadPosition()
                                if (savedPos > 5000) {
                                    player?.seekTo(savedPos)
                                    Toast.makeText(requireContext(), "已恢复播放位置", Toast.LENGTH_SHORT).show()
                                }
                                // 更新时长显示
                                val dur = player?.getDuration() ?: 0
                                if (dur > 0) tvDuration.text = formatMs(dur)
                                // 隐藏播放按钮，显示暂停图标
                                ivPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                            }
                            com.google.android.exoplayer2.Player.STATE_BUFFERING -> {
                                progressBar.visibility = View.VISIBLE
                            }
                            com.google.android.exoplayer2.Player.STATE_ENDED -> {
                                ivPlayPause.setImageResource(android.R.drawable.ic_media_play)
                                ivPlayPause.visibility = View.VISIBLE
                                showControls()
                            }
                            com.google.android.exoplayer2.Player.STATE_IDLE -> {
                                progressBar.visibility = View.GONE
                            }
                        }
                    }
                }
                player?.setSurface(surfaceView)
                // 初始显示控制栏
                showControls()
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                savePosition()
                player?.release()
                player = null
            }
        })

        // ── 点击画面切换控制栏 ──
        surfaceView.setOnClickListener { toggleControls() }
    }

    // ── 记忆播放 ──

    private fun savePosition() {
        val pos = player?.getCurrentPosition() ?: return
        if (pos > 3000) {
            requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putLong("$KEY_PREFIX$videoId", pos).apply()
        }
    }

    private fun loadPosition(): Long {
        return requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong("$KEY_PREFIX$videoId", 0L)
    }

    // ── 全屏 ──

    private fun enterFullscreen() {
        val activity = requireActivity()
        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
        isFullscreen = true
        autoHideControls()
    }

    private fun exitFullscreen() {
        val activity = requireActivity()
        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        isFullscreen = false
        showControls()
    }

    // ── 控制栏显示/隐藏 ──

    private fun showControls() {
        val view = requireView()
        view.findViewById<LinearLayout>(R.id.topBar).visibility = View.VISIBLE
        view.findViewById<LinearLayout>(R.id.bottomBar).visibility = View.VISIBLE
        controlsVisible = true
        handler.removeCallbacksAndMessages(null)
        if (player?.isPlaying() == true) {
            handler.postDelayed({ autoHideControls() }, 4000)
        }
    }

    private fun autoHideControls() {
        if (!isAdded) return
        val view = requireView()
        view.findViewById<LinearLayout>(R.id.topBar).visibility = View.GONE
        view.findViewById<LinearLayout>(R.id.bottomBar).visibility = View.GONE
        controlsVisible = false
    }

    private fun toggleControls() {
        if (controlsVisible) {
            autoHideControls()
        } else {
            showControls()
        }
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

    override fun onDestroyView() {
        savePosition()
        handler.removeCallbacksAndMessages(null)
        player?.release(); player = null
        if (isFullscreen) exitFullscreen()
        super.onDestroyView()
    }
}
