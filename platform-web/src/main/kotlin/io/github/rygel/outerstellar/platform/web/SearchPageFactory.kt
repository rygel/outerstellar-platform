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

        /**
         * Neutralises dangerous URL schemes for use in an `href` context. jte's `${...}` HTML-escapes, so attribute
         * breakout via `"` is impossible, but it does NOT neutralise executable schemes — `javascript:`, `data:`,
         * `vbscript:` survive unchanged and execute on click. This allow-lists only http(s), protocol-relative
         * (`//host`), root-relative (`/path`), and fragment (`#`) URLs; anything else (including all executable
         * schemes, with or without surrounding whitespace/case tricks) is collapsed to `#` so the link still renders
         * but is inert. Defence-in-depth: SearchProvider is an extension SPI, so a careless/malicious third-party
         * provider can't inject script via result URLs.
         */
        fun safeHref(url: String): String {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) return "#"
            // Root-relative and fragment links are always safe.
            if (trimmed.startsWith("/") && !trimmed.startsWith("//")) return trimmed
            val schemeEnd = trimmed.indexOf(':')
            // No scheme (no colon before any '/', '?', '#') → relative/same-origin, safe.
            val firstSlash = trimmed.indexOfAny(charArrayOf('/', '?', '#'))
            if (schemeEnd == -1 || (firstSlash != -1 && schemeEnd > firstSlash)) return trimmed
            val scheme = trimmed.substring(0, schemeEnd).lowercase()
            return if (scheme == "http" || scheme == "https") trimmed else "#"
        }
    }

    fun buildSearchPage(
        shellRenderer: ShellRenderer,
        query: String,
        providers: List<SearchProvider>,
        limit: Int = DEFAULT_SEARCH_LIMIT,
        typeFilter: String = "",
    ): Page<SearchPage> {
        val i18n = shellRenderer.i18n
        val shell = shellRenderer.shell(i18n.translate("web.search.title"), "/search")
        val results =
            aggregateResults(providers, query, limit, typeFilter).map { r ->
                SearchResultViewModel(
                    id = r.id,
                    title = r.title,
                    subtitle = r.subtitle,
                    url = safeHref(r.url),
                    type = r.type,
                )
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
