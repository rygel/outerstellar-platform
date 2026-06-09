package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.infra.render
import io.github.rygel.outerstellar.platform.model.VoteScore
import io.github.rygel.outerstellar.platform.service.PollService
import io.github.rygel.outerstellar.platform.service.VoteService
import org.http4k.contract.bindContract
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.lens.Path
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.lens.string
import org.http4k.template.TemplateRenderer

private const val DEFAULT_LIMIT = 10
private const val MAX_LIMIT = 100
private const val VOTE_PATH_PREFIX = "/components/messages/"
private const val VOTE_PATH_SUFFIX = "/vote"

class ComponentRoutes(
    private val sidebarFactory: SidebarFactory,
    private val homePageFactory: HomePageFactory,
    private val infraPageFactory: InfraPageFactory,
    private val renderer: TemplateRenderer,
    private val voteService: VoteService? = null,
    private val pollService: PollService? = null,
) : ServerRoutes {
    private val queryLens = Query.string().optional("q")
    private val limitLens = Query.int().defaulted("limit", DEFAULT_LIMIT)
    private val offsetLens = Query.int().defaulted("offset", 0)
    private val yearLens = Query.int().optional("year")
    private val optionIdLens = Query.string().required("optionId")
    private val pollSyncIdPath = Path.string().of("syncId")

    val footerStatusRoute =
        "/components/footer-status" meta
            {
                summary = "Footer status fragment"
            } bindContract
            GET to
            { request: org.http4k.core.Request ->
                renderer.render(infraPageFactory.buildFooterStatus(request.shellRenderer))
            }

    val publicRoutes =
        listOf(
            footerStatusRoute,
            "/components/navigation/page" meta
                {
                    summary = "Theme/Lang/Layout refresh"
                } bindContract
                GET to
                { request: org.http4k.core.Request ->
                    val pagePath = request.query("pagePath")?.ifBlank { "/" } ?: "/"
                    val forwardParams =
                        request.uri.query.split("&").filter { !it.startsWith("pagePath=") }.joinToString("&")
                    val redirectUrl = if (forwardParams.isBlank()) pagePath else "$pagePath?$forwardParams"
                    Response(Status.OK).header("HX-Redirect", redirectUrl)
                },
            "/components/sidebar/theme-selector" meta
                {
                    summary = "Sidebar theme selector fragment"
                } bindContract
                GET to
                { request: org.http4k.core.Request ->
                    renderer.render(sidebarFactory.buildThemeSelector(request.requestContext, request.shellRenderer))
                },
            "/components/sidebar/language-selector" meta
                {
                    summary = "Sidebar language selector fragment"
                } bindContract
                GET to
                { request: org.http4k.core.Request ->
                    renderer.render(sidebarFactory.buildLanguageSelector(request.requestContext, request.shellRenderer))
                },
            "/components/sidebar/layout-selector" meta
                {
                    summary = "Sidebar layout selector fragment"
                } bindContract
                GET to
                { request: org.http4k.core.Request ->
                    renderer.render(sidebarFactory.buildLayoutSelector(request.requestContext, request.shellRenderer))
                },
        ) + votePublicRoutes() + pollPublicRoutes()

    val protectedRoutes =
        listOf(
            "/components/message-list" meta
                {
                    summary = "Message list fragment"
                    queries += queryLens
                    queries += limitLens
                    queries += offsetLens
                    queries += yearLens
                } bindContract
                GET to
                { request: org.http4k.core.Request ->
                    val ctx = request.requestContext
                    val shellRenderer = request.shellRenderer
                    val query = queryLens(request)
                    val limit = limitLens(request).coerceIn(1, MAX_LIMIT)
                    val offset = offsetLens(request).coerceAtLeast(0)
                    val year = yearLens(request)
                    renderer.render(homePageFactory.buildMessageList(ctx, shellRenderer, query, limit, offset, year))
                }
        ) + voteProtectedRoutes() + pollProtectedRoutes()

    override val routes = publicRoutes + protectedRoutes

    private fun votePublicRoutes() =
        if (voteService == null) {
            emptyList()
        } else {
            val vs = voteService
            listOf(
                "/components/messages/{syncId}/vote" meta
                    {
                        summary = "Vote fragment for a message"
                    } bindContract
                    GET to
                    { request: org.http4k.core.Request ->
                        val syncId = extractVoteSyncId(request) ?: return@to Response(Status.BAD_REQUEST)
                        val ctx = request.requestContext
                        val userId = ctx.user?.id
                        val score = vs.getScore(syncId, userId)
                        renderer.render(VoteFragmentViewModel(score, syncId))
                    }
            )
        }

    private fun voteProtectedRoutes() =
        if (voteService == null) {
            emptyList()
        } else {
            val vs = voteService
            listOf(
                "/components/messages/{syncId}/vote" meta
                    {
                        summary = "Submit a vote on a message"
                    } bindContract
                    POST to
                    { request: org.http4k.core.Request ->
                        val syncId = extractVoteSyncId(request) ?: return@to Response(Status.BAD_REQUEST)
                        val user = request.requestContext.user!!
                        val direction =
                            parseMessageVoteDirection(request)
                                ?: return@to Response(Status.BAD_REQUEST).body("direction must be 1 or -1")
                        val score = vs.vote(syncId, user.id, direction) ?: VoteScore(syncId, 0, 0, 0, null)
                        renderer.render(VoteFragmentViewModel(score, syncId))
                    }
            )
        }

    private fun pollPublicRoutes() =
        if (pollService == null) {
            emptyList()
        } else {
            val ps = pollService
            listOf(
                "/components/polls" / pollSyncIdPath meta
                    {
                        summary = "Poll card fragment"
                    } bindContract
                    GET to
                    { syncId ->
                        { request: org.http4k.core.Request ->
                            val ctx = request.requestContext
                            val results = ps.getPoll(syncId, ctx.user?.id) ?: return@to Response(Status.NOT_FOUND)
                            renderer.render(PollFragmentViewModel(results, syncId))
                        }
                    }
            )
        }

    private fun pollProtectedRoutes() =
        if (pollService == null) {
            emptyList()
        } else {
            val ps = pollService
            listOf(
                "/components/polls" / pollSyncIdPath / "vote" meta
                    {
                        summary = "Cast a vote on a poll option"
                    } bindContract
                    POST to
                    { syncId, _ ->
                        { request: org.http4k.core.Request ->
                            val user = request.requestContext.user!!
                            val optionId =
                                request.form("optionId")?.toLongOrNull() ?: return@to Response(Status.BAD_REQUEST)
                            val results =
                                try {
                                    ps.castVote(syncId, optionId, user.id)
                                } catch (e: IllegalStateException) {
                                    return@to Response(Status.CONFLICT).body(e.message ?: "Poll cannot accept votes")
                                } catch (e: IllegalArgumentException) {
                                    return@to Response(Status.BAD_REQUEST).body(e.message ?: "Invalid poll vote")
                                } ?: return@to Response(Status.NOT_FOUND)
                            renderer.render(PollFragmentViewModel(results, syncId))
                        }
                    },
                "/components/polls" / pollSyncIdPath / "vote" meta
                    {
                        summary = "Remove a vote from a poll option"
                    } bindContract
                    DELETE to
                    { syncId, _ ->
                        { request: org.http4k.core.Request ->
                            val user = request.requestContext.user!!
                            val optionId =
                                optionIdLens(request).toLongOrNull() ?: return@to Response(Status.BAD_REQUEST)
                            ps.removeVote(syncId, optionId, user.id)
                            val results = ps.getPoll(syncId, user.id) ?: return@to Response(Status.NOT_FOUND)
                            renderer.render(PollFragmentViewModel(results, syncId))
                        }
                    },
            )
        }

    private fun extractVoteSyncId(request: org.http4k.core.Request): String? {
        val path = request.uri.path
        if (!path.startsWith(VOTE_PATH_PREFIX) || !path.endsWith(VOTE_PATH_SUFFIX)) return null
        val syncId = path.removePrefix(VOTE_PATH_PREFIX).removeSuffix(VOTE_PATH_SUFFIX)
        return syncId.ifBlank { null }
    }

    private fun parseMessageVoteDirection(request: org.http4k.core.Request): Int? =
        when (val direction = request.form("direction")?.toIntOrNull()) {
            1,
            -1 -> direction
            else -> null
        }
}
