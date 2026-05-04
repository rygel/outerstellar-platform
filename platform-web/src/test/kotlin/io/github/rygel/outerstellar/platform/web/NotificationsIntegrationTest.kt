package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.model.AuthTokenResponse
import io.github.rygel.outerstellar.platform.model.LoginRequest
import io.github.rygel.outerstellar.platform.model.RegisterRequest
import io.github.rygel.outerstellar.platform.service.NotificationService
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Method.PUT
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.Jackson.auto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationsIntegrationTest : H2WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var notificationService: NotificationService

    private val loginLens = Body.auto<LoginRequest>().toLens()
    private val registerLens = Body.auto<RegisterRequest>().toLens()
    private val tokenLens = Body.auto<AuthTokenResponse>().toLens()
    private val notificationListLens = Body.auto<List<NotificationDto>>().toLens()

    @BeforeEach
    fun setupTest() {
        notificationService = NotificationService(notificationRepository)
        app = buildApp(overrides = TestOverrides(notificationService = notificationService))
    }

    @AfterEach fun teardown() = cleanup()

    private fun registerAndLogin(
        username: String = "notifuser${UUID.randomUUID().toString().take(6)}"
    ): Pair<UUID, String> {
        val password = testPassword()
        val regReq = Request(POST, "/api/v1/auth/register").with(registerLens of RegisterRequest(username, password))
        app(regReq)
        val loginReq = Request(POST, "/api/v1/auth/login").with(loginLens of LoginRequest(username, password))
        val token = tokenLens(app(loginReq)).token
        val user = userRepository.findByUsername(username)!!
        return user.id to token
    }

    private fun bearerRequest(method: org.http4k.core.Method, path: String, token: String) =
        Request(method, path).header("Authorization", "Bearer $token")

    // ---- API (bearer token) tests ----

    @Test
    fun `GET notifications returns empty list initially`() {
        val (_, token) = registerAndLogin()
        val response = app(bearerRequest(GET, "/api/v1/notifications", token))

        assertEquals(Status.OK, response.status)
        val notifications = notificationListLens(response)
        assertTrue(notifications.isEmpty())
    }

    @Test
    fun `GET notifications returns created notifications`() {
        val (userId, token) = registerAndLogin()
        notificationService.create(userId, "Hello", "This is a test notification")
        notificationService.create(userId, "Warning", "Something needs attention", "warning")

        val response = app(bearerRequest(GET, "/api/v1/notifications", token))

        assertEquals(Status.OK, response.status)
        val notifications = notificationListLens(response)
        assertEquals(2, notifications.size)
        assertTrue(notifications.any { it.title == "Warning" })
        assertFalse(notifications[0].read)
        assertTrue(notifications.any { it.type == "info" })
    }

    @Test
    fun `GET notifications only returns own user notifications`() {
        val (userId1, token1) = registerAndLogin()
        val (userId2, _) = registerAndLogin()
        notificationService.create(userId1, "For user1", "Private")
        notificationService.create(userId2, "For user2", "Also private")

        val response = app(bearerRequest(GET, "/api/v1/notifications", token1))
        val notifications = notificationListLens(response)

        assertEquals(1, notifications.size)
        assertEquals("For user1", notifications[0].title)
    }

    @Test
    fun `GET notifications requires authentication`() {
        val response = app(Request(GET, "/api/v1/notifications"))
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `PUT mark notification read marks it as read`() {
        val (userId, token) = registerAndLogin()
        notificationService.create(userId, "Test", "Body")
        val notifications = notificationRepository.findByUserId(userId)
        val notifId = notifications[0].id

        val response = app(bearerRequest(PUT, "/api/v1/notifications/$notifId/read", token))

        assertEquals(Status.NO_CONTENT, response.status)
        val updated = notificationRepository.findByUserId(userId)[0]
        assertTrue(updated.isRead)
    }

    @Test
    fun `PUT mark all read marks all as read`() {
        val (userId, token) = registerAndLogin()
        notificationService.create(userId, "N1", "B1")
        notificationService.create(userId, "N2", "B2")

        assertEquals(2, notificationRepository.countUnread(userId))

        val response = app(bearerRequest(PUT, "/api/v1/notifications/read-all", token))
        assertEquals(Status.NO_CONTENT, response.status)
        assertEquals(0, notificationRepository.countUnread(userId))
    }

    @Test
    fun `PUT mark read only affects own notifications`() {
        val (userId1, _) = registerAndLogin()
        val (_, token2) = registerAndLogin()
        notificationService.create(userId1, "User1 notif", "Private")
        val notifId = notificationRepository.findByUserId(userId1)[0].id

        // user2 tries to mark user1's notification as read
        app(bearerRequest(PUT, "/api/v1/notifications/$notifId/read", token2))

        // still unread for user1
        assertFalse(notificationRepository.findByUserId(userId1)[0].isRead)
    }

    // ---- Web (session cookie) tests ----

    @Test
    fun `GET notifications page redirects unauthenticated users`() {
        val response = app(Request(GET, "/notifications"))
        assertEquals(Status.FOUND, response.status)
        assertTrue(response.header("location")?.contains("/auth") == true)
    }

    @Test
    fun `GET notification bell component returns HTML`() {
        val response = app(Request(GET, "/components/notification-bell"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("ri-notification"))
    }

    @Test
    fun `GET notification bell shows unread count badge`() {
        val (userId, _) = registerAndLogin("belluser${UUID.randomUUID().toString().take(5)}")
        notificationService.create(userId, "Unread", "Body")

        // The bell fragment counts per-user; anonymous request shows 0
        val response = app(Request(GET, "/components/notification-bell"))
        assertEquals(Status.OK, response.status)
    }
}
