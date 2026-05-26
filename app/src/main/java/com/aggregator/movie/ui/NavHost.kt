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

    // 底部导航栏只在主页面显示
    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        "category",
        Screen.Collection.route,
        Screen.History.route
    )

    val bottomNavItems = listOf(
        BottomNavItem("首页", Icons.Default.Home, Screen.Home.route),
        BottomNavItem("分类", Icons.Default.Category, "category"),
        BottomNavItem("收藏", Icons.Default.Favorite, Screen.Collection.route),
        BottomNavItem("历史", Icons.Default.History, Screen.History.route)
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = currentRoute == item.route ||
                                (item.route == "category" && currentRoute?.startsWith("category") == true),
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(Screen.Home.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
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
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(navController = navController)
            }
            composable("category") {
                CategoryScreen(navController = navController)
            }
            composable(Screen.Collection.route) {
                CollectionScreen(navController = navController)
            }
            composable(Screen.History.route) {
                HistoryScreen(navController = navController)
            }
            composable(
                Screen.Search.route,
                arguments = listOf(navArgument("query") {
                    type = NavType.StringType
                    defaultValue = ""
                })
            ) { backStackEntry ->
                val query = backStackEntry.arguments?.getString("query") ?: ""
                SearchScreen(navController = navController, initialQuery = query)
            }
            composable(
                Screen.Detail.route,
                arguments = listOf(
                    navArgument("movieId") { type = NavType.StringType },
                    navArgument("sourceId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                DetailScreen(
                    movieId = backStackEntry.arguments?.getString("movieId") ?: "",
                    sourceId = backStackEntry.arguments?.getString("sourceId") ?: "",
                    navController = navController
                )
            }
            // Player 路由同步注册（非懒加载），强制参数校验
            composable(
                Screen.Player.route,
                arguments = listOf(
                    navArgument("movieId") { type = NavType.StringType },
                    navArgument("sourceId") { type = NavType.StringType },
                    navArgument("episodeIndex") { type = NavType.IntType }
                )
            ) { backStackEntry ->
                val pid = backStackEntry.arguments?.getString("movieId") ?: ""
                val sid = backStackEntry.arguments?.getString("sourceId") ?: ""
                val eid = backStackEntry.arguments?.getInt("episodeIndex") ?: 0
                // 参数校验：如果 movieId 为空，不渲染播放器（防止空白页）
                if (pid.isBlank()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text("参数错误：缺少影片ID")
                    }
                } else {
                    PlayerScreen(
                        movieId = pid,
                        sourceId = sid,
                        episodeIndex = eid,
                        navController = navController
                    )
                }
            }
        }
    }
}

/**
 * 分类页面（简化版，实际可扩展为带筛选的页面）
 */
@Composable
fun CategoryScreen(navController: NavHostController) {
    com.aggregator.movie.ui.category.CategoryScreen(navController = navController)
}
