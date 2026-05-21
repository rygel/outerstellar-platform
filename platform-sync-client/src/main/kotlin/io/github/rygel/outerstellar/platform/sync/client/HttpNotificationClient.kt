package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.model.NotificationSummary
import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.PUT
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.format.KotlinxSerialization.auto

class HttpNotificationClient(
    private val baseUrl: String,
    private val session: ApiSession,
    private val client: HttpHandler,
) : NotificationClient {

    private val notificationListLens = Body.auto<List<NotificationSummary>>().toLens()

    override fun listNotifications(): List<NotificationSummary> {
        val response = authenticated(Request(GET, "$baseUrl/api/v1/notifications"))
        checkSessionExpired(response)
        if (response.status != Status.OK) return emptyList()
        return notificationListLens(response)
    }

    override fun markNotificationRead(notificationId: String) {
        val response = authenticated(Request(PUT, "$baseUrl/api/v1/notifications/$notificationId/read"))
        checkSessionExpired(response)
    }

    override fun markAllNotificationsRead() {
        val response = authenticated(Request(PUT, "$baseUrl/api/v1/notifications/read-all"))
        checkSessionExpired(response)
    }

    private fun authenticated(request: Request): Response {
        val authed = session.apiToken?.let { request.header("Authorization", "Bearer $it") } ?: request
        return client(authed)
    }

    private fun checkSessionExpired(response: Response) {
        if (response.status == Status.UNAUTHORIZED && response.header("X-Session-Expired") == "true") {
            throw SessionExpiredException()
        }
    }
}
