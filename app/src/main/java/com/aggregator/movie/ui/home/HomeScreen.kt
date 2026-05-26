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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavHostController) {
    val repository = MovieApplication.instance.repository
    var homeData by remember { mutableStateOf<HomeData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    
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
        // === 顶部搜索栏 ===
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { 
                            Text("搜索影视…", color = TextSecondary, fontSize = 14.sp) 
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
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
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        
        // === 加载状态 ===
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        
        // === 错误状态 ===
        error?.let {
            item {
                Card(
                    modifier = Modifier.padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(it, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            scope.launch {
                                isLoading = true; error = null
                                repository.getHomeData().fold(
                                    onSuccess = { homeData = it; error = null },
                                    onFailure = { error = it.message }
                                )
                                isLoading = false
                            }
                        }) {
                            Text("重试")
                        }
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
        
        // === 热门推荐 ===
        homeData?.hotMovies?.let { movies ->
            if (movies.isNotEmpty()) {
                item {
                    SectionHeader("热门推荐", "更多") {
                        navController.navigate("category")
                    }
                }
                item {
                    MovieRow(movies = movies, navController = navController)
                }
            }
        }
        
        // === 热播剧集 ===
        homeData?.hotTv?.let { movies ->
            if (movies.isNotEmpty()) {
                item {
                    SectionHeader("热播剧集", "更多") {
                        navController.navigate("category")
                    }
                }
                item {
                    MovieRow(movies = movies, navController = navController)
                }
            }
        }
        
        // === 热门动漫 ===
        homeData?.hotAnime?.let { movies ->
            if (movies.isNotEmpty()) {
                item {
                    SectionHeader("热门动漫", "更多") {
                        navController.navigate("category")
                    }
                }
                item {
                    MovieRow(movies = movies, navController = navController)
                }
            }
        }
    }
}

@Composable
fun BannerCarousel(banners: List<Movie>, navController: NavHostController) {
    var currentIndex by remember { mutableIntStateOf(0) }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                if (banners.isNotEmpty()) {
                    val movie = banners[currentIndex % banners.size]
                    navController.navigate(Screen.Detail.createRoute(movie.id, movie.sourceId))
                }
            }
    ) {
        AsyncImage(
            model = banners[currentIndex % banners.size].coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // 渐变遮罩
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                        startY = 100f
                    )
                )
        )
        // 标题
        Text(
            text = banners[currentIndex % banners.size].title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        )
        // 指示器
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(minOf(banners.size, 5)) { idx ->
                Box(
                    modifier = Modifier
                        .size(if (idx == currentIndex % banners.size) 8.dp else 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (idx == currentIndex % banners.size) OrangePrimary 
                            else Color.White.copy(alpha = 0.5f)
                        )
                )
            }
        }
    }
    
    // 自动轮播
    LaunchedEffect(banners) {
        while (true) {
            kotlinx.coroutines.delay(4000)
            currentIndex = (currentIndex + 1) % banners.size
        }
    }
}

@Composable
fun SectionHeader(title: String, actionText: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        TextButton(onClick = onAction) {
            Text(actionText, color = OrangePrimary, fontSize = 13.sp)
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = OrangePrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun MovieRow(movies: List<Movie>, navController: NavHostController) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(movies) { movie ->
            MovieCard(movie = movie, onClick = {
                navController.navigate(Screen.Detail.createRoute(movie.id, movie.sourceId))
            })
        }
    }
}

@Composable
fun MovieCard(movie: Movie, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick)
    ) {
        // 封面
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(170.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurfaceVariant)
        ) {
            AsyncImage(
                model = movie.coverUrl,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // 评分标签
            if (movie.score.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = OrangePrimary.copy(alpha = 0.9f)
                ) {
                    Text(
                        text = movie.score,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = movie.title,
            color = TextPrimary,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )
        if (movie.year.isNotBlank()) {
            Text(
                text = movie.year,
                color = TextSecondary,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}
