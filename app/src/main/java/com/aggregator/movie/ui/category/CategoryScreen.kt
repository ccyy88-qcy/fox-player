package com.aggregator.movie.ui.category

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
    
    Column(modifier = Modifier.fillMaxSize()) {
        // 分类标签
        if (categories.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    FilterChip(
                        selected = cat.id == selectedCategory?.id,
                        onClick = { 
                            selectedCategory = cat; page = 1; movies = emptyList() 
                        },
                        label = { Text(cat.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OrangePrimary,
                            selectedLabelColor = Color.White,
                            containerColor = DarkSurface,
                            labelColor = TextSecondary
                        )
                    )
                }
            }
        }
        
        // 影片网格
        if (isLoading && movies.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
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
                        // 补齐空位
                        repeat(3 - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                item {
                    if (movies.isNotEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(onClick = { page++ }) {
                                Text("加载更多", color = OrangePrimary)
                            }
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
        ) {
            AsyncImage(
                model = movie.coverUrl,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
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
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = movie.title,
            color = TextPrimary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
