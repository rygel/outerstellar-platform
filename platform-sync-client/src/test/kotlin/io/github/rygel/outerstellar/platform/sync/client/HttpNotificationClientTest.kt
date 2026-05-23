package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.model.NotificationSummary
import kotlin.test.assertEquals
import org.http4k.core.Body
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.KotlinxSerialization.auto
import org.junit.jupiter.api.Test

class HttpNotificationClientTest {

    private val session = ApiSession()

    private fun makeClient(handler: (Request) -> Response): HttpNotificationClient =
        HttpNotificationClient("http://localhost:8080", session, handler)

    private fun withAuth(handler: (Request) -> Response): HttpNotificationClient {
        session.apiToken = "tok"
        return makeClient(handler)
    }

    @Test
    fun `listNotifications returns list`() {
        val notifs = listOf(NotificationSummary("n1", "Title", "Body", "INFO", false, "2026-01-01T00:00:00Z"))
        val client = withAuth { Response(Status.OK).with(Body.auto<List<NotificationSummary>>().toLens() of notifs) }
        val result = client.listNotifications()
        assertEquals(1, result.size)
        assertEquals("n1", result[0].id)
    }

    @Test
    fun `listNotifications returns empty on non-OK`() {
        val client = withAuth { Response(Status.INTERNAL_SERVER_ERROR) }
        val result = client.listNotifications()
        assertEquals(0, result.size)
    }

    @Test
    fun `listNotifications sends auth header`() {
        var authHeader: String? = null
        val client = withAuth { req ->
            authHeader = req.header("Authorization")
            Response(Status.OK).with(Body.auto<List<NotificationSummary>>().toLens() of emptyList())
        }
        client.listNotifications()
        assertEquals("Bearer tok", authHeader)
    }

    @Test
    fun `markNotificationRead requires auth`() {
        var called = false
        var authHeader: String? = null
        val client = withAuth { req ->
            called = true
            authHeader = req.header("Authorization")
            Response(Status.OK)
        }
        client.markNotificationRead("notif-1")
        assert(called)
        assertEquals("Bearer tok", authHeader)
    }

    @Test
    fun `markAllNotificationsRead requires auth`() {
        var called = false
        var authHeader: String? = null
        val client = withAuth { req ->
            called = true
            authHeader = req.header("Authorization")
            Response(Status.OK)
        }
        client.markAllNotificationsRead()
        assert(called)
        assertEquals("Bearer tok", authHeader)
    }
}
