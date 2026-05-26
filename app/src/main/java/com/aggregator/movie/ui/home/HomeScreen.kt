package com.aggregator.movie.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.aggregator.movie.MovieApplication
import com.aggregator.movie.data.model.HomeData
import com.aggregator.movie.data.model.Movie
import com.aggregator.movie.data.model.WatchHistoryEntity
import com.aggregator.movie.ui.Screen
import com.aggregator.movie.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

val categoryTabs = listOf("首页", "电影", "连续剧", "综艺", "动漫", "短剧")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavHostController) {
    val repository = MovieApplication.instance.repository
    var homeData by remember { mutableStateOf<HomeData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showContinueBar by remember { mutableStateOf(true) }
    var continueHistory by remember { mutableStateOf<WatchHistoryEntity?>(null) }

    // 加载数据
    LaunchedEffect(Unit) {
        isLoading = true
        repository.getHomeData().fold(
            onSuccess = { homeData = it },
            onFailure = {}
        )

        // 加载续看历史（用first()一次性获取，不用collect持续监听）
        try {
            continueHistory = repository.getWatchHistory().first().firstOrNull()
        } catch (_: Exception) {}

        isLoading = false
    }

    Box(modifier = Modifier.fillMaxSize().background(LightBg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // === 顶部：搜索栏 ===
            item {
                Surface(
                    color = LightSurface,
                    tonalElevation = 0.dp,
                    shadowElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 搜索框
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text("搜索名称 简介", color = TextGray, fontSize = 14.sp)
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = TextGray, modifier = Modifier.size(20.dp))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RedPrimary,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = LightBg,
                                unfocusedContainerColor = LightBg,
                                cursorColor = RedPrimary
                            ),
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        // 刷新
                        IconButton(onClick = {
                            isLoading = true
                            kotlinx.coroutines.MainScope().launch {
                                repository.getHomeData().fold(
                                    onSuccess = { homeData = it },
                                    onFailure = {}
                                )
                                isLoading = false
                            }
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = TextGray, modifier = Modifier.size(20.dp))
                        }
                        // 历史
                        IconButton(onClick = {
                            navController.navigate(Screen.History.route)
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.History, contentDescription = "历史", tint = TextGray, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // === 分类标签栏 ===
            item {
                Surface(color = LightSurface) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(categoryTabs) { idx, name ->
                            TabItem(
                                name = name,
                                selected = idx == selectedTab,
                                onClick = { selectedTab = idx }
                            )
                        }
                    }
                }
            }

            // === Banner轮播 ===
            homeData?.banners?.let { banners ->
                if (banners.isNotEmpty()) {
                    item {
                        BannerCarousel(banners = banners, navController = navController)
                    }
                }
            }

            // === 当前热播标题 ===
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 20.dp, bottom = 8.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("当前热播", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark)
                    TextButton(onClick = { navController.navigate("category") }) {
                        Text("更多", color = RedPrimary, fontSize = 13.sp)
                    }
                }
            }

            // === 双列网格卡片 ===
            val displayMovies = homeData?.hotMovies?.take(10) ?: emptyList()
            if (displayMovies.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        displayMovies.chunked(2).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                row.forEach { movie ->
                                    MovieCardLight(
                                        movie = movie,
                                        onClick = {
                                            navController.navigate(Screen.Detail.createRoute(movie.id, movie.sourceId))
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (row.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }
                }
            } else if (!isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("暂无数据", color = TextGray, fontSize = 14.sp)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        // === 底部续看条 ===
        if (showContinueBar && continueHistory != null) {
            val h = continueHistory!!
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .clickable {
                        navController.navigate(Screen.Player.createRoute(h.movieId, h.sourceId, h.episodeIndex))
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = LightSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("继续观看", color = TextGray, fontSize = 11.sp)
                        Text(
                            "${h.title} - 第${h.episodeIndex + 1}集",
                            color = TextDark, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { showContinueBar = false }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = TextGray, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ===== 子组件 =====

@Composable
fun TabItem(name: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            name,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) RedPrimary else TextDark
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(if (selected) RedPrimary else Color.Transparent)
        )
    }
}

@Composable
fun BannerCarousel(banners: List<Movie>, navController: NavHostController) {
    var currentIndex by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp)
        .height(180.dp)
        .clip(RoundedCornerShape(12.dp))
        .clickable {
            val movie = banners[currentIndex % banners.size]
            navController.navigate(Screen.Detail.createRoute(movie.id, movie.sourceId))
        }
    ) {
        AsyncImage(
            model = banners[currentIndex % banners.size].coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // 底部渐变遮罩
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.BottomCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                    )
                )
        )
        // 标题
        Text(
            text = banners[currentIndex % banners.size].title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
        )
        // 指示器
        Row(
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(minOf(banners.size, 5)) { idx ->
                Box(
                    modifier = Modifier
                        .size(if (idx == currentIndex % banners.size) 8.dp else 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (idx == currentIndex % banners.size) Color.White else Color.White.copy(alpha = 0.4f))
                )
            }
        }
    }

    // 自动轮播
    LaunchedEffect(banners) {
        while (true) {
            delay(4000)
            currentIndex = (currentIndex + 1) % banners.size
        }
    }
}

@Composable
fun MovieCardLight(movie: Movie, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clickable(onClick = onClick)
    ) {
        // 海报
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(10.dp))
                .background(LightBg)
        ) {
            AsyncImage(
                model = movie.coverUrl,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // 状态标签（如"更新至12集"）
            if (movie.score.isNotBlank() && movie.score != "0.0" && movie.score != "0") {
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = RedPrimary.copy(alpha = 0.85f)
                ) {
                    Text(
                        text = "全${movie.score}集",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            } else {
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "更新中",
                        color = Color.White,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = movie.title,
            color = TextDark,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 17.sp
        )
    }
}
