package com.aggregator.movie.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    object Profile : Screen("profile")
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
        Screen.Profile.route,
        Screen.History.route,
        Screen.Collection.route
    )

    val bottomNavItems = listOf(
        BottomNavItem("首页", Icons.Default.Home, Screen.Home.route),
        BottomNavItem("排行榜", Icons.Default.TrendingUp, "category"),
        BottomNavItem("我的", Icons.Default.Person, Screen.Profile.route)
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = LightSurface,
                    tonalElevation = 0.dp
                ) {
                    bottomNavItems.forEach { item ->
                        val isSelected = when (item.route) {
                            "category" -> currentRoute?.startsWith("category") == true
                            else -> currentRoute == item.route
                        }
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    item.icon,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    item.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                                )
                            },
                            selected = isSelected,
                            onClick = {
                                if (!isSelected) {
                                    navController.navigate(item.route) {
                                        popUpTo(Screen.Home.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = RedPrimary,
                                selectedTextColor = RedPrimary,
                                unselectedIconColor = TextLightGray,
                                unselectedTextColor = TextGray,
                                indicatorColor = RedLight
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
            composable(Screen.Profile.route) {
                ProfileScreen(navController = navController)
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
 * 分类页面
 */
@Composable
fun CategoryScreen(navController: NavHostController) {
    com.aggregator.movie.ui.category.CategoryScreen(navController = navController)
}

/**
 * 我的页面 - 收藏+历史入口
 */
@Composable
fun ProfileScreen(navController: NavHostController) {
    Column(
        modifier = Modifier.fillMaxSize().background(LightBg).padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        // 用户头像占位
        Box(
            modifier = Modifier.size(64.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(32.dp),
                color = RedLight
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = RedPrimary,
                    modifier = Modifier.padding(14.dp).fillMaxSize())
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("我的", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark)
        Spacer(modifier = Modifier.height(24.dp))

        // 收藏入口
        ProfileMenuItem(
            icon = Icons.Default.Favorite,
            title = "我的收藏",
            desc = "收藏的影视作品",
            onClick = { navController.navigate(Screen.Collection.route) }
        )
        Spacer(modifier = Modifier.height(12.dp))
        // 历史入口
        ProfileMenuItem(
            icon = Icons.Default.History,
            title = "观看历史",
            desc = "最近播放记录",
            onClick = { navController.navigate(Screen.History.route) }
        )
    }
}

@Composable
fun ProfileMenuItem(icon: ImageVector, title: String, desc: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(desc, color = TextGray, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextLightGray)
        }
    }
}
