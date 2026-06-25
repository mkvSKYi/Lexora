package com.reader.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.reader.feature.dashboard.DashboardScreen
import com.reader.feature.library.LibraryScreen
import com.reader.feature.reader.ReaderScreen
import com.reader.feature.saved.SavedWordsScreen
import com.reader.feature.saved.review.ReviewSessionScreen

private const val MAIN_ROUTE = "main"
private const val TODAY_ROUTE = "today"
private const val LIBRARY_ROUTE = "library"
private const val WORDS_ROUTE = "words"
private const val READER_ROUTE = "reader"
private const val REVIEW_ROUTE = "review"
private const val BOOK_ID_ARG = "bookId"

@Composable
fun ReaderNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = MAIN_ROUTE) {
        composable(MAIN_ROUTE) {
            MainTabs(
                onOpenBook = { id -> navController.navigate("$READER_ROUTE/$id") },
                onStartReview = { navController.navigate(REVIEW_ROUTE) },
            )
        }
        composable(REVIEW_ROUTE) {
            ReviewSessionScreen(onDone = { navController.popBackStack() })
        }
        composable(
            route = "$READER_ROUTE/{$BOOK_ID_ARG}",
            arguments = listOf(navArgument(BOOK_ID_ARG) { type = NavType.LongType }),
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getLong(BOOK_ID_ARG) ?: return@composable
            ReaderScreen(
                bookId = bookId,
                onBack = { navController.popBackStack() },
                viewModel = hiltViewModel(),
            )
        }
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    TODAY(TODAY_ROUTE, "Today", Icons.Filled.Today),
    LIBRARY(LIBRARY_ROUTE, "Library", Icons.Filled.MenuBook),
    WORDS(WORDS_ROUTE, "Words", Icons.Filled.Bookmarks),
}

@Composable
private fun MainTabs(
    onOpenBook: (Long) -> Unit,
    onStartReview: () -> Unit,
) {
    val tabsNav = rememberNavController()
    Scaffold(
        bottomBar = {
            val current by tabsNav.currentBackStackEntryAsState()
            val currentRoute = current?.destination?.route
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            tabsNav.navigate(tab.route) {
                                popUpTo(tabsNav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = tabsNav,
            startDestination = TODAY_ROUTE,
            modifier = Modifier.padding(innerPadding),
            // Fade-through between tabs: the outgoing screen fades out while the incoming one
            // fades + scales up softly. Keeps tab switches feeling intentional, not instant.
            enterTransition = { fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.96f) },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.96f) },
            popExitTransition = { fadeOut(tween(150)) },
        ) {
            composable(TODAY_ROUTE) {
                DashboardScreen(onStartReview = onStartReview)
            }
            composable(LIBRARY_ROUTE) {
                LibraryScreen(
                    onBookClick = onOpenBook,
                    onOpenSaved = { tabsNav.navigate(WORDS_ROUTE) { launchSingleTop = true } },
                    viewModel = hiltViewModel(),
                )
            }
            composable(WORDS_ROUTE) {
                SavedWordsScreen(
                    onBack = { tabsNav.navigate(TODAY_ROUTE) { launchSingleTop = true } },
                    onStartReview = onStartReview,
                )
            }
        }
    }
}
