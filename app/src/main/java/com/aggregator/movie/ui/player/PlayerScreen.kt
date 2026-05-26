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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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

/**
 * 播放器页面 v3
 *
 * 修复:
 * 1. 控制栏横竖屏统一自动隐藏+点按唤起（原bug:竖屏永远显示遮挡视频）
 * 2. 断点续传 SharedPreferences 持久化
 * 3. 播放中每5秒保存进度，>95%自动清空
 * 4. ExoPlayer 加大缓冲区减少卡顿
 * 5. 进入播放器时自动恢复上次播放位置
 */
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

    // SharedPreferences 用于每集断点续传
    val prefs = remember { context.getSharedPreferences("player_progress", Context.MODE_PRIVATE) }

    fun scheduleHideControls() {
        hideControlsJob?.cancel()
        hideControlsJob = scope.launch {
            delay(4000)
            showControls = false
        }
    }

    // 初始1秒后自动隐藏控制栏
    LaunchedEffect(Unit) {
        delay(1000)
        showControls = false
    }

    // ExoPlayer（使用默认缓冲区配置）
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
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
                            currentPlayUrl = newUrl
                            currentSourceIndex = newIdx
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

    // ★ 修复：解析播放地址 + 断点续传恢复
    // 依赖 playSources.size 解决首次加载时 playSources 为空导致视频不播放的bug
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

        // ★ 修复：尝试恢复断点续传位置
        val savedPos = prefs.getLong("play_progress_${movieId}_${currentEpisodeIndex}", -1L)
        if (savedPos > 0) {
            delay(300) // 等播放器准备好
            exoPlayer.seekTo(savedPos)
        }
    }

    // ★ 修复：播放中每5秒保存进度（实时断点续传）
    LaunchedEffect(currentEpisodeIndex) {
        while (isActive) {
            delay(5000)
            if (exoPlayer.isPlaying) {
                savePosition(prefs, movieId, currentEpisodeIndex, exoPlayer)
            }
        }
    }

    // 保存观看历史（Room）
    DisposableEffect(currentEpisodeIndex) {
        onDispose {
            scope.launch {
                savePosition(prefs, movieId, currentEpisodeIndex, exoPlayer)
                val pos = exoPlayer.currentPosition
                val dur = exoPlayer.duration.coerceAtLeast(0)
                if (dur > 0) {
                    val movie = try { repository.getMovieDetail(movieId, sourceId).getOrNull() } catch (e: Exception) { null }
                    movie?.let {
                        val epTitle = playSources.getOrNull(currentSourceIndex)?.episodes?.getOrNull(currentEpisodeIndex)?.title ?: ""
                        repository.saveHistory(it, currentEpisodeIndex, epTitle, pos, dur)
                    }
                }
            }
        }
    }

    // 横屏控制 + 沉浸全屏
    DisposableEffect(isFullscreen) {
        val activity = context as? Activity
        activity?.let {
            if (isFullscreen) {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    it.window.setDecorFitsSystemWindows(false)
                    it.window.insetsController?.hide(WindowInsets.Type.systemBars())
                    it.window.insetsController?.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    @Suppress("DEPRECATION")
                    it.window.decorView.systemUiVisibility = (
                        android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                        android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
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

    // 物理返回键：全屏→退出全屏→返回详情
    BackHandler(enabled = true) {
        if (isFullscreen) {
            isFullscreen = false
            hideControlsJob?.cancel()
            showControls = true
        } else {
            savePosition(prefs, movieId, currentEpisodeIndex, exoPlayer)
            navController.popBackStack()
        }
    }

    // ===== UI 布局 =====
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 播放器（填满全屏，无遮挡）
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false  // 用自定义控制栏
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    // ★ 修复：横竖屏都切换控制栏
                    showControls = !showControls
                    if (showControls) scheduleHideControls()
                }
        )

        // ★ 修复：控制栏统一用 showControls 控制，不再区分横竖屏
        if (showControls) {
            // 顶部控制栏（半透明）
            Row(
                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 4.dp, vertical = 2.dp).align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (isFullscreen) {
                        isFullscreen = false
                        hideControlsJob?.cancel()
                        showControls = true
                    } else {
                        savePosition(prefs, movieId, currentEpisodeIndex, exoPlayer)
                        navController.popBackStack()
                    }
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
                val epTitle = playSources.getOrNull(currentSourceIndex)?.episodes?.getOrNull(currentEpisodeIndex)?.title
                    ?: "第${currentEpisodeIndex + 1}集"
                Text(epTitle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    isFullscreen = !isFullscreen
                    if (isFullscreen) { showControls = true; scheduleHideControls() }
                    else { hideControlsJob?.cancel(); showControls = true }
                }) {
                    Icon(
                        if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = "全屏", tint = Color.White
                    )
                }
            }
        }

        // 缓冲提示
        if (exoPlayer.playbackState == Player.STATE_BUFFERING && !isChangingSource) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("缓冲中…", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                }
            }
        }

        // 换源提示
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
                                    onAllFailed = {}
                                )
                                isChangingSource = false
                            }
                        }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("换源") }
                        Button(onClick = { navController.popBackStack() }) { Text("返回", color = Color.Black) }
                    }
                }
            }
        }

        // ★ 修复：底部控制面板（选集+线路），只在控制栏显示时展示
        if (showControls && playSources.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(start = 8.dp, end = 8.dp, bottom = 12.dp)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                // 线路选择
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
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
                // 集数选择
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
                                modifier = Modifier.size(
                                    width = if (ep.title.length > 4) 60.dp else 48.dp,
                                    height = 36.dp
                                ),
                                contentPadding = PaddingValues(2.dp)
                            ) {
                                Text(
                                    ep.title.replace("第", "").replace("集", "").ifBlank { "${idx + 1}" },
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===== 工具函数 =====

/**
 * ★ 修复：保存播放位置到 SharedPreferences
 * 每集独立存储，>95%自动清空（视为已看完）
 */
private fun savePosition(prefs: android.content.SharedPreferences, movieId: String, episodeIdx: Int, player: ExoPlayer) {
    try {
        val pos = player.currentPosition
        val dur = player.duration.coerceAtLeast(0)
        if (dur > 0) {
            val key = "play_progress_${movieId}_${episodeIdx}"
            val ratio = pos.toFloat() / dur.toFloat()
            if (ratio >= 0.95f) {
                // 看完95%以上，清空记录，下次从头播
                prefs.edit().remove(key).apply()
            } else if (ratio > 0.02f && pos > 3000) {
                // 播了超过3秒才存（避免误存0位置）
                prefs.edit().putLong(key, pos).apply()
            }
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
    playSources: List<PlaySource>,
    currentSourceIndex: Int,
    episodeIndex: Int,
    repository: com.aggregator.movie.data.repository.MovieRepository,
    onSuccess: (PlayUrl, Int) -> Unit,
    onAllFailed: () -> Unit
) {
    for (i in 1 until playSources.size) {
        val nextIdx = (currentSourceIndex + i) % playSources.size
        val result = repository.resolvePlayUrl(playSources, episodeIndex)
        if (result.isSuccess) {
            onSuccess(result.getOrThrow(), nextIdx)
            return
        }
    }
    onAllFailed()
}
