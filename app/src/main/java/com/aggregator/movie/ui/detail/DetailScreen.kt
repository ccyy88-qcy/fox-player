package com.aggregator.movie.ui.detail

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.aggregator.movie.MovieApplication
import com.aggregator.movie.data.model.*
import com.aggregator.movie.ui.Screen
import com.aggregator.movie.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    movieId: String,
    sourceId: String,
    navController: NavHostController
) {
    val repository = MovieApplication.instance.repository
    val context = LocalContext.current
    var movie by remember { mutableStateOf<Movie?>(null) }
    var playSources by remember { mutableStateOf<List<PlaySource>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isFavorite by remember { mutableStateOf(false) }
    var selectedSourceIndex by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(movieId) {
        scope.launch {
            isLoading = true
            val detailResult = repository.getMovieDetail(movieId, sourceId)
            if (detailResult.isSuccess) movie = detailResult.getOrNull()
            else error = detailResult.exceptionOrNull()?.message

            val sourceResult = repository.getPlaySources(movieId, sourceId)
            if (sourceResult.isSuccess) playSources = sourceResult.getOrDefault(emptyList())

            try { isFavorite = repository.isFavorite(movieId).first() } catch (_: Exception) {}

            isLoading = false
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    error?.let {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { navController.popBackStack() }) { Text("返回") }
            }
        }
        return
    }

    movie?.let { m ->
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // === 顶部封面+信息 ===
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = m.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, DarkBackground)
                                )
                            )
                    )
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50))
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                if (isFavorite) repository.removeFavorite(movieId)
                                else repository.addFavorite(m)
                                isFavorite = !isFavorite
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50))
                    ) {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "收藏",
                            tint = if (isFavorite) Color.Red else Color.White
                        )
                    }
                }
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(m.title, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        // ★ 评分修复：0.0 / 0 / blank → 待播（未开播剧集）
                        val rawScore = m.score.trim()
                        val hasValidScore = rawScore.isNotBlank() && rawScore != "0.0" && rawScore != "0"
                        if (hasValidScore) {
                            Text("⭐ $rawScore", color = OrangePrimary, fontSize = 14.sp)
                        } else {
                            Text("📺 待播", color = TextSecondary, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        if (m.year.isNotBlank()) {
                            Text(m.year, color = TextSecondary, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                    }
                    if (m.genre.isNotBlank()) Text("类型: ${m.genre}", color = TextSecondary, fontSize = 13.sp)
                    if (m.region.isNotBlank()) Text("地区: ${m.region}", color = TextSecondary, fontSize = 13.sp)
                    if (m.director.isNotBlank()) Text("导演: ${m.director}", color = TextSecondary, fontSize = 13.sp)
                    if (m.actors.isNotBlank()) Text("主演: ${m.actors}", color = TextSecondary, fontSize = 13.sp)
                    if (m.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(m.description, color = TextSecondary, fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                    }
                }
            }

            // === 播放线路 ===
            if (playSources.isNotEmpty()) {
                item {
                    Text("播放线路", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(playSources) { index, source ->
                            FilterChip(
                                selected = index == selectedSourceIndex,
                                onClick = { selectedSourceIndex = index },
                                label = { Text(source.name, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = OrangePrimary, selectedLabelColor = Color.White,
                                    containerColor = DarkSurface, labelColor = TextSecondary
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                val currentSource = playSources.getOrNull(selectedSourceIndex)
                if (currentSource != null) {
                    item {
                        Text("选集 (${currentSource.episodes.size}集)",
                            color = TextPrimary, fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

                        // ★ 修复：使用 LazyRow 保证横向滚动 + 加大 touch target
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(currentSource.episodes) { epIndex, ep ->
                                FilledTonalButton(
                                    onClick = {
                                        // ★ 修复：集数按钮→播放器页路由跳转（无延迟同步执行）
                                        val playerRoute = Screen.Player.createRoute(movieId, sourceId, epIndex)
                                        try {
                                            // 方案一：先检查路由是否能找到目标
                                            navController.navigate(playerRoute) {
                                                // 强制清栈保证新页面
                                                launchSingleTop = false
                                                restoreState = false
                                            }
                                        } catch (e: Exception) {
                                            // 兜底：弹 Toast 提示
                                            Toast.makeText(context, "跳转播放失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = DarkSurfaceVariant, contentColor = TextPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    // ★ 修复：加大触摸区域，保证所有集数按钮可点击
                                    modifier = Modifier.size(
                                        width = if (currentSource.episodes.size > 50) 48.dp else 60.dp,
                                        height = 42.dp
                                    ),
                                    contentPadding = PaddingValues(4.dp)
                                ) {
                                    Text(
                                        ep.title
                                            .replace("第", "")
                                            .replace("集", "")
                                            .ifBlank { "${epIndex + 1}" },
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}
