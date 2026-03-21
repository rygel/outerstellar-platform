package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.infra.render
import io.github.rygel.outerstellar.platform.search.SearchProvider
import org.http4k.contract.ContractRoute
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.format.Jackson
import org.http4k.template.TemplateRenderer

private const val DEFAULT_SEARCH_LIMIT = 20
private const val MAX_SEARCH_LIMIT = 100

class SearchRoutes(
    private val pageFactory: WebPageFactory,
    private val renderer: TemplateRenderer,
    private val providers: List<SearchProvider>,
) : ServerRoutes {

    override val routes: List<ContractRoute> =
        listOf(
            "/search" meta
                {
                    summary = "Search results page"
                } bindContract
                GET to
                { request ->
                    val ctx = request.webContext
                    val query = request.query("q").orEmpty()
                    val limit =
                        request.query("limit")?.toIntOrNull()?.coerceIn(1, MAX_SEARCH_LIMIT) ?: DEFAULT_SEARCH_LIMIT
                    renderer.render(pageFactory.buildSearchPage(ctx, query, providers, limit))
                },
            "/api/v1/search" meta
                {
                    summary = "JSON search results"
                } bindContract
                GET to
                { request ->
                    val query = request.query("q").orEmpty()
                    val limit =
                        request.query("limit")?.toIntOrNull()?.coerceIn(1, MAX_SEARCH_LIMIT) ?: DEFAULT_SEARCH_LIMIT
                    val results =
                        if (query.isBlank()) {
                            emptyList()
                        } else {
                            providers.flatMap { it.search(query, limit) }.sortedByDescending { it.score }.take(limit)
                        }
                    val json =
                        Jackson.asJsonObject(mapOf("query" to query, "results" to results, "total" to results.size))
                    Response(Status.OK).header("content-type", "application/json; charset=utf-8").body(json.toString())
                },
        )
}
