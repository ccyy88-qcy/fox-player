package com.foxplayer.ui.player

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playUrl = arguments?.getString("url") ?: arguments?.getString("videoId") ?: ""
        title = arguments?.getString("title") ?: arguments?.getString("videoTitle") ?: ""

        if (playUrl.isBlank()) {
            Toast.makeText(requireContext(), "无播放地址", Toast.LENGTH_LONG).show()
            return
        }

        view.findViewById<TextView>(R.id.tvTitle).text = title

        // 返回键
        view.findViewById<View>(R.id.ivBack).setOnClickListener {
            if (isFullscreen) exitFullscreen()
            findNavController().navigateUp()
        }

        // 全屏键
        view.findViewById<View>(R.id.ivFullscreen).setOnClickListener {
            if (isFullscreen) exitFullscreen() else enterFullscreen()
        }

        // 播放/暂停
        view.findViewById<View>(R.id.ivPlayPause).setOnClickListener {
            player?.let { p -> if (p.isPlaying()) p.pause() else p.play() }
        }

        // 进度更新
        val seekBar = view.findViewById<SeekBar>(R.id.seekBar)
        val tvProgress = view.findViewById<TextView>(R.id.tvProgress)
        val tvDuration = view.findViewById<TextView>(R.id.tvDuration)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)

        handler.postDelayed(object : Runnable {
            override fun run() {
                val p = player ?: return
                val pos = p.getCurrentPosition()
                val dur = p.getDuration()
                if (dur > 0 && !isSeeking) {
                    seekBar.progress = ((pos * 100) / dur).toInt()
                    tvProgress.text = formatMs(pos)
                    tvDuration.text = formatMs(dur)
                }
                handler.postDelayed(this, 500)
            }
        }, 500)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    val dur = player?.getDuration() ?: 0
                    if (dur > 0) tvProgress.text = formatMs((p.toLong() * dur) / 100)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { isSeeking = true }
            override fun onStopTrackingTouch(sb: SeekBar?) {
                isSeeking = false
                val dur = player?.getDuration() ?: 0
                if (dur > 0) player?.seekTo((seekBar.progress.toLong() * dur) / 100)
            }
        })

        // SurfaceView
        val surfaceView = view.findViewById<SurfaceView>(R.id.playerSurface)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                player = FoxPlayer(requireContext()).apply {
                    play(playUrl)
                    onBuffering = { b -> progressBar.visibility = if (b) View.VISIBLE else View.GONE }
                    onError = { msg -> Toast.makeText(requireContext(), "播放错误: $msg", Toast.LENGTH_LONG).show() }
                    onPlaybackStateChanged = { state ->
                        view.findViewById<ImageView>(R.id.ivPlayPause).visibility =
                            if (state == com.google.android.exoplayer2.Player.STATE_READY) View.GONE else View.VISIBLE
                    }
                }
                player?.setSurface(surfaceView)
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) { player?.release(); player = null }
        })

        // 点击画面切换控制栏
        surfaceView.setOnClickListener { toggleControls(view) }
        showControls(view)
    }

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
        // 全屏时3秒后自动隐藏控制栏
        autoHideControls()
    }

    private fun exitFullscreen() {
        val activity = requireActivity()
        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        isFullscreen = false
        showControls(requireView())
    }

    private fun showControls(view: View) {
        view.findViewById<LinearLayout>(R.id.topBar).visibility = View.VISIBLE
        view.findViewById<LinearLayout>(R.id.bottomBar).visibility = View.VISIBLE
        // 取消之前的隐藏任务
        handler.removeCallbacksAndMessages(null)
        // 3秒后自动隐藏
        handler.postDelayed({
            view.findViewById<LinearLayout>(R.id.topBar).visibility = View.GONE
            view.findViewById<LinearLayout>(R.id.bottomBar).visibility = View.GONE
        }, 3000)
    }

    private fun autoHideControls() {
        val view = requireView()
        view.findViewById<LinearLayout>(R.id.topBar).visibility = View.VISIBLE
        view.findViewById<LinearLayout>(R.id.bottomBar).visibility = View.VISIBLE
        handler.removeCallbacksAndMessages(null)
        // 全屏时2秒后自动隐藏
        handler.postDelayed({
            view.findViewById<LinearLayout>(R.id.topBar).visibility = View.GONE
            view.findViewById<LinearLayout>(R.id.bottomBar).visibility = View.GONE
        }, 2000)
    }

    private fun toggleControls(view: View) {
        val topBar = view.findViewById<LinearLayout>(R.id.topBar)
        if (topBar.visibility == View.VISIBLE) {
            topBar.visibility = View.GONE
            view.findViewById<LinearLayout>(R.id.bottomBar).visibility = View.GONE
            handler.removeCallbacksAndMessages(null)
        } else {
            if (isFullscreen) autoHideControls() else showControls(view)
        }
    }

    private fun formatMs(ms: Long): String {
        if (ms <= 0) return "00:00"
        val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, sec) else String.format("%02d:%02d", m, sec)
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        player?.release(); player = null
        if (isFullscreen) exitFullscreen()
        super.onDestroyView()
    }
}
