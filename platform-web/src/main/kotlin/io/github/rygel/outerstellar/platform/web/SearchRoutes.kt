package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.infra.render
import io.github.rygel.outerstellar.platform.search.SearchProvider
import org.http4k.contract.ContractRoute
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.format.KotlinxSerialization
import org.http4k.template.TemplateRenderer

class SearchRoutes(
    private val searchPageFactory: SearchPageFactory,
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
                    val ctx = request.requestContext
                    val shellRenderer = request.shellRenderer
                    if (ctx.user == null) {
                        return@to Response(Status.FOUND).header("location", shellRenderer.url("/auth"))
                    }
                    val query = request.query("q").orEmpty()
                    val limit =
                        request.query("limit")?.toIntOrNull()?.coerceIn(1, SearchPageFactory.MAX_SEARCH_LIMIT)
                            ?: SearchPageFactory.DEFAULT_SEARCH_LIMIT
                    val type = request.query("type").orEmpty()
                    renderer.render(searchPageFactory.buildSearchPage(shellRenderer, query, providers, limit, type))
                },
            "/api/v1/search" meta
                {
                    summary = "JSON search results"
                } bindContract
                GET to
                { request ->
                    val ctx = request.requestContext
                    if (ctx.user == null) {
                        return@to Response(Status.UNAUTHORIZED)
                            .header("content-type", "application/json")
                            .body("""{"message":"Authentication required","status":401}""")
                    }
                    val query = request.query("q").orEmpty()
                    val limit =
                        request.query("limit")?.toIntOrNull()?.coerceIn(1, SearchPageFactory.MAX_SEARCH_LIMIT)
                            ?: SearchPageFactory.DEFAULT_SEARCH_LIMIT
                    val results = SearchPageFactory.aggregateResults(providers, query, limit)
                    val json =
                        KotlinxSerialization.asJsonObject(
                            mapOf("query" to query, "results" to results, "total" to results.size)
                        )
                    Response(Status.OK).header("content-type", "application/json; charset=utf-8").body(json.toString())
                },
        )
}
