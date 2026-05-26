package com.aggregator.movie.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.aggregator.movie.MovieApplication
import com.aggregator.movie.data.model.Movie
import com.aggregator.movie.ui.Screen
import com.aggregator.movie.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(navController: NavHostController, initialQuery: String = "") {
    val repository = MovieApplication.instance.repository
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(1) }
    var totalPage by remember { mutableIntStateOf(1) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }
    
    fun doSearch(keyword: String, pageNum: Int = 1) {
        if (keyword.isBlank()) return
        searchJob?.cancel()
        searchJob = scope.launch {
            isLoading = true; error = null
            repository.search(keyword, pageNum).fold(
                onSuccess = { result ->
                    if (pageNum == 1) results = result.movies
                    else results = results + result.movies
                    totalPage = result.totalPage
                },
                onFailure = { error = it.message }
            )
            isLoading = false
        }
    }
    
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) doSearch(initialQuery)
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // 搜索栏
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = TextPrimary)
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { 
                        query = it
                        if (it.isNotBlank()) doSearch(it)
                    },
                    placeholder = { Text("搜片", color = TextSecondary) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                if (query.isNotBlank()) {
                    IconButton(onClick = { 
                        query = ""; results = emptyList(); error = null 
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "清除", tint = TextSecondary)
                    }
                }
            }
        }
        
        // 结果列表
        if (isLoading && results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (error != null && results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { doSearch(query) }) { Text("重试") }
                }
            }
        } else if (results.isEmpty() && query.isNotBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("未找到相关结果", color = TextSecondary)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(results) { movie ->
                    SearchResultItem(movie = movie, onClick = {
                        navController.navigate(Screen.Detail.createRoute(movie.id, movie.sourceId))
                    })
                }
                if (page < totalPage) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(onClick = { 
                                page++; doSearch(query, page) 
                            }) {
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
fun SearchResultItem(movie: Movie, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(modifier = Modifier.padding(10.dp)) {
            AsyncImage(
                model = movie.coverUrl,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(80.dp)
                    .height(115.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = movie.title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (movie.score.isNotBlank()) {
                    Text("评分: ${movie.score}", color = OrangePrimary, fontSize = 12.sp)
                }
                if (movie.year.isNotBlank()) {
                    Text("年份: ${movie.year}", color = TextSecondary, fontSize = 12.sp)
                }
                if (movie.genre.isNotBlank()) {
                    Text("类型: ${movie.genre}", color = TextSecondary, fontSize = 12.sp)
                }
                if (movie.actors.isNotBlank()) {
                    Text(
                        text = "主演: ${movie.actors}",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
