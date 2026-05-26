package com.aggregator.movie.ui.category

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.coroutines.launch

@Composable
fun CategoryScreen(navController: NavHostController) {
    val repository = MovieApplication.instance.repository
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var movies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(1) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        repository.getCategories().fold(
            onSuccess = {
                categories = it
                if (it.isNotEmpty()) selectedCategory = it.first()
            },
            onFailure = { error = it.message }
        )
    }

    LaunchedEffect(selectedCategory, page) {
        selectedCategory?.let { cat ->
            isLoading = true
            repository.getMoviesByCategory(cat.id, page).fold(
                onSuccess = { result ->
                    movies = if (page == 1) result.movies else movies + result.movies
                },
                onFailure = { error = it.message }
            )
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(LightBg)) {
        // 顶部标题
        Surface(color = LightSurface, shadowElevation = 1.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("排行榜", color = TextDark, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 分类标签（空时显示提示）
        if (categories.isEmpty() && !isLoading && error == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Category, contentDescription = null, tint = TextLightGray, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("暂无分类数据", color = TextGray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = {
                        categories = emptyList(); error = null
                        scope.launch {
                            repository.getCategories().fold(
                                onSuccess = { categories = it; if (it.isNotEmpty()) selectedCategory = it.first() },
                                onFailure = { error = it.message }
                            )
                        }
                    }) { Text("重试") }
                }
            }
            return@Column
        }

        if (categories.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    FilterChip(
                        selected = cat.id == selectedCategory?.id,
                        onClick = {
                            selectedCategory = cat; page = 1; movies = emptyList()
                        },
                        label = { Text(cat.name, fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RedPrimary,
                            selectedLabelColor = Color.White,
                            containerColor = Color.White,
                            labelColor = TextGray
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = cat.id == selectedCategory?.id,
                            borderColor = DividerLight,
                            selectedBorderColor = RedPrimary,
                            disabledBorderColor = DividerLight,
                            disabledSelectedBorderColor = RedPrimary
                        )
                    )
                }
            }
        }

        // 错误提示
        error?.let {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = TextGray, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = TextGray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { navController.popBackStack() }) { Text("返回") }
                }
            }
            return@Column
        }

        // 加载中
        if (isLoading && movies.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = RedPrimary)
            }
            return@Column
        }

        // 空数据
        if (!isLoading && movies.isEmpty() && categories.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = TextLightGray, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("暂无数据", color = TextGray, fontSize = 14.sp)
                }
            }
            return@Column
        }

        // 影片网格
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(movies.chunked(3)) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { movie ->
                        GridMovieCard(
                            movie = movie,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                navController.navigate(
                                    Screen.Detail.createRoute(movie.id, movie.sourceId)
                                )
                            }
                        )
                    }
                    repeat(3 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            // 加载更多
            item {
                if (movies.isNotEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        TextButton(onClick = { page++ }) {
                            Text("加载更多", color = RedPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GridMovieCard(movie: Movie, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(LightBg)
        ) {
            AsyncImage(
                model = movie.coverUrl,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (movie.score.isNotBlank() && movie.score != "0.0" && movie.score != "0") {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = RedPrimary.copy(alpha = 0.85f)
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
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = movie.title,
            color = TextDark,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
