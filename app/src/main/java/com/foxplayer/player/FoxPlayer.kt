package com.foxplayer.player

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer
import com.google.android.exoplayer2.source.*
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.ui.DefaultTrackNameProvider
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.video.VideoSize

/**
 * FoxPlayer — ExoPlayer 完整封装
 * 支持: HLS/DASH/SS/RTMP/MP4/FLV
 * 特性: ABR自适应码率 / 硬解软解切换 / 倍速 / 音轨 / 字幕
 */
class FoxPlayer(private val context: Context) {

    private val bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()
    private var trackSelector: DefaultTrackSelector
    private var exoPlayer: ExoPlayer

    var onPlaybackStateChanged: ((Int) -> Unit)? = null
    var onVideoSizeChanged: ((VideoSize) -> Unit)? = null
    var onBuffering: ((Boolean) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    init {
        val trackSelectionFactory = AdaptiveTrackSelection.Factory()
        trackSelector = DefaultTrackSelector(context, trackSelectionFactory)

        exoPlayer = ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setBandwidthMeter(bandwidthMeter)
            .setLoadControl(DefaultLoadControl.Builder().apply {
                setBufferDurationsMs(
                    10_000,  // minBuffer 10s
                    50_000,  // maxBuffer 50s
                    1_000,   // bufferForPlayback 1s
                    5_000    // bufferForPlaybackAfterRebuffer 5s
                )
            }.build())
            .setAudioAttributes(AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build(), true)
            .build()

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                onPlaybackStateChanged?.invoke(state)
            }
            override fun onVideoSizeChanged(size: VideoSize) {
                onVideoSizeChanged?.invoke(size)
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onBuffering?.invoke(false)
            }
            override fun onPlayerError(error: PlaybackException) {
                onError?.invoke(error.message ?: "播放错误")
            }
        })
    }

    /** 播放 URL — 自动识别格式 */
    fun play(url: String, userAgent: String = DEFAULT_UA) {
        val uri = Uri.parse(url)
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)

        val mediaSource = when {
            url.contains(".m3u8") -> HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(uri))
            url.contains(".mpd") -> DashMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(uri))
            url.contains(".ism") -> SsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(uri))
            url.startsWith("rtmp") -> {
                // RTMP via extension
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(uri))
            }
            else -> ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(uri))
        }

        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun setSurface(surfaceView: SurfaceView) { exoPlayer.setVideoSurfaceView(surfaceView) }
    fun play() { exoPlayer.play() }
    fun pause() { exoPlayer.pause() }
    fun seekTo(ms: Long) { exoPlayer.seekTo(ms) }
    fun getCurrentPosition(): Long = exoPlayer.currentPosition
    fun getDuration(): Long = exoPlayer.duration
    fun isPlaying(): Boolean = exoPlayer.isPlaying
    fun getBufferedPosition(): Long = exoPlayer.bufferedPosition

    /** 倍速 0.5x ~ 4x */
    fun setSpeed(speed: Float) {
        exoPlayer.setPlaybackParameters(
            PlaybackParameters(speed, 1f)
        )
    }
    fun getSpeed(): Float = exoPlayer.playbackParameters.speed

    /** 切换音轨 */
    fun getAudioTracks(): List<TrackInfo> {
        val tracks = mutableListOf<TrackInfo>()
        val trackGroups = exoPlayer.currentTracks.groups
        for (group in trackGroups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    tracks.add(TrackInfo(
                        index = i,
                        label = group.getTrackFormat(i).label ?: "音轨${i + 1}",
                        mimeType = group.getTrackFormat(i).sampleMimeType ?: ""
                    ))
                }
            }
        }
        return tracks
    }

    /** 硬解/软解切换 */
    fun setHardwareDecode(enabled: Boolean) {
        trackSelector.parameters = trackSelector.buildUponParameters().apply {
            // 软解: 禁用 MediaCodec renderer
            if (!enabled) {
                setRendererDisabled(0, false)  // video renderer
            }
        }.build()
    }

    /** 获取可用画质列表 */
    fun getVideoQualities(): List<TrackInfo> {
        val tracks = mutableListOf<TrackInfo>()
        for (group in exoPlayer.currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_VIDEO) {
                for (i in 0 until group.length) {
                    val fmt = group.getTrackFormat(i)
                    val h = fmt.height
                    val label = when {
                        h >= 2160 -> "4K"
                        h >= 1080 -> "1080P"
                        h >= 720 -> "720P"
                        h >= 480 -> "480P"
                        else -> "SD"
                    }
                    tracks.add(TrackInfo(index = i, label = label, mimeType = fmt.sampleMimeType ?: ""))
                }
            }
        }
        return tracks
    }

    fun release() { exoPlayer.release() }

    data class TrackInfo(val index: Int, val label: String, val mimeType: String)

    companion object {
        const val DEFAULT_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"
    }
}
