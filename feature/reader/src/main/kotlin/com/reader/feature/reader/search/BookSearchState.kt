package com.reader.feature.reader.search

sealed interface BookSearchState {
    data object Idle : BookSearchState
    data class Searching(val results: List<SearchResult>) : BookSearchState
    data class Results(val results: List<SearchResult>) : BookSearchState
    data object Empty : BookSearchState
    data object Error : BookSearchState
}
