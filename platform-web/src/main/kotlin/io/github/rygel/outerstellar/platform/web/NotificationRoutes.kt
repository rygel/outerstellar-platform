package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.infra.render
import io.github.rygel.outerstellar.platform.service.NotificationService
import java.util.UUID
import org.http4k.contract.ContractRoute
import org.http4k.contract.bindContract
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.lens.Path
import org.http4k.lens.string
import org.http4k.template.TemplateRenderer
import org.slf4j.LoggerFactory

class NotificationRoutes(
    private val adminPageFactory: AdminPageFactory,
    private val renderer: TemplateRenderer,
    private val notificationService: NotificationService,
) : ServerRoutes {
    private val notificationIdPath = Path.string().of("notificationId")
    private val logger = LoggerFactory.getLogger(NotificationRoutes::class.java)

    val publicRoutes: List<ContractRoute> =
        listOf(
            "/components/notification-bell" meta
                {
                    summary = "Notification bell fragment (unread count)"
                } bindContract
                GET to
                { request ->
                    val ctx = request.requestContext
                    val shellRenderer = request.shellRenderer
                    val fragment = adminPageFactory.buildNotificationBell(ctx, shellRenderer)
                    Response(Status.OK).header("content-type", "text/html; charset=utf-8").body(renderer(fragment))
                }
        )

    val protectedRoutes: List<ContractRoute> =
        listOf(
            "/notifications" meta
                {
                    summary = "In-app notifications page"
                } bindContract
                GET to
                { request ->
                    val ctx = request.requestContext
                    val shellRenderer = request.shellRenderer
                    renderer.render(adminPageFactory.buildNotificationsPage(ctx, shellRenderer))
                },
            "/notifications/read-all" meta
                {
                    summary = "Mark all notifications as read"
                } bindContract
                POST to
                { request ->
                    val user = request.requestContext.user!!
                    notificationService.markAllRead(user.id)
                    Response(Status.FOUND).header("location", "/notifications")
                },
            "/notifications" / notificationIdPath / "read" meta
                {
                    summary = "Mark a single notification as read"
                } bindContract
                POST to
                { notificationId, _ ->
                    { request ->
                        val user = request.requestContext.user!!
                        try {
                            notificationService.markRead(UUID.fromString(notificationId), user.id)
                        } catch (e: IllegalArgumentException) {
                            logger.debug("Invalid notification UUID: {}", e.message)
                        }
                        Response(Status.FOUND).header("location", "/notifications")
                    }
                },
        )

    override val routes: List<ContractRoute> = publicRoutes + protectedRoutes
}
