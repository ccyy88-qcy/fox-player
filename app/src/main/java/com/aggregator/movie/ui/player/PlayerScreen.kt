package com.aggregator.movie.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.compose.foundation.background
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
import kotlinx.coroutines.launch

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
    
    // ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
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
    
    // 解析播放地址
    LaunchedEffect(currentEpisodeIndex, currentSourceIndex) {
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
    }
    
    // 保存观看历史
    DisposableEffect(Unit) {
        onDispose {
            scope.launch {
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
    
    // 横屏控制
    DisposableEffect(isFullscreen) {
        val activity = context as? Activity
        activity?.let {
            if (isFullscreen) {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                it.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        onDispose {
            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            (context as? Activity)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 播放器
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // 顶部控制栏
        Row(
            modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(8.dp).align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
            }
            val epTitle = playSources.getOrNull(currentSourceIndex)?.episodes?.getOrNull(currentEpisodeIndex)?.title ?: "第${currentEpisodeIndex + 1}集"
            Text(epTitle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { isFullscreen = !isFullscreen }) {
                Icon(if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, contentDescription = "全屏", tint = Color.White)
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
        
        // 底部选集面板
        if (!isFullscreen && playSources.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.85f)).padding(8.dp)
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
                        itemsIndexed(curSrc.episodes) { idx, _ ->
                            FilledTonalButton(
                                onClick = { currentEpisodeIndex = idx },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (idx == currentEpisodeIndex) OrangePrimary else Color.DarkGray,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.size(width = 48.dp, height = 36.dp),
                                contentPadding = PaddingValues(2.dp)
                            ) { Text("${idx + 1}", fontSize = 11.sp) }
                        }
                    }
                }
            }
        }
    }
}

// ===== 工具函数 =====

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
        repository.resolvePlayUrl(playSources, episodeIndex).fold(
            onSuccess = { onSuccess(it, nextIdx) },
            onFailure = { continue }
        )
        return
    }
    onAllFailed()
}
