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

class NotificationRoutes(
    private val pageFactory: WebPageFactory,
    private val renderer: TemplateRenderer,
    private val notificationService: NotificationService,
) : ServerRoutes {
    private val notificationIdPath = Path.string().of("notificationId")

    override val routes: List<ContractRoute> =
        listOf(
            "/notifications" meta
                {
                    summary = "In-app notifications page"
                } bindContract
                GET to
                { request ->
                    val ctx = request.webContext
                    if (ctx.user == null) {
                        Response(Status.FOUND).header("location", "/auth")
                    } else {
                        renderer.render(pageFactory.buildNotificationsPage(ctx))
                    }
                },
            "/notifications/read-all" meta
                {
                    summary = "Mark all notifications as read"
                } bindContract
                POST to
                { request ->
                    val ctx = request.webContext
                    val user = ctx.user
                    if (user == null) {
                        Response(Status.FORBIDDEN)
                    } else {
                        notificationService.markAllRead(user.id)
                        // Re-render the page via HTMX redirect
                        Response(Status.FOUND).header("location", "/notifications")
                    }
                },
            "/notifications" / notificationIdPath / "read" meta
                {
                    summary = "Mark a single notification as read"
                } bindContract
                POST to
                { notificationId, _ ->
                    { request ->
                        val ctx = request.webContext
                        val user = ctx.user
                        if (user == null) {
                            Response(Status.FORBIDDEN)
                        } else {
                            try {
                                notificationService.markRead(
                                    UUID.fromString(notificationId),
                                    user.id,
                                )
                            } catch (_: IllegalArgumentException) {
                                // ignore invalid UUID
                            }
                            Response(Status.FOUND).header("location", "/notifications")
                        }
                    }
                },
            "/components/notification-bell" meta
                {
                    summary = "Notification bell fragment (unread count)"
                } bindContract
                GET to
                { request ->
                    val ctx = request.webContext
                    val fragment = pageFactory.buildNotificationBell(ctx)
                    Response(Status.OK)
                        .header("content-type", "text/html; charset=utf-8")
                        .body(renderer(fragment))
                },
        )
}
