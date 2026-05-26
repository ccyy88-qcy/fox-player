package com.aggregator.movie.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.aggregator.movie.MovieApplication
import com.aggregator.movie.data.model.HomeData
import com.aggregator.movie.data.model.Movie
import com.aggregator.movie.ui.Screen
import com.aggregator.movie.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavHostController) {
    val repository = MovieApplication.instance.repository
    var homeData by remember { mutableStateOf<HomeData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        isLoading = true
        repository.getHomeData().fold(
            onSuccess = { homeData = it; error = null },
            onFailure = { error = it.message }
        )
        isLoading = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // ========== 顶部搜索栏（高级版） ==========
        item(key = "search_bar") {
            Surface(
                color = DarkSurface,
                tonalElevation = 0.dp,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 搜索框 - 修复输入截断：用更大高度+无固定高度约束
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text("搜片", color = TextSecondary, fontSize = 15.sp)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary,
                                modifier = Modifier.size(22.dp))
                        },
                        modifier = Modifier
                            .weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OrangePrimary,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = OrangePrimary
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 15.sp),
                        // 去掉固定height，让TextField自然撑开，避免裁切
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    // 搜索按钮（带圆角背景）
                    Surface(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(20.dp)),
                        color = OrangePrimary,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (searchQuery.isNotBlank()) {
                                    navController.navigate(Screen.Search.createRoute(searchQuery))
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "搜索",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        // ========== 加载状态 ==========
        if (isLoading) {
            item(key = "loading") {
                Box(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = OrangePrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        // ========== 错误状态 ==========
        error?.let {
            item(key = "error_state") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = DarkSurfaceVariant
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(it, color = ErrorColor, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    isLoading = true; error = null
                                    repository.getHomeData().fold(
                                        onSuccess = { homeData = it; error = null },
                                        onFailure = { error = it.message }
                                    )
                                    isLoading = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                        ) { Text("重试") }
                    }
                }
            }
        }

        // ========== Banner轮播（升级版） ==========
        homeData?.banners?.let { banners ->
            if (banners.isNotEmpty()) {
                item {
                    PremiumBannerCarousel(banners = banners, navController = navController)
                }
            }
        }

        // ========== 内容推荐区域 ==========
        homeData?.hotMovies?.let { movies ->
            if (movies.isNotEmpty()) {
                item {
                    PremiumSectionHeader("🔥 热门推荐") {
                        navController.navigate("category")
                    }
                }
                item {
                    PremiumMovieRow(movies = movies, navController = navController)
                }
            }
        }

        homeData?.hotTv?.let { movies ->
            if (movies.isNotEmpty()) {
                item {
                    PremiumSectionHeader("📺 热播剧集") {
                        navController.navigate("category")
                    }
                }
                item {
                    PremiumMovieRow(movies = movies, navController = navController)
                }
            }
        }

        homeData?.hotAnime?.let { movies ->
            if (movies.isNotEmpty()) {
                item {
                    PremiumSectionHeader("🎬 热门动漫") {
                        navController.navigate("category")
                    }
                }
                item {
                    PremiumMovieRow(movies = movies, navController = navController)
                }
            }
        }
    }
}

// =============================================
// 高级版组件
// =============================================

@Composable
fun PremiumBannerCarousel(banners: List<Movie>, navController: NavHostController) {
    var currentIndex by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                if (banners.isNotEmpty()) {
                    val movie = banners[currentIndex % banners.size]
                    navController.navigate(Screen.Detail.createRoute(movie.id, movie.sourceId))
                }
            }
    ) {
        // 背景图
        AsyncImage(
            model = banners[currentIndex % banners.size].coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // 双层渐变遮罩（更高级的视觉效果）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.15f),
                            Color.Black.copy(alpha = 0.75f)
                        )
                    )
                )
        )
        // 底部信息
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
        ) {
            Text(
                text = banners[currentIndex % banners.size].title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = banners[currentIndex % banners.size].description.take(60)
                    .ifBlank { "点击查看详情" },
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // 指示器（放在右下角）
        Row(
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            repeat(minOf(banners.size, 5)) { idx ->
                Box(
                    modifier = Modifier
                        .size(if (idx == currentIndex % banners.size) 10.dp else 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (idx == currentIndex % banners.size) OrangePrimary
                            else Color.White.copy(alpha = 0.4f)
                        )
                )
            }
        }
    }

    // 自动轮播
    LaunchedEffect(banners) {
        while (true) {
            delay(4500)
            currentIndex = (currentIndex + 1) % banners.size
        }
    }
}

@Composable
fun PremiumSectionHeader(title: String, onMore: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        TextButton(
            onClick = onMore,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text("更多", color = OrangePrimary, fontSize = 13.sp)
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = OrangePrimary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun PremiumMovieRow(movies: List<Movie>, navController: NavHostController) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(movies) { movie ->
            PremiumMovieCard(
                movie = movie,
                onClick = {
                    navController.navigate(Screen.Detail.createRoute(movie.id, movie.sourceId))
                }
            )
        }
    }
}

@Composable
fun PremiumMovieCard(movie: Movie, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = onClick)
    ) {
        // 海报（带阴影效果）
        Box(
            modifier = Modifier
                .width(130.dp)
                .height(185.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurfaceVariant)
        ) {
            AsyncImage(
                model = movie.coverUrl,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // 底部渐变
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                        )
                    )
            )
            // 评分标签（右上）
            if (movie.score.isNotBlank() && movie.score != "0.0" && movie.score != "0") {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = OrangePrimary.copy(alpha = 0.9f)
                ) {
                    Text(
                        text = movie.score,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
            // 底部标签（年份）
            if (movie.year.isNotBlank()) {
                Text(
                    text = movie.year,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = movie.title,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 17.sp
        )
    }
}
