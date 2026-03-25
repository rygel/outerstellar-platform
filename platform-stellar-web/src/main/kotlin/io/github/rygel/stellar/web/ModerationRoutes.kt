package io.github.rygel.stellar.web

import io.github.rygel.outerstellar.platform.web.PluginContext
import io.github.rygel.outerstellar.platform.web.webContext
import io.github.rygel.stellar.service.CommunityService
import io.github.rygel.stellar.service.ModerationService
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
import org.http4k.lens.string
import org.http4k.routing.path
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ModerationRoutes(private val context: PluginContext) : KoinComponent {
    private val moderationService: ModerationService by inject()
    private val communityService: CommunityService by inject()
    private val renderer = context.renderer

    private val communityNamePath = Path.string().of("name")
    private val reportIdPath = Path.string().of("reportId")
    private val postIdPath = Path.string().of("postId")
    private val commentIdPath = Path.string().of("commentId")
    private val userIdPath = Path.string().of("userId")

    val routes: List<ContractRoute> =
        listOf(
            "/r" / communityNamePath / "mod" meta
                {
                    summary = "Moderation dashboard"
                } bindContract
                GET to
                { name: String, _ ->
                    { request: Request ->
                        val userId = request.webContext.user?.id ?: return@to Response(Status.UNAUTHORIZED)
                        val community = communityService.getCommunity(name)

                        if (!moderationService.isModerator(userId, community.id)) {
                            return@to Response(Status.FORBIDDEN)
                        }

                        val pendingReports = moderationService.getPendingReports(community.id)
                        val moderators = moderationService.listModerators(community.id)

                        val page =
                            ModerationDashboardPage(
                                communityName = community.name,
                                pendingReportCount = pendingReports.size,
                                moderators =
                                    moderators.map {
                                        ModeratorViewModel(
                                            userId = it.userId,
                                            username = it.userId.toString(),
                                            role = it.role.name,
                                        )
                                    },
                            )
                        renderPage(request, "Mod Tools - r/${community.name}", "/r/${community.name}", page)
                    }
                },
            "/r/{name}/mod" / reportIdPath / "reports" meta
                {
                    summary = "Report queue"
                } bindContract
                GET to
                { _: String, _ ->
                    { request: Request ->
                        val userId = request.webContext.user?.id ?: return@to Response(Status.UNAUTHORIZED)
                        val name = request.path("name") ?: return@to Response(Status.BAD_REQUEST)
                        val community = communityService.getCommunity(name)

                        if (!moderationService.isModerator(userId, community.id)) {
                            return@to Response(Status.FORBIDDEN)
                        }

                        val reports = moderationService.getPendingReports(community.id)
                        val page =
                            ReportQueuePage(
                                communityName = community.name,
                                reports =
                                    reports.map { report ->
                                        ReportViewModel(
                                            id = report.id,
                                            reporterUsername = report.reporterId.toString(),
                                            targetId = report.targetId,
                                            targetType = report.targetType.name,
                                            reason = report.reason,
                                            createdAt = TimeFormatting.formatTimeAgo(report.createdAt),
                                        )
                                    },
                            )
                        renderPage(request, "Reports - r/${community.name}", "/r/${community.name}", page)
                    }
                },
            "/r/{name}/mod/reports" / reportIdPath / "resolve" meta
                {
                    summary = "Resolve report"
                } bindContract
                POST to
                { reportIdStr: String, _ ->
                    { request: Request ->
                        val userId = request.webContext.user?.id ?: return@to Response(Status.UNAUTHORIZED)
                        val name = request.path("name") ?: return@to Response(Status.BAD_REQUEST)
                        val community = communityService.getCommunity(name)

                        if (!moderationService.isModerator(userId, community.id)) {
                            return@to Response(Status.FORBIDDEN)
                        }

                        val reportId = UUID.fromString(reportIdStr)
                        moderationService.resolveReport(reportId, userId)
                        Response(Status.SEE_OTHER).header("Location", "/r/${community.name}/mod/reports")
                    }
                },
            "/r/{name}/mod/reports" / reportIdPath / "dismiss" meta
                {
                    summary = "Dismiss report"
                } bindContract
                POST to
                { reportIdStr: String, _ ->
                    { request: Request ->
                        val userId = request.webContext.user?.id ?: return@to Response(Status.UNAUTHORIZED)
                        val name = request.path("name") ?: return@to Response(Status.BAD_REQUEST)
                        val community = communityService.getCommunity(name)

                        if (!moderationService.isModerator(userId, community.id)) {
                            return@to Response(Status.FORBIDDEN)
                        }

                        val reportId = UUID.fromString(reportIdStr)
                        moderationService.dismissReport(reportId, userId)
                        Response(Status.SEE_OTHER).header("Location", "/r/${community.name}/mod/reports")
                    }
                },
            "/r/{name}/mod" / userIdPath / "ban" meta
                {
                    summary = "Ban user from community"
                } bindContract
                POST to
                { _: String, _ ->
                    { request: Request ->
                        val moderatorId = request.webContext.user?.id ?: return@to Response(Status.UNAUTHORIZED)
                        val name = request.path("name") ?: return@to Response(Status.BAD_REQUEST)
                        val community = communityService.getCommunity(name)

                        val userIdStr = request.form("userId") ?: return@to Response(Status.BAD_REQUEST)
                        val targetUserId = UUID.fromString(userIdStr)
                        val reason = request.form("reason")

                        moderationService.banUser(targetUserId, community.id, reason, moderatorId)
                        Response(Status.SEE_OTHER).header("Location", "/r/${community.name}/mod")
                    }
                },
            "/r/{name}/mod/unban" / userIdPath meta
                {
                    summary = "Unban user from community"
                } bindContract
                POST to
                { targetUserIdStr: String ->
                    { request: Request ->
                        val moderatorId = request.webContext.user?.id ?: return@to Response(Status.UNAUTHORIZED)
                        val name = request.path("name") ?: return@to Response(Status.BAD_REQUEST)
                        val community = communityService.getCommunity(name)

                        val targetUserId = UUID.fromString(targetUserIdStr)
                        moderationService.unbanUser(targetUserId, community.id, moderatorId)
                        Response(Status.SEE_OTHER).header("Location", "/r/${community.name}/mod")
                    }
                },
            "/r/{name}/posts" / postIdPath / "remove" meta
                {
                    summary = "Remove post"
                } bindContract
                POST to
                { postIdStr: String, _ ->
                    { request: Request ->
                        val moderatorId = request.webContext.user?.id ?: return@to Response(Status.UNAUTHORIZED)
                        val name = request.path("name") ?: return@to Response(Status.BAD_REQUEST)
                        val community = communityService.getCommunity(name)

                        val postId = UUID.fromString(postIdStr)
                        moderationService.removePost(postId, moderatorId, community.id)
                        Response(Status.SEE_OTHER).header("Location", "/r/${community.name}")
                    }
                },
            "/r/{name}/posts" / postIdPath / "lock" meta
                {
                    summary = "Lock or unlock post"
                } bindContract
                POST to
                { postIdStr: String, _ ->
                    { request: Request ->
                        val moderatorId = request.webContext.user?.id ?: return@to Response(Status.UNAUTHORIZED)
                        val name = request.path("name") ?: return@to Response(Status.BAD_REQUEST)
                        val community = communityService.getCommunity(name)

                        val postId = UUID.fromString(postIdStr)
                        moderationService.lockPost(postId, moderatorId, community.id)
                        Response(Status.SEE_OTHER).header("Location", "/r/${community.name}/posts/$postId")
                    }
                },
            "/r/{name}/posts" / postIdPath / "pin" meta
                {
                    summary = "Pin or unpin post"
                } bindContract
                POST to
                { postIdStr: String, _ ->
                    { request: Request ->
                        val moderatorId = request.webContext.user?.id ?: return@to Response(Status.UNAUTHORIZED)
                        val name = request.path("name") ?: return@to Response(Status.BAD_REQUEST)
                        val community = communityService.getCommunity(name)

                        val postId = UUID.fromString(postIdStr)
                        moderationService.pinPost(postId, moderatorId, community.id)
                        Response(Status.SEE_OTHER).header("Location", "/r/${community.name}/posts/$postId")
                    }
                },
            "/comments" / commentIdPath / "remove" meta
                {
                    summary = "Remove comment"
                } bindContract
                POST to
                { commentIdStr: String, _ ->
                    { request: Request ->
                        val moderatorId = request.webContext.user?.id ?: return@to Response(Status.UNAUTHORIZED)

                        val communityIdStr = request.form("communityId") ?: return@to Response(Status.BAD_REQUEST)
                        val communityId = UUID.fromString(communityIdStr)
                        val commentId = UUID.fromString(commentIdStr)

                        moderationService.removeComment(commentId, moderatorId, communityId)
                        Response(Status.OK)
                    }
                },
        )

    private fun renderPage(
        request: Request,
        title: String,
        section: String,
        data: org.http4k.template.ViewModel,
    ): Response =
        Response(Status.OK)
            .header("content-type", "text/html; charset=utf-8")
            .body(renderer(context.buildPage(request, title, section, data)))
}
