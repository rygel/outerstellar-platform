package io.github.rygel.stellar.web

import io.github.rygel.outerstellar.platform.web.PluginContext
import io.github.rygel.outerstellar.platform.web.webContext
import io.github.rygel.stellar.service.CommunityService
import io.github.rygel.stellar.service.FeedService
import io.github.rygel.stellar.web.TimeFormatting.toCardViewModel
import org.http4k.contract.ContractRoute
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.lens.string
import org.http4k.template.ViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SearchRoutes(private val context: PluginContext) : KoinComponent {
    private val feedService: FeedService by inject()
    private val communityService: CommunityService by inject()
    private val renderer = context.renderer

    private val queryLens = Query.string().optional("q")
    private val limitLens = Query.int().defaulted("limit", 25)
    private val offsetLens = Query.int().defaulted("offset", 0)

    val routes: List<ContractRoute> =
        listOf(
            "/search" meta
                {
                    summary = "Search posts and communities"
                } bindContract
                GET to
                { request ->
                    val query = queryLens(request)
                    val limit = limitLens(request)
                    val offset = offsetLens(request)
                    val userId = request.webContext.user?.id

                    val posts =
                        if (query != null) {
                            feedService.searchPosts(query, limit, offset, userId)
                        } else {
                            emptyList()
                        }
                    val communities =
                        if (query != null) {
                            communityService.search(query, 10)
                        } else {
                            emptyList()
                        }

                    val page =
                        SearchResultsPage(
                            query = query ?: "",
                            posts = posts.map { it.toCardViewModel() },
                            communities = communities.map { it.toCardViewModel() },
                        )
                    renderPage(request, "Search", "/search", page)
                }
        )

    private fun renderPage(
        request: org.http4k.core.Request,
        title: String,
        section: String,
        data: ViewModel,
    ): Response =
        Response(Status.OK)
            .header("content-type", "text/html; charset=utf-8")
            .body(renderer(context.buildPage(request, title, section, data)))
}
