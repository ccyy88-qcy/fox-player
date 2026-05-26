package com.aggregator.movie.ui.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Build
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import com.aggregator.movie.MovieApplication
import com.aggregator.movie.data.model.PlaySource
import com.aggregator.movie.data.model.PlayUrl
import com.aggregator.movie.ui.theme.OrangePrimary
import kotlin.contracts.ExperimentalContracts
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun PlayerScreen(
    movieId: String,
    sourceId: String,
    episodeIndex: Int,
    navController: NavHostController
) {
    val repository = MovieApplication.instance.repository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var playSources by remember { mutableStateOf<List<PlaySource>>(emptyList()) }
    var currentEpisodeIndex by remember { mutableIntStateOf(episodeIndex) }
    var currentPlayUrl by remember { mutableStateOf<PlayUrl?>(null) }
    var currentSourceIndex by remember { mutableIntStateOf(0) }
    var isChangingSource by remember { mutableStateOf(false) }
    var playError by remember { mutableStateOf<String?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var hideControlsJob by remember { mutableStateOf<Job?>(null) }
    // 播放进度
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) } // 拖拽中不隐藏控制栏

    val prefs = remember { context.getSharedPreferences("player_progress", Context.MODE_PRIVATE) }

    fun scheduleHideControls() {
        if (isSeeking) return // 拖拽中不隐藏
        hideControlsJob?.cancel()
        hideControlsJob = scope.launch {
            delay(4000)
            showControls = false
        }
    }

    // 初始1秒后自动隐藏
    LaunchedEffect(Unit) {
        delay(1000)
        showControls = false
    }

    // ExoPlayer（大缓冲区减少卡顿）
    val exoPlayer = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30000,    // 最小缓冲: 30秒
                90000,    // 最大缓冲: 90秒
                3000,     // 开始播放前缓冲: 3秒
                5000      // 卡顿后重新缓冲: 5秒
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }

    // 播放错误监听 → 自动换源
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                scope.launch {
                    isChangingSource = true
                    tryNextSource(playSources, currentSourceIndex, currentEpisodeIndex, repository,
                        onSuccess = { newUrl, newIdx ->
                            currentPlayUrl = newUrl; currentSourceIndex = newIdx
                            playExo(exoPlayer, newUrl)
                        },
                        onAllFailed = { playError = "所有线路均无法播放" }
                    )
                    isChangingSource = false
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) playError = null
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            savePosition(prefs, movieId, currentEpisodeIndex, exoPlayer)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // 加载播放源
    LaunchedEffect(movieId) {
        repository.getPlaySources(movieId, sourceId).fold(
            onSuccess = { playSources = it },
            onFailure = { playError = "加载播放源失败" }
        )
    }

    // 解析播放地址 + 断点续传
    LaunchedEffect(currentEpisodeIndex, currentSourceIndex, playSources.size) {
        if (playSources.isEmpty()) return@LaunchedEffect
        val source = playSources.getOrNull(currentSourceIndex) ?: return@LaunchedEffect
        val episode = source.episodes.getOrNull(currentEpisodeIndex) ?: return@LaunchedEffect
        playError = null

        if (episode.url.endsWith(".mp4") || episode.url.endsWith(".m3u8") || episode.url.endsWith(".flv")) {
            currentPlayUrl = PlayUrl(url = episode.url, headers = mapOf("Referer" to "https://www.zuidapi.com/"))
            playExo(exoPlayer, currentPlayUrl!!)
        } else {
            repository.resolvePlayUrl(playSources, currentEpisodeIndex).fold(
                onSuccess = { url -> currentPlayUrl = url; playExo(exoPlayer, url) },
                onFailure = {
                    isChangingSource = true
                    tryNextSource(playSources, currentSourceIndex, currentEpisodeIndex, repository,
                        onSuccess = { url, idx -> currentPlayUrl = url; currentSourceIndex = idx; playExo(exoPlayer, url) },
                        onAllFailed = { playError = "所有线路均无法播放" }
                    )
                    isChangingSource = false
                }
            )
        }

        val savedPos = prefs.getLong("play_progress_${movieId}_${currentEpisodeIndex}", -1L)
        if (savedPos > 0) {
            delay(300)
            exoPlayer.seekTo(savedPos)
        }
    }

    // ★ 播放进度实时更新（每200ms）
    LaunchedEffect(Unit) {
        while (isActive) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0)
            delay(200)
        }
    }

    // 每5秒保存进度
    LaunchedEffect(currentEpisodeIndex) {
        while (isActive) {
            delay(5000)
            if (exoPlayer.isPlaying) savePosition(prefs, movieId, currentEpisodeIndex, exoPlayer)
        }
    }

    // 保存历史
    DisposableEffect(currentEpisodeIndex) {
        onDispose {
            scope.launch {
                savePosition(prefs, movieId, currentEpisodeIndex, exoPlayer)
                val pos = exoPlayer.currentPosition; val dur = exoPlayer.duration.coerceAtLeast(0)
                if (dur > 0) {
                    val movie = try { repository.getMovieDetail(movieId, sourceId).getOrNull() } catch (_: Exception) { null }
                    movie?.let {
                        val epTitle = playSources.getOrNull(currentSourceIndex)?.episodes?.getOrNull(currentEpisodeIndex)?.title ?: ""
                        repository.saveHistory(it, currentEpisodeIndex, epTitle, pos, dur)
                    }
                }
            }
        }
    }

    // 横屏控制
    DisposableEffect(isFullscreen) {
        val activity = context as? Activity
        activity?.let {
            if (isFullscreen) {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    it.window.setDecorFitsSystemWindows(false)
                    it.window.insetsController?.hide(WindowInsets.Type.systemBars())
                    it.window.insetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    @Suppress("DEPRECATION")
                    it.window.decorView.systemUiVisibility = (
                        android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    )
                }
            } else {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                it.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    it.window.setDecorFitsSystemWindows(true)
                    it.window.insetsController?.show(WindowInsets.Type.systemBars())
                } else {
                    @Suppress("DEPRECATION")
                    it.window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
                }
            }
        }
        onDispose {
            (context as? Activity)?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    act.window.setDecorFitsSystemWindows(true)
                    act.window.insetsController?.show(WindowInsets.Type.systemBars())
                } else {
                    @Suppress("DEPRECATION")
                    act.window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
                }
            }
        }
    }

    // 物理返回键
    BackHandler(enabled = true) {
        if (isFullscreen) {
            isFullscreen = false; hideControlsJob?.cancel(); showControls = true
        } else {
            savePosition(prefs, movieId, currentEpisodeIndex, exoPlayer); navController.popBackStack()
        }
    }

    // ============================
    // UI 布局
    // ============================
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 播放器
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    showControls = !showControls
                    if (showControls) { isSeeking = false; scheduleHideControls() }
                }
        )

        // 控制层（showControls 统一控制，横竖屏一致）
        if (showControls) {
            // === 顶部：返回+标题+全屏 ===
            Row(
                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 4.dp, vertical = 2.dp).align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (isFullscreen) { isFullscreen = false; hideControlsJob?.cancel(); showControls = true }
                    else { savePosition(prefs, movieId, currentEpisodeIndex, exoPlayer); navController.popBackStack() }
                }) { Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White) }
                val epTitle = playSources.getOrNull(currentSourceIndex)?.episodes?.getOrNull(currentEpisodeIndex)?.title
                    ?: "第${currentEpisodeIndex + 1}集"
                Text(epTitle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    isFullscreen = !isFullscreen
                    if (isFullscreen) { showControls = true; scheduleHideControls() }
                    else { hideControlsJob?.cancel(); showControls = true }
                }) { Icon(if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, tint = Color.White, contentDescription = "全屏") }
            }

            // === 底部：播放控制栏 + 选集 ===
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                // ---- 播放进度条 ----
                val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 播放/暂停
                    IconButton(onClick = {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        scheduleHideControls()
                    }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            if (exoPlayer.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (exoPlayer.isPlaying) "暂停" else "播放",
                            tint = Color.White, modifier = Modifier.size(28.dp)
                        )
                    }
                    // 当前时间
                    Text(formatTime(currentPosition), color = Color.White, fontSize = 11.sp,
                        modifier = Modifier.width(42.dp), textAlign = TextAlign.Center)
                    // 进度条
                    Slider(
                        value = if (duration > 0) progress else 0f,
                        onValueChange = { fraction ->
                            isSeeking = true
                            val seekPos = (fraction * duration).toLong()
                            exoPlayer.seekTo(seekPos)
                        },
                        onValueChangeFinished = {
                            isSeeking = false
                            scheduleHideControls()
                        },
                        modifier = Modifier.weight(1f).height(28.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = OrangePrimary,
                            activeTrackColor = OrangePrimary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                    // 总时长
                    Text(formatTime(duration), color = Color.White, fontSize = 11.sp,
                        modifier = Modifier.width(42.dp), textAlign = TextAlign.Center)
                }

                // ---- 线路选择 ----
                if (playSources.size > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(playSources) { idx, source ->
                            FilterChip(
                                selected = idx == currentSourceIndex,
                                onClick = { currentSourceIndex = idx },
                                label = { Text(source.name, fontSize = 11.sp, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = OrangePrimary, selectedLabelColor = Color.White,
                                    containerColor = Color.DarkGray, labelColor = Color.LightGray
                                )
                            )
                        }
                    }
                }

                // ---- 集数选择 ----
                val curSrc = playSources.getOrNull(currentSourceIndex)
                if (curSrc != null && curSrc.episodes.size > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(curSrc.episodes) { idx, ep ->
                            FilledTonalButton(
                                onClick = { currentEpisodeIndex = idx },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (idx == currentEpisodeIndex) OrangePrimary else Color.DarkGray,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.size(width = if (ep.title.length > 4) 60.dp else 48.dp, height = 34.dp),
                                contentPadding = PaddingValues(2.dp)
                            ) { Text(ep.title.replace("第", "").replace("集", "").ifBlank { "${idx + 1}" }, fontSize = 11.sp) }
                        }
                    }
                }
            }
        }

        // 缓冲中
        if (exoPlayer.playbackState == Player.STATE_BUFFERING && !isChangingSource) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("缓冲中…", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                }
            }
        }

        // 换源中
        if (isChangingSource) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = OrangePrimary, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("自动换源中…", color = Color.White, fontSize = 13.sp)
                }
            }
        }

        // 错误提示
        if (playError != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(playError!!, color = Color.White, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                isChangingSource = true
                                tryNextSource(playSources, currentSourceIndex, currentEpisodeIndex, repository,
                                    onSuccess = { url, idx -> currentPlayUrl = url; currentSourceIndex = idx; playExo(exoPlayer, url) },
                                    onAllFailed = {})
                                isChangingSource = false
                            }
                        }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("换源") }
                        Button(onClick = { navController.popBackStack() }) { Text("返回", color = Color.Black) }
                    }
                }
            }
        }
    }
}

// ===== 工具函数 =====

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val total = ms / 1000
    return String.format(Locale.CHINA, "%02d:%02d", total / 60, total % 60)
}

private fun savePosition(prefs: android.content.SharedPreferences, movieId: String, episodeIdx: Int, player: ExoPlayer) {
    try {
        val pos = player.currentPosition; val dur = player.duration.coerceAtLeast(0)
        if (dur > 0) {
            val key = "play_progress_${movieId}_${episodeIdx}"
            val ratio = pos.toFloat() / dur.toFloat()
            if (ratio >= 0.95f) prefs.edit().remove(key).apply()
            else if (ratio > 0.02f && pos > 3000) prefs.edit().putLong(key, pos).apply()
        }
    } catch (_: Exception) {}
}

private fun playExo(player: ExoPlayer, playUrl: PlayUrl) {
    val builder = MediaItem.Builder().setUri(playUrl.url)
    if (playUrl.headers.isNotEmpty()) {
        val extras = Bundle()
        playUrl.headers.forEach { (k, v) -> extras.putString(k, v) }
        builder.setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(extras).build())
    }
    player.setMediaItem(builder.build())
    player.prepare()
    player.playWhenReady = true
}

@OptIn(ExperimentalContracts::class)
private suspend fun tryNextSource(
    playSources: List<PlaySource>, currentSourceIndex: Int, episodeIndex: Int,
    repository: com.aggregator.movie.data.repository.MovieRepository,
    onSuccess: (PlayUrl, Int) -> Unit, onAllFailed: () -> Unit
) {
    for (i in 1 until playSources.size) {
        val nextIdx = (currentSourceIndex + i) % playSources.size
        val result = repository.resolvePlayUrl(playSources, episodeIndex)
        if (result.isSuccess) { onSuccess(result.getOrThrow(), nextIdx); return }
    }
    onAllFailed()
}
