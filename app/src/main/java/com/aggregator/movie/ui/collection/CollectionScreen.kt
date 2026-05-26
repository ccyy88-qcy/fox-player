package com.aggregator.movie.ui.collection

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
import com.aggregator.movie.data.model.FavoriteEntity
import com.aggregator.movie.ui.Screen
import com.aggregator.movie.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun CollectionScreen(navController: NavHostController) {
    val repository = MovieApplication.instance.repository
    val favorites by repository.getFavorites().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    
    Column(modifier = Modifier.fillMaxSize()) {
        // 标题栏
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = OrangePrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "我的收藏",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (favorites.isNotEmpty()) {
                    TextButton(onClick = {
                        scope.launch {
                            repository.clearFavorites()
                        }
                    }) {
                        Text("清空", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
        
        if (favorites.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("暂无收藏", color = TextSecondary, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("去首页发现好片吧", color = TextSecondary.copy(alpha = 0.6f), fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(favorites) { fav ->
                    FavoriteItem(
                        favorite = fav,
                        onClick = {
                            navController.navigate(Screen.Detail.createRoute(fav.movieId, fav.sourceId))
                        },
                        onRemove = {
                            scope.launch {
                                repository.removeFavorite(fav.movieId)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FavoriteItem(favorite: FavoriteEntity, onClick: () -> Unit, onRemove: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = favorite.coverUrl,
                contentDescription = favorite.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(70.dp)
                    .height(100.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = favorite.title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (favorite.score.isNotBlank()) {
                    Text("⭐ ${favorite.score}", color = OrangePrimary, fontSize = 12.sp)
                }
                if (favorite.year.isNotBlank()) {
                    Text(favorite.year, color = TextSecondary, fontSize = 12.sp)
                }
                if (favorite.genre.isNotBlank()) {
                    Text(favorite.genre, color = TextSecondary, fontSize = 12.sp)
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = TextSecondary)
            }
        }
    }
}
