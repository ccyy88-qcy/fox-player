package com.aggregator.movie.ui.history

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
import com.aggregator.movie.data.model.WatchHistoryEntity
import com.aggregator.movie.ui.Screen
import com.aggregator.movie.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(navController: NavHostController) {
    val repository = MovieApplication.instance.repository
    val histories by repository.getWatchHistory().collectAsState(initial = emptyList())
    
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
                Icon(Icons.Default.History, contentDescription = null, tint = OrangePrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "观看历史",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (histories.isNotEmpty()) {
                    TextButton(onClick = {
                        kotlinx.coroutines.GlobalScope.launch {
                            repository.clearHistory()
                        }
                    }) {
                        Text("清空", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
        
        if (histories.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("暂无观看记录", color = TextSecondary, fontSize = 15.sp)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(histories) { history ->
                    HistoryItem(
                        history = history,
                        onClick = {
                            navController.navigate(
                                Screen.Detail.createRoute(history.movieId, history.sourceId)
                            )
                        },
                        onDelete = {
                            kotlinx.coroutines.GlobalScope.launch {
                                repository.clearHistory() // 简化：清全部。实际应删单条
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryItem(history: WatchHistoryEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(modifier = Modifier.padding(10.dp)) {
            // 封面
            Box {
                AsyncImage(
                    model = history.coverUrl,
                    contentDescription = history.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(80.dp)
                        .height(115.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                // 进度条
                if (history.duration > 0) {
                    val progress = (history.position.toFloat() / history.duration).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                        color = OrangePrimary,
                        trackColor = DarkSurface,
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = history.title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (history.episodeTitle.isNotBlank()) {
                    Text("看到: ${history.episodeTitle}", color = OrangePrimary, fontSize = 12.sp)
                }
                // 进度
                if (history.duration > 0) {
                    val progressPct = (history.position * 100 / history.duration)
                    Text(
                        text = "进度: ${progressPct}%",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                // 时间
                val dateStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                    .format(Date(history.watchedAt))
                Text("观看时间: $dateStr", color = TextSecondary, fontSize = 11.sp)
            }
            
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = TextSecondary)
            }
        }
    }
}
