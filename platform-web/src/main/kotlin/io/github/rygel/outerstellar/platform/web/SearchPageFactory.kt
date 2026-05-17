package io.github.rygel.outerstellar.platform.web

class SearchPageFactory {

    fun buildSearchPage(
        ctx: WebContext,
        query: String,
        providers: List<io.github.rygel.outerstellar.platform.search.SearchProvider>,
        limit: Int = 20,
        typeFilter: String = "",
    ): Page<SearchPage> {
        val i18n = ctx.i18n
        val shell = ctx.shell(i18n.translate("web.search.title"), "/search")
        val filteredProviders =
            if (typeFilter.isBlank() || typeFilter == "all") {
                providers
            } else {
                providers.filter { it.type == typeFilter }
            }
        val results =
            if (query.isBlank()) {
                emptyList()
            } else {
                val providerLimit =
                    (kotlin.math.ceil(limit.toDouble() / (filteredProviders.size.coerceAtLeast(1)))).toInt()
                filteredProviders
                    .flatMap { it.search(query, providerLimit) }
                    .sortedByDescending { it.score }
                    .take(limit)
                    .map { r ->
                        SearchResultViewModel(
                            id = r.id,
                            title = r.title,
                            subtitle = r.subtitle,
                            url = r.url,
                            type = r.type,
                        )
                    }
            }
        val typeFilters =
            listOf(
                SearchTypeFilter(
                    "all",
                    i18n.translate("web.search.filter.all"),
                    if (query.isBlank()) "/search" else "/search?q=$query",
                    typeFilter.isBlank() || typeFilter == "all",
                ),
                SearchTypeFilter(
                    "message",
                    i18n.translate("web.search.filter.message"),
                    "/search?q=$query&type=message",
                    typeFilter == "message",
                ),
                SearchTypeFilter(
                    "contact",
                    i18n.translate("web.search.filter.contact"),
                    "/search?q=$query&type=contact",
                    typeFilter == "contact",
                ),
            )
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
}
