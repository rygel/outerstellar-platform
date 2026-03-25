package io.github.rygel.stellar.web

import io.github.rygel.outerstellar.platform.web.PluginContext
import io.github.rygel.outerstellar.platform.web.webContext
import io.github.rygel.stellar.model.CommunityRule
import io.github.rygel.stellar.model.PostSort
import io.github.rygel.stellar.model.TimeRange
import io.github.rygel.stellar.persistence.CommunityRuleRepository
import io.github.rygel.stellar.service.CommunityService
import io.github.rygel.stellar.service.ModerationService
import io.github.rygel.stellar.service.PostService
import io.github.rygel.stellar.service.StellarRateLimiter
import io.github.rygel.stellar.web.TimeFormatting.toCardViewModel
import java.util.UUID
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
    private val moderationService: ModerationService by inject()
    private val ruleRepo: CommunityRuleRepository by inject()
    private val rateLimiter: StellarRateLimiter by inject()
    private val renderer = context.renderer

    private val communityNamePath = Path.string().of("name")
    private val ruleIdPath = Path.string().of("ruleId")
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
                    if (!rateLimiter.checkLimit("create:$userId")) {
                        return@to Response(Status.TOO_MANY_REQUESTS)
                            .body("Rate limit exceeded. Please wait before creating another community.")
                    }

                    val name = request.form("name") ?: return@to Response(Status.BAD_REQUEST)
                    val displayName = request.form("displayName") ?: name
                    val description = request.form("description") ?: ""

                    communityService.createCommunity(name, displayName, description, userId)
                    Response(Status.SEE_OTHER).header("Location", "/r/$name")
                },
            // GET /r/{name}/settings — community settings form (mod only)
            "/r" / communityNamePath / "settings" meta
                {
                    summary = "Community settings"
                } bindContract
                GET to
                { name: String, _ ->
                    { request: Request ->
                        val userId = request.webContext.user?.id ?: return@to Response(Status.UNAUTHORIZED)
                        val community = communityService.getCommunity(name)

                        if (!moderationService.isModerator(userId, community.id)) {
                            return@to renderPage(
                                request,
                                "Forbidden",
                                "/r/$name",
                                ErrorPage(
                                    code = 403,
                                    title = "Forbidden",
                                    message = "You must be a moderator to access community settings.",
                                ),
                            )
                        }

                        val rules = ruleRepo.listByCommunity(community.id)
                        val moderators = moderationService.listModerators(community.id)
                        val userRepo = context.userRepository

                        val page =
                            CommunitySettingsPage(
                                community =
                                    CommunityDetailViewModel(
                                        id = community.id,
                                        name = community.name,
                                        displayName = community.displayName,
                                        description = community.description,
                                        sidebar = community.sidebar,
                                        subscriberCount = community.subscriberCount,
                                        postCount = 0,
                                        isNsfw = community.isNsfw,
                                    ),
                                rules =
                                    rules.map {
                                        CommunityRuleViewModel(
                                            id = it.id,
                                            title = it.title,
                                            description = it.description,
                                            position = it.position,
                                        )
                                    },
                                moderators =
                                    moderators.map { member ->
                                        val user = userRepo.findById(member.userId)
                                        ModeratorViewModel(
                                            userId = member.userId,
                                            username = user?.username ?: member.userId.toString(),
                                            role = member.role.name,
                                        )
                                    },
                            )
                        renderPage(request, "Settings - r/$name", "/r/$name", page)
                    }
                },
            // POST /r/{name}/settings — update community settings
            "/r" / communityNamePath / "settings" meta
                {
                    summary = "Update community settings"
                } bindContract
                POST to
                { name: String, _ ->
                    { request: Request ->
                        val userId = request.webContext.user?.id ?: return@to Response(Status.UNAUTHORIZED)
                        val community = communityService.getCommunity(name)

                        if (!moderationService.isModerator(userId, community.id)) {
                            return@to Response(Status.FORBIDDEN)
                        }

                        val description = request.form("description") ?: community.description
                        val sidebar = request.form("sidebar") ?: community.sidebar
                        val isNsfw = request.form("isNsfw") != null

                        val updated =
                            community.copy(description = description.trim(), sidebar = sidebar.trim(), isNsfw = isNsfw)
                        communityService.updateCommunity(updated)

                        Response(Status.SEE_OTHER).header("Location", "/r/$name/settings")
                    }
                },
            // POST /r/{name}/rules — add rule
            "/r" / communityNamePath / "rules" meta
                {
                    summary = "Add community rule"
                } bindContract
                POST to
                { name: String, _ ->
                    { request: Request ->
                        val userId = request.webContext.user?.id ?: return@to Response(Status.UNAUTHORIZED)
                        val community = communityService.getCommunity(name)

                        if (!moderationService.isModerator(userId, community.id)) {
                            return@to Response(Status.FORBIDDEN)
                        }

                        val title = request.form("title") ?: return@to Response(Status.BAD_REQUEST)
                        val ruleDescription = request.form("description") ?: ""
                        val existingRules = ruleRepo.listByCommunity(community.id)
                        val nextPosition = if (existingRules.isEmpty()) 0 else existingRules.maxOf { it.position } + 1

                        val rule =
                            CommunityRule(
                                id = UUID.randomUUID(),
                                communityId = community.id,
                                title = title.trim(),
                                description = ruleDescription.trim(),
                                position = nextPosition,
                            )
                        ruleRepo.create(rule)

                        Response(Status.SEE_OTHER).header("Location", "/r/$name/settings")
                    }
                },
            // POST /r/{name}/rules/{ruleId}/delete — delete rule
            "/r" / communityNamePath / "rules" / ruleIdPath / "delete" meta
                {
                    summary = "Delete community rule"
                } bindContract
                POST to
                { name: String, _, ruleId: String, _ ->
                    { request: Request ->
                        val userId = request.webContext.user?.id ?: return@to Response(Status.UNAUTHORIZED)
                        val community = communityService.getCommunity(name)

                        if (!moderationService.isModerator(userId, community.id)) {
                            return@to Response(Status.FORBIDDEN)
                        }

                        ruleRepo.delete(UUID.fromString(ruleId))

                        Response(Status.SEE_OTHER).header("Location", "/r/$name/settings")
                    }
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
