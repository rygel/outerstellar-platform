package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.search.SearchProvider
import io.github.rygel.outerstellar.platform.search.SearchResult
import kotlin.math.ceil

class SearchPageFactory {

    companion object {
        const val DEFAULT_SEARCH_LIMIT = 20
        const val MAX_SEARCH_LIMIT = 100

        fun aggregateResults(
            providers: List<SearchProvider>,
            query: String,
            limit: Int = DEFAULT_SEARCH_LIMIT,
            typeFilter: String = "",
        ): List<SearchResult> {
            if (query.isBlank()) return emptyList()
            val safeLimit = limit.coerceIn(1, MAX_SEARCH_LIMIT)
            val filtered =
                if (typeFilter.isBlank() || typeFilter == "all") {
                    providers
                } else {
                    providers.filter { it.type == typeFilter }
                }
            if (filtered.isEmpty()) return emptyList()
            val providerLimit = ceil(safeLimit.toDouble() / filtered.size).toInt()
            return filtered.flatMap { it.search(query, providerLimit) }.sortedByDescending { it.score }.take(safeLimit)
        }
    }

    fun buildSearchPage(
        ctx: WebContext,
        query: String,
        providers: List<SearchProvider>,
        limit: Int = DEFAULT_SEARCH_LIMIT,
        typeFilter: String = "",
    ): Page<SearchPage> {
        val i18n = ctx.i18n
        val shell = ctx.shell(i18n.translate("web.search.title"), "/search")
        val results =
            aggregateResults(providers, query, limit, typeFilter).map { r ->
                SearchResultViewModel(id = r.id, title = r.title, subtitle = r.subtitle, url = r.url, type = r.type)
            }
        val typeFilters = buildTypeFilters(providers, query, typeFilter, i18n)
        return Page(
            shell = shell,
            data =
                SearchPage(
                    title = i18n.translate("web.search.title"),
                    query = query,
                    results = results,
                    emptyLabel = i18n.translate("web.search.empty"),
                    searchPlaceholder = i18n.translate("web.search.placeholder"),
                    searchLabel = i18n.translate("web.search.label"),
                    typeFilter = if (typeFilter.isBlank()) "all" else typeFilter,
                    typeFilters = typeFilters,
                ),
        )
    }

    private fun buildTypeFilters(
        providers: List<SearchProvider>,
        query: String,
        typeFilter: String,
        i18n: I18nService,
    ): List<SearchTypeFilter> {
        val allFilter =
            SearchTypeFilter(
                "all",
                i18n.translate("web.search.filter.all"),
                if (query.isBlank()) "/search" else "/search?q=$query",
                typeFilter.isBlank() || typeFilter == "all",
            )
        val providerFilters = providers.map { provider ->
            SearchTypeFilter(
                provider.type,
                i18n.translate("web.search.filter.${provider.type}"),
                "/search?q=$query&type=${provider.type}",
                typeFilter == provider.type,
            )
        }
        return listOf(allFilter) + providerFilters
    }
}
