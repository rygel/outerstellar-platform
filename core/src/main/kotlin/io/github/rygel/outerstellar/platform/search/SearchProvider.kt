package io.github.rygel.outerstellar.platform.search

data class SearchResult(
    val id: String,
    val title: String,
    val subtitle: String,
    val url: String,
    val type: String,
    val score: Double = 1.0,
)

interface SearchProvider {
    val type: String
    fun search(query: String, limit: Int = 20): List<SearchResult>
}
