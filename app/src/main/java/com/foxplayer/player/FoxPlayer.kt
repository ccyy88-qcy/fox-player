package com.foxplayer.player

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.source.*
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy

class FoxPlayer(private val context: Context) {

    private var trackSelector: DefaultTrackSelector
    private var exoPlayer: ExoPlayer
    var currentUrl: String = ""

    var onBuffering: ((Boolean) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onPlaybackStateChanged: ((Int) -> Unit)? = null

    init {
        val trackSelectionFactory = AdaptiveTrackSelection.Factory()
        trackSelector = DefaultTrackSelector(context, trackSelectionFactory)

        exoPlayer = ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(DefaultLoadControl.Builder().apply {
                setBufferDurationsMs(10_000, 50_000, 1_000, 5_000)
            }.build())
            .setAudioAttributes(AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                onPlaybackStateChanged?.invoke(state)
                when (state) {
                    Player.STATE_BUFFERING -> onBuffering?.invoke(true)
                    Player.STATE_READY, Player.STATE_ENDED, Player.STATE_IDLE -> onBuffering?.invoke(false)
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                val msg = "${error.errorCodeName}: ${error.message}"
                android.util.Log.e("FoxPlayer", "error: $msg url=$currentUrl", error)
                onError?.invoke(msg)
            }
        })
    }

    fun play(url: String, userAgent: String = DEFAULT_UA) {
        currentUrl = cleanUrl(url)
        android.util.Log.d("FoxPlayer", "play: $currentUrl")

        val uri = Uri.parse(currentUrl)

        // DataSourceFactory — 加Referer防盗链
        val referer = try {
            val u = java.net.URL(currentUrl)
            "${u.protocol}://${u.host}/"
        } catch (_: Exception) { currentUrl }

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf(
                "Referer" to referer,
                "Accept" to "*/*",
            ))

        // 根据URL判断格式
        val mediaSource = when {
            currentUrl.contains(".m3u8") -> {
                android.util.Log.d("FoxPlayer", "→ HLS")
                HlsMediaSource.Factory(dataSourceFactory)
                    .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(3))
                    .createMediaSource(MediaItem.fromUri(uri))
            }
            currentUrl.contains(".mpd") -> {
                android.util.Log.d("FoxPlayer", "→ DASH")
                DashMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(uri))
            }
            currentUrl.contains(".ism") -> {
                android.util.Log.d("FoxPlayer", "→ SS")
                SsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(uri))
            }
            else -> {
                // MP4/FLV/TS等 — Progressive
                android.util.Log.d("FoxPlayer", "→ Progressive (mp4/flv/ts)")
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(3))
                    .createMediaSource(MediaItem.fromUri(uri))
            }
        }

        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    private fun cleanUrl(url: String): String {
        return url.replace("\\/", "/")
            .replace("\\u0026", "&")
            .trim()
            .let { if (it.startsWith("//")) "https:$it" else it }
    }

    fun setSurface(surfaceView: SurfaceView) {
        android.util.Log.d("FoxPlayer", "setSurface: ${surfaceView.width}x${surfaceView.height}")
        exoPlayer.setVideoSurface(surfaceView.holder.surface)
    }
    fun play() { exoPlayer.play() }
    fun pause() { exoPlayer.pause() }
    fun seekTo(ms: Long) { exoPlayer.seekTo(ms) }
    fun getCurrentPosition(): Long = exoPlayer.currentPosition
    fun getDuration(): Long = exoPlayer.duration
    fun isPlaying(): Boolean = exoPlayer.isPlaying

    fun setSpeed(speed: Float) {
        exoPlayer.setPlaybackParameters(PlaybackParameters(speed, 1f))
    }
    fun getSpeed(): Float = exoPlayer.playbackParameters.speed

    fun release() { exoPlayer.release() }

    companion object {
        const val DEFAULT_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36"
    }
}
