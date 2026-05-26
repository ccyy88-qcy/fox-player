package com.aggregator.movie.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.aggregator.movie.ui.home.HomeScreen
import com.aggregator.movie.ui.search.SearchScreen
import com.aggregator.movie.ui.detail.DetailScreen
import com.aggregator.movie.ui.player.PlayerScreen
import com.aggregator.movie.ui.collection.CollectionScreen
import com.aggregator.movie.ui.history.HistoryScreen
import com.aggregator.movie.ui.theme.*

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Category : Screen("category")
    object Collection : Screen("collection")
    object History : Screen("history")
    object Search : Screen("search?query={query}") {
        fun createRoute(query: String = "") = "search?query=$query"
    }
    object Detail : Screen("detail/{movieId}/{sourceId}") {
        fun createRoute(movieId: String, sourceId: String) = "detail/$movieId/$sourceId"
    }
    object Player : Screen("player/{movieId}/{sourceId}/{episodeIndex}") {
        fun createRoute(movieId: String, sourceId: String, episodeIndex: Int) =
            "player/${movieId}/${sourceId}/${episodeIndex}"
    }
}

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieNavHost() {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    // 底部导航只在主页面显示
    val mainPages = listOf("home", "category", "collection", "history")
    val showBottomBar = currentRoute in mainPages

    val bottomNavItems = remember {
        listOf(
            BottomNavItem("首页", Icons.Default.Home, "home"),
            BottomNavItem("分类", Icons.Default.Category, "category"),
            BottomNavItem("收藏", Icons.Default.Favorite, "collection"),
            BottomNavItem("历史", Icons.Default.History, "history")
        )
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 2.dp
                ) {
                    bottomNavItems.forEach { item ->
                        val isSelected = currentRoute == item.route ||
                            (item.route == "category" && currentRoute?.startsWith("category") == true)
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = isSelected,
                            onClick = {
                                // 直接导航，去掉守卫条件
                                navController.navigate(item.route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("home") {
                HomeScreen(navController = navController)
            }
            composable("category") {
                com.aggregator.movie.ui.category.CategoryScreen(navController = navController)
            }
            composable("collection") {
                CollectionScreen(navController = navController)
            }
            composable("history") {
                HistoryScreen(navController = navController)
            }
            composable(
                "search?query={query}",
                arguments = listOf(navArgument("query") {
                    type = NavType.StringType; defaultValue = ""
                })
            ) { entry ->
                SearchScreen(
                    navController = navController,
                    initialQuery = entry.arguments?.getString("query") ?: ""
                )
            }
            composable(
                "detail/{movieId}/{sourceId}",
                arguments = listOf(
                    navArgument("movieId") { type = NavType.StringType },
                    navArgument("sourceId") { type = NavType.StringType }
                )
            ) { entry ->
                DetailScreen(
                    movieId = entry.arguments?.getString("movieId") ?: "",
                    sourceId = entry.arguments?.getString("sourceId") ?: "",
                    navController = navController
                )
            }
            composable(
                "player/{movieId}/{sourceId}/{episodeIndex}",
                arguments = listOf(
                    navArgument("movieId") { type = NavType.StringType },
                    navArgument("sourceId") { type = NavType.StringType },
                    navArgument("episodeIndex") { type = NavType.IntType }
                )
            ) { entry ->
                val pid = entry.arguments?.getString("movieId") ?: ""
                val sid = entry.arguments?.getString("sourceId") ?: ""
                val eid = entry.arguments?.getInt("episodeIndex") ?: 0
                if (pid.isBlank()) {
                    Box(modifier = Modifier.fillMaxSize()) { Text("参数错误") }
                } else {
                    PlayerScreen(movieId = pid, sourceId = sid, episodeIndex = eid, navController = navController)
                }
            }
        }
    }
}
