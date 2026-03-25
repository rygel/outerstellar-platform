package io.github.rygel.stellar.web

import io.github.rygel.outerstellar.platform.web.PluginContext
import io.github.rygel.outerstellar.platform.web.webContext
import io.github.rygel.stellar.model.PostSort
import io.github.rygel.stellar.model.TimeRange
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

class FeedRoutes(private val context: PluginContext) : KoinComponent {
    private val feedService: FeedService by inject()
    private val communityService: CommunityService by inject()
    private val renderer = context.renderer

    private val sortLens = Query.string().defaulted("sort", "hot")
    private val timeLens = Query.string().optional("t")
    private val limitLens = Query.int().defaulted("limit", 25)
    private val offsetLens = Query.int().defaulted("offset", 0)

    val routes: List<ContractRoute> =
        listOf(
            "/" meta
                {
                    summary = "Home feed"
                } bindContract
                GET to
                { request ->
                    val sort = parseSort(sortLens(request))
                    val timeRange = parseTimeRange(timeLens(request))
                    val limit = limitLens(request)
                    val offset = offsetLens(request)
                    val userId = request.webContext.user?.id

                    val posts = feedService.homeFeed(userId, sort, timeRange, limit, offset)
                    val communities = communityService.listPopular(10)
                    val page =
                        FeedPage(
                            feedType = "home",
                            sort = sortLens(request),
                            timeRange = timeLens(request),
                            posts = posts.map { it.toCardViewModel() },
                            communities = communities.map { it.toCardViewModel() },
                            hasMore = posts.size == limit,
                            nextOffset = offset + limit,
                        )
                    renderPage(request, "Home", "/", page)
                },
            "/popular" meta
                {
                    summary = "Popular posts"
                } bindContract
                GET to
                { request ->
                    val sort = parseSort(sortLens(request))
                    val timeRange = parseTimeRange(timeLens(request))
                    val limit = limitLens(request)
                    val offset = offsetLens(request)
                    val userId = request.webContext.user?.id

                    val posts = feedService.popularFeed(sort, timeRange, limit, offset, userId)
                    val communities = communityService.listPopular(10)
                    val page =
                        FeedPage(
                            feedType = "popular",
                            sort = sortLens(request),
                            timeRange = timeLens(request),
                            posts = posts.map { it.toCardViewModel() },
                            communities = communities.map { it.toCardViewModel() },
                            hasMore = posts.size == limit,
                            nextOffset = offset + limit,
                        )
                    renderPage(request, "Popular", "/popular", page)
                },
            "/components/feed" meta
                {
                    summary = "Feed fragment for infinite scroll"
                } bindContract
                GET to
                { request ->
                    val sort = parseSort(sortLens(request))
                    val timeRange = parseTimeRange(timeLens(request))
                    val limit = limitLens(request)
                    val offset = offsetLens(request)
                    val userId = request.webContext.user?.id

                    val posts = feedService.popularFeed(sort, timeRange, limit, offset, userId)
                    val fragment =
                        FeedListFragment(
                            posts = posts.map { it.toCardViewModel() },
                            hasMore = posts.size == limit,
                            nextOffset = offset + limit,
                            sort = sortLens(request),
                            feedType = "all",
                        )
                    Response(Status.OK).header("content-type", "text/html; charset=utf-8").body(renderer(fragment))
                },
        )

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
        data: ViewModel,
    ): Response =
        Response(Status.OK)
            .header("content-type", "text/html; charset=utf-8")
            .body(renderer(context.buildPage(request, title, section, data)))
}
