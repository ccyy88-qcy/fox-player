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
import com.foxplayer.util.UrlResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FoxPlayer(private val context: Context) {

    private var trackSelector: DefaultTrackSelector
    private var exoPlayer: ExoPlayer
    var currentUrl: String = ""
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var onBuffering: ((Boolean) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onPlaybackStateChanged: ((Int) -> Unit)? = null

    init {
        val trackSelectionFactory = AdaptiveTrackSelection.Factory()
        trackSelector = DefaultTrackSelector(context, trackSelectionFactory)

        exoPlayer = ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(DefaultLoadControl.Builder().apply {
                // 增大缓冲: 初始10秒, 最大50秒, 重缓冲5秒
                setBufferDurationsMs(15_000, 60_000, 5_000, 10_000)
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
                val errorCode = error.errorCodeName
                val msg = error.message ?: "未知错误"
                val fullMsg = "$errorCode: $msg"

                // 对 PARSING_CONTAINER_UNSUPPORTED 做智能重试
                if (error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED) {
                    android.util.Log.e("FoxPlayer", "解码失败, 尝试解析实际URL: $currentUrl")
                    attemptSmartRetry()
                    return
                }

                android.util.Log.e("FoxPlayer", "播放错误: $errorCode url=$currentUrl", error)
                onError?.invoke(fullMsg)
            }
        })
    }

    /** 使用URL解析后播放 */
    fun play(url: String, userAgent: String = DEFAULT_UA) {
        currentUrl = cleanUrl(url)
        android.util.Log.d("FoxPlayer", "开始播放: $currentUrl")

        // 异步解析URL再播放
        scope.launch {
            val resolved = withContext(Dispatchers.IO) {
                UrlResolver.resolveUrl(currentUrl)
            }
            if (resolved != currentUrl) {
                android.util.Log.d("FoxPlayer", "URL已解析: $currentUrl → $resolved")
            }
            doPlay(resolved, userAgent)
        }
    }

    /** 实际播放 */
    private fun doPlay(url: String, userAgent: String) {
        currentUrl = url
        val uri = Uri.parse(url)

        val referer = try {
            val u = java.net.URL(url)
            "${u.protocol}://${u.host}/"
        } catch (_: Exception) { url }

        // 带Referer的DataSource — 来源请求防盗链
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)
            .setDefaultRequestProperties(mapOf(
                "Referer" to referer,
                "Origin" to getOrigin(url),
                "Accept" to "*/*",
            ))

        // 根据URL后缀自动探测格式
        val lower = url.lowercase()
        val mediaSource = when {
            lower.contains(".m3u8") -> {
                android.util.Log.d("FoxPlayer", "→ HLS")
                HlsMediaSource.Factory(dataSourceFactory)
                    .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(5))
                    .createMediaSource(MediaItem.fromUri(uri))
            }
            lower.contains(".mpd") -> {
                android.util.Log.d("FoxPlayer", "→ DASH")
                DashMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(uri))
            }
            lower.contains(".ism") -> {
                android.util.Log.d("FoxPlayer", "→ SmoothStreaming")
                SsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(uri))
            }
            else -> {
                android.util.Log.d("FoxPlayer", "→ Progressive (mp4/flv/ts)")
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(5))
                    .createMediaSource(MediaItem.fromUri(uri))
            }
        }

        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    /** 智能重试 — 解码容器不支持时尝试其他URL格式 */
    private fun attemptSmartRetry() {
        // 当前URL已经带/m3u8后缀了，尝试换种方式
        val url = currentUrl
        if (url.endsWith("/index.m3u8")) {
            // 尝试去掉 /index.m3u8 直接播放
            val base = url.removeSuffix("/index.m3u8")
            doPlay(base, DEFAULT_UA)
            android.util.Log.d("FoxPlayer", "重试: 去掉/index.m3u8 → $base")
        } else {
            // 尝试追加 /index.m3u8
            val withM3u8 = "${url.removeSuffix("/")}/index.m3u8"
            doPlay(withM3u8, DEFAULT_UA)
            android.util.Log.d("FoxPlayer", "重试: 追加/index.m3u8 → $withM3u8")
        }
    }

    private fun getOrigin(url: String): String {
        return try {
            val u = java.net.URL(url)
            "${u.protocol}://${u.host}"
        } catch (_: Exception) { url }
    }

    private fun cleanUrl(url: String): String {
        return url.replace("\\/", "/")
            .replace("\\u0026", "&")
            .trim()
            .let { if (it.startsWith("//")) "https:$it" else it }
    }

    fun setSurface(surfaceView: SurfaceView) {
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

    fun release() {
        scope.coroutineContext.javaClass // trigger cleanup
        exoPlayer.release()
    }

    companion object {
        const val DEFAULT_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36"
    }
}
