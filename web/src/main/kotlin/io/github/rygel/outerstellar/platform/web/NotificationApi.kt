package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.persistence.Notification
import io.github.rygel.outerstellar.platform.security.SecurityRules
import io.github.rygel.outerstellar.platform.service.NotificationService
import java.util.UUID
import org.http4k.contract.ContractRoute
import org.http4k.contract.bindContract
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Body
import org.http4k.core.Method.GET
import org.http4k.core.Method.PUT
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.Jackson.auto
import org.http4k.lens.Path
import org.http4k.lens.string

data class NotificationDto(
    val id: String,
    val title: String,
    val body: String,
    val type: String,
    val read: Boolean,
    val createdAt: String,
)

private fun Notification.toDto() =
    NotificationDto(
        id = id.toString(),
        title = title,
        body = body,
        type = type,
        read = isRead,
        createdAt = createdAt.toString(),
    )

class NotificationApi(private val notificationService: NotificationService) : ServerRoutes {
    private val notificationListLens = Body.auto<List<NotificationDto>>().toLens()
    private val notificationIdPath = Path.string().of("notificationId")

    override val routes: List<ContractRoute> =
        listOf(
            "/api/v1/notifications" meta
                {
                    summary = "List notifications for the authenticated user"
                } bindContract
                GET to
                { request ->
                    val user = SecurityRules.USER_KEY(request)!!
                    val notifications = notificationService.listForUser(user.id).map { it.toDto() }
                    Response(Status.OK).with(notificationListLens of notifications)
                },
            "/api/v1/notifications/read-all" meta
                {
                    summary = "Mark all notifications as read"
                } bindContract
                PUT to
                { request ->
                    val user = SecurityRules.USER_KEY(request)!!
                    notificationService.markAllRead(user.id)
                    Response(Status.NO_CONTENT)
                },
            "/api/v1/notifications" / notificationIdPath / "read" meta
                {
                    summary = "Mark a single notification as read"
                } bindContract
                PUT to
                { notificationId, _ ->
                    { request ->
                        val user = SecurityRules.USER_KEY(request)!!
                        try {
                            notificationService.markRead(UUID.fromString(notificationId), user.id)
                            Response(Status.NO_CONTENT)
                        } catch (e: IllegalArgumentException) {
                            Response(Status.BAD_REQUEST).body("Invalid notification id")
                        }
                    }
                },
        )
}
