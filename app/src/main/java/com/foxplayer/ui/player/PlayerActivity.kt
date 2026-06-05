package com.foxplayer.ui.player

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.foxplayer.R
import com.foxplayer.databinding.ActivityPlayerBinding
import com.foxplayer.player.FoxPlayer
import com.foxplayer.player.GestureController
import com.foxplayer.player.PipHelper

class PlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlayerBinding
    private var player: FoxPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val url = intent.getStringExtra("url") ?: return
        val title = intent.getStringExtra("title") ?: ""
        binding.tvTitle.text = title

        player = FoxPlayer(this).apply {
            play(url)
            onBuffering = { binding.progressBar.visibility = if (it) View.VISIBLE else View.GONE }
            onError = { msg -> binding.tvTitle.text = "错误: $msg" }
        }

        binding.ivPlayPause.setOnClickListener {
            player?.let { p -> if (p.isPlaying()) p.pause() else p.play() }
            binding.ivPlayPause.visibility = View.GONE
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        PipHelper.enterPip(this)
    }

    override fun onDestroy() {
        player?.release()
        super.onDestroy()
    }
}
