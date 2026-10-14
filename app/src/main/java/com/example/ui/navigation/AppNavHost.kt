package com.example.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.MainViewModel
import com.example.ui.screens.bookmarks.BookmarksScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.import_screen.ImportScreen
import com.example.ui.screens.lesson_detail.LessonDetailScreen
import com.example.ui.screens.library.LibraryScreen
import com.example.ui.screens.review.FinalReviewScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.study.SectionStudyScreen
import com.example.ui.theme.CardSurface
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextMuted

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home.route, "Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    BottomNavItem(Screen.Library.route, "Library", Icons.Filled.MenuBook, Icons.Outlined.MenuBook, "nav_library"),
    BottomNavItem(Screen.Bookmarks.route, "Bookmarks", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder, "nav_bookmarks"),
    BottomNavItem(Screen.Settings.route, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
)

@Composable
fun AppNavHost(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Show bottom bar only on the 4 top-level screens
    val isTopLevelDestination = bottomNavItems.any { it.route == currentRoute }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (isTopLevelDestination) {
                NavigationBar(
                    containerColor = CardSurface,
                    tonalElevation = 2.dp
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) },
                            modifier = Modifier.testTag(item.testTag),
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Home Destination
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToLesson = { lessonId ->
                        navController.navigate(Screen.LessonDetail.createRoute(lessonId))
                    },
                    onNavigateToSection = { lessonId, sectionId ->
                        navController.navigate(Screen.SectionStudy.createRoute(lessonId, sectionId))
                    },
                    onNavigateToLibrary = {
                        navController.navigate(Screen.Library.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToImport = {
                        navController.navigate(Screen.Import.route)
                    }
                )
            }

            // Library Destination
            composable(Screen.Library.route) {
                LibraryScreen(
                    viewModel = viewModel,
                    onNavigateToLesson = { lessonId ->
                        navController.navigate(Screen.LessonDetail.createRoute(lessonId))
                    },
                    onNavigateToImport = {
                        navController.navigate(Screen.Import.route)
                    }
                )
            }

            // Bookmarks Destination
            composable(Screen.Bookmarks.route) {
                BookmarksScreen(
                    viewModel = viewModel,
                    onNavigateToLesson = { lessonId ->
                        navController.navigate(Screen.LessonDetail.createRoute(lessonId))
                    },
                    onNavigateToSection = { lessonId, sectionId ->
                        navController.navigate(Screen.SectionStudy.createRoute(lessonId, sectionId))
                    }
                )
            }

            // Settings Destination
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = viewModel)
            }

            // Import Screen
            composable(Screen.Import.route) {
                ImportScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onImportSuccess = { navController.popBackStack() }
                )
            }

            // Lesson Detail Screen
            composable(
                route = Screen.LessonDetail.route,
                arguments = listOf(navArgument("lessonId") { type = NavType.StringType })
            ) { backStackEntry ->
                val lessonId = backStackEntry.arguments?.getString("lessonId") ?: ""
                LessonDetailScreen(
                    lessonId = lessonId,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSection = { lId, sId ->
                        navController.navigate(Screen.SectionStudy.createRoute(lId, sId))
                    },
                    onNavigateToReview = { lId ->
                        navController.navigate(Screen.FinalReview.createRoute(lId))
                    }
                )
            }

            // Section Study Screen
            composable(
                route = Screen.SectionStudy.route,
                arguments = listOf(
                    navArgument("lessonId") { type = NavType.StringType },
                    navArgument("sectionId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val lessonId = backStackEntry.arguments?.getString("lessonId") ?: ""
                val sectionId = backStackEntry.arguments?.getString("sectionId") ?: ""
                SectionStudyScreen(
                    lessonId = lessonId,
                    sectionId = sectionId,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSection = { lId, sId ->
                        navController.navigate(Screen.SectionStudy.createRoute(lId, sId)) {
                            popUpTo(Screen.SectionStudy.route) { inclusive = true }
                        }
                    },
                    onNavigateToReview = { lId ->
                        navController.navigate(Screen.FinalReview.createRoute(lId)) {
                            popUpTo(Screen.LessonDetail.route)
                        }
                    }
                )
            }

            // Final Review Screen
            composable(
                route = Screen.FinalReview.route,
                arguments = listOf(navArgument("lessonId") { type = NavType.StringType })
            ) { backStackEntry ->
                val lessonId = backStackEntry.arguments?.getString("lessonId") ?: ""
                FinalReviewScreen(
                    lessonId = lessonId,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
