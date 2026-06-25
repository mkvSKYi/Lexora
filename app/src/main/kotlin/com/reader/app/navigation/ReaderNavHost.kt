package com.reader.app.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.reader.core.designsystem.theme.AuroraAccent
import com.reader.core.designsystem.theme.LexHairline
import com.reader.core.designsystem.theme.LexSurfaceHigh
import com.reader.core.designsystem.theme.LexTeal
import com.reader.core.designsystem.theme.LexTextMuted
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

private val Coral = Color(0xFFFF7A59)

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

private enum class Tab(val route: String, val label: String, val icon: ImageVector, val color: Color) {
    TODAY(TODAY_ROUTE, "Today", Icons.Filled.Today, AuroraAccent),
    LIBRARY(LIBRARY_ROUTE, "Library", Icons.Filled.AutoStories, LexTeal),
    WORDS(WORDS_ROUTE, "Words", Icons.Filled.Translate, Coral),
}

@Composable
private fun MainTabs(
    onOpenBook: (Long) -> Unit,
    onStartReview: () -> Unit,
) {
    val tabsNav = rememberNavController()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            val current by tabsNav.currentBackStackEntryAsState()
            val currentRoute = current?.destination?.route
            LexoraBottomBar(currentRoute) { tab ->
                tabsNav.navigate(tab.route) {
                    popUpTo(tabsNav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = tabsNav,
            startDestination = TODAY_ROUTE,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(tween(120)) },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(120)) },
            popExitTransition = { fadeOut(tween(120)) },
        ) {
            composable(TODAY_ROUTE) {
                DashboardScreen(onStartReview = onStartReview)
            }
            composable(LIBRARY_ROUTE) {
                LibraryScreen(
                    onBookClick = onOpenBook,
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

/** A floating, pill-shaped nav bar: each tab has its own colour, and the active one expands to
 *  reveal a coloured label pill. */
@Composable
private fun LexoraBottomBar(currentRoute: String?, onSelect: (Tab) -> Unit) {
    Row(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(LexSurfaceHigh)
            .border(1.dp, LexHairline, RoundedCornerShape(28.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { tab ->
            val selected = currentRoute == tab.route
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (selected) tab.color.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(tab) }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint = if (selected) tab.color else LexTextMuted,
                    modifier = Modifier.size(24.dp),
                )
                AnimatedVisibility(
                    visible = selected,
                    enter = fadeIn(tween(180)) + expandHorizontally(tween(220)),
                    exit = fadeOut(tween(120)) + shrinkHorizontally(tween(160)),
                ) {
                    Row {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = tab.label,
                            color = tab.color,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}
