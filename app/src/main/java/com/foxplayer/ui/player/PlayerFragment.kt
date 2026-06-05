package com.foxplayer.ui.player

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.foxplayer.R
import com.foxplayer.databinding.FragmentPlayerBinding
import com.foxplayer.player.FoxPlayer
import com.foxplayer.player.GestureController
import com.foxplayer.player.PipHelper
import com.foxplayer.util.toast

class PlayerFragment : Fragment(R.layout.fragment_player) {
    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!
    private val args: PlayerFragmentArgs by navArgs()
    private var player: FoxPlayer? = null
    private var gesture: GestureController? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentPlayerBinding.bind(view)
        binding.tvTitle.text = args.videoTitle

        // 初始化 ExoPlayer
        player = FoxPlayer(requireContext()).apply {
            play(args.videoId)
            onBuffering = { buffering ->
                binding.progressBar.visibility = if (buffering) View.VISIBLE else View.GONE
            }
            onError = { msg -> requireContext().toast(msg) }
            onPlaybackStateChanged = { state ->
                if (state == android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING) {
                    binding.ivPlayPause.visibility = View.GONE
                }
            }
        }

        // 手势控制
        gesture = GestureController(
            context = requireContext(),
            player = player!!,
            onBrightChange = { /* update brightness overlay */ },
            onVolumeChange = { /* update volume overlay */ },
            onProgressChange = { pos, text -> binding.tvProgress.text = text },
            onSpeedChange = { speed -> binding.tvSpeed.text = "${speed}x" },
        )
        binding.root.setOnTouchListener { _, e -> gesture?.onTouchEvent(e); true }

        // 播放/暂停按钮
        binding.ivPlayPause.setOnClickListener {
            player?.let { p -> if (p.isPlaying()) p.pause() else p.play() }
            binding.ivPlayPause.visibility = View.GONE
        }

        // 倍速按钮
        binding.tvSpeed.setOnClickListener {
            val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f)
            val current = player?.getSpeed() ?: 1f
            val next = speeds[(speeds.indexOf(current) + 1) % speeds.size]
            player?.setSpeed(next)
            binding.tvSpeed.text = "${next}x"
        }

        // 画中画
        binding.ivPip.setOnClickListener {
            PipHelper.enterPip(requireActivity() as androidx.appcompat.app.AppCompatActivity)
        }
    }

    override fun onDestroyView() {
        player?.release()
        player = null
        _binding = null
        super.onDestroyView()
    }
}
