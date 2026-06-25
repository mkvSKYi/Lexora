package com.reader.app.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.reader.feature.library.LibraryScreen
import com.reader.feature.reader.ReaderScreen
import com.reader.feature.saved.SavedWordsScreen
import com.reader.feature.saved.review.ReviewSessionScreen

private const val LIBRARY_ROUTE = "library"
private const val READER_ROUTE = "reader"
private const val SAVED_ROUTE = "saved"
private const val REVIEW_ROUTE = "review"
private const val BOOK_ID_ARG = "bookId"

@Composable
fun ReaderNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = LIBRARY_ROUTE) {
        composable(LIBRARY_ROUTE) {
            LibraryScreen(
                onBookClick = { id -> navController.navigate("$READER_ROUTE/$id") },
                onOpenSaved = { navController.navigate(SAVED_ROUTE) },
                viewModel = hiltViewModel(),
            )
        }
        composable(SAVED_ROUTE) {
            SavedWordsScreen(
                onBack = { navController.popBackStack() },
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
