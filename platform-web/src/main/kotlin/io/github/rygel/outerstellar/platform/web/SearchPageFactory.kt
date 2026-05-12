package io.github.rygel.outerstellar.platform.web

class SearchPageFactory {

    fun buildSearchPage(
        ctx: WebContext,
        query: String,
        providers: List<io.github.rygel.outerstellar.platform.search.SearchProvider>,
        limit: Int = 20,
    ): Page<SearchPage> {
        val i18n = ctx.i18n
        val shell = ctx.shell(i18n.translate("web.search.title"), "/search")
        val results =
            if (query.isBlank()) {
                emptyList()
            } else {
                providers
                    .flatMap { it.search(query, limit) }
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
                ),
        )
    }
}
