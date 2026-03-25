package io.github.rygel.stellar.web

import io.github.rygel.outerstellar.platform.web.PluginContext
import io.github.rygel.outerstellar.platform.web.webContext
import io.github.rygel.stellar.model.PostSort
import io.github.rygel.stellar.model.TimeRange
import io.github.rygel.stellar.service.CommunityService
import io.github.rygel.stellar.service.PostService
import io.github.rygel.stellar.web.TimeFormatting.toCardViewModel
import org.http4k.contract.ContractRoute
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.lens.Path
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.lens.string
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CommunityRoutes(private val context: PluginContext) : KoinComponent {
    private val communityService: CommunityService by inject()
    private val postService: PostService by inject()
    private val renderer = context.renderer

    private val communityNamePath = Path.string().of("name")
    private val sortLens = Query.string().defaulted("sort", "hot")
    private val timeLens = Query.string().optional("t")
    private val limitLens = Query.int().defaulted("limit", 25)
    private val offsetLens = Query.int().defaulted("offset", 0)
    private val queryLens = Query.string().optional("q")

    val routes: List<ContractRoute> =
        listOf(
            "/communities" meta
                {
                    summary = "Community directory"
                } bindContract
                GET to
                { request ->
                    val query = queryLens(request)
                    val communities =
                        if (query != null) {
                            communityService.search(query)
                        } else {
                            communityService.listPopular(50)
                        }
                    val page = CommunitiesPage(communities = communities.map { it.toCardViewModel() }, query = query)
                    renderPage(request, "Communities", "/communities", page)
                },
            "/r" / communityNamePath meta
                {
                    summary = "Community page"
                } bindContract
                GET to
                { name: String ->
                    { request: Request ->
                        val community = communityService.getCommunity(name)
                        val sort = parseSort(sortLens(request))
                        val timeRange = parseTimeRange(timeLens(request))
                        val limit = limitLens(request)
                        val offset = offsetLens(request)
                        val ctx = request.webContext

                        val posts = postService.listByCommunity(community.id, sort, timeRange, limit, offset)
                        val isSubscribed =
                            ctx.user?.id?.let { communityService.isSubscribed(it, community.id) } ?: false
                        val postCount = postService.countByCommunity(community.id)

                        val page =
                            CommunityPage(
                                community =
                                    CommunityDetailViewModel(
                                        id = community.id,
                                        name = community.name,
                                        displayName = community.displayName,
                                        description = community.description,
                                        sidebar = community.sidebar,
                                        subscriberCount = community.subscriberCount,
                                        postCount = postCount,
                                    ),
                                posts = posts.map { it.toCardViewModel() },
                                sort = sortLens(request),
                                timeRange = timeLens(request),
                                isSubscribed = isSubscribed,
                                hasMore = posts.size == limit,
                                nextOffset = offset + limit,
                            )
                        renderPage(request, "r/${community.name}", "/r/${community.name}", page)
                    }
                },
            "/r" / communityNamePath / "subscribe" meta
                {
                    summary = "Toggle subscription"
                } bindContract
                POST to
                { name: String, _ ->
                    { request: Request ->
                        val community = communityService.getCommunity(name)
                        val userId = request.webContext.user?.id ?: return@to Response(Status.UNAUTHORIZED)

                        val isSubscribed = communityService.isSubscribed(userId, community.id)
                        if (isSubscribed) {
                            communityService.unsubscribe(userId, community.id)
                        } else {
                            communityService.subscribe(userId, community.id)
                        }

                        val fragment =
                            SubscribeButtonFragment(
                                communityId = community.id,
                                communityName = community.name,
                                isSubscribed = !isSubscribed,
                            )
                        Response(Status.OK).header("content-type", "text/html; charset=utf-8").body(renderer(fragment))
                    }
                },
            "/communities/create" meta
                {
                    summary = "Create community form"
                } bindContract
                GET to
                { request ->
                    renderPage(request, "Create Community", "/communities", CreateCommunityPage())
                },
            "/communities" meta
                {
                    summary = "Create community"
                } bindContract
                POST to
                { request ->
                    val userId = request.webContext.user?.id ?: return@to Response(Status.UNAUTHORIZED)

                    val name = request.form("name") ?: return@to Response(Status.BAD_REQUEST)
                    val displayName = request.form("displayName") ?: name
                    val description = request.form("description") ?: ""

                    communityService.createCommunity(name, displayName, description, userId)
                    Response(Status.SEE_OTHER).header("Location", "/r/$name")
                },
        )

    private fun PostService.countByCommunity(communityId: java.util.UUID): Long =
        0L // Will be wired properly; placeholder for route compilation

    private fun parseSort(s: String): PostSort =
        when (s.lowercase()) {
            "new" -> PostSort.NEW
            "top" -> PostSort.TOP
            "rising" -> PostSort.RISING
            "controversial" -> PostSort.CONTROVERSIAL
            else -> PostSort.HOT
        }

    private fun parseTimeRange(t: String?): TimeRange =
        when (t?.lowercase()) {
            "hour" -> TimeRange.HOUR
            "day" -> TimeRange.DAY
            "week" -> TimeRange.WEEK
            "month" -> TimeRange.MONTH
            "year" -> TimeRange.YEAR
            else -> TimeRange.ALL
        }

    private fun renderPage(
        request: org.http4k.core.Request,
        title: String,
        section: String,
        data: org.http4k.template.ViewModel,
    ): Response =
        Response(Status.OK)
            .header("content-type", "text/html; charset=utf-8")
            .body(renderer(context.buildPage(request, title, section, data)))
}
