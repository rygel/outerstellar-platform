package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.security.UserRole
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class SessionTimeoutIntegrationTest : WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var activeUser: User
    private lateinit var expiredUser: User
    private lateinit var activeToken: String
    private lateinit var expiredToken: String
    private lateinit var securityService: SecurityService

    @BeforeEach
    fun setupTest() {
        securityService =
            SecurityService(
                userRepository,
                encoder,
                sessionRepository = sessionRepository,
                apiKeyRepository = apiKeyRepository,
                resetRepository = passwordResetRepository,
                auditRepository = auditRepository,
            )

        activeUser = createTestUser("activeuser", "active@test.com")
        expiredUser = createTestUser("expireduser", "expired@test.com")

        userRepository.save(activeUser)
        userRepository.save(expiredUser)

        activeToken = securityService.createSession(activeUser.id)
        expiredToken = securityService.createSession(expiredUser.id)

        testDsl.execute(
            "UPDATE plt_sessions SET expires_at = CURRENT_TIMESTAMP - INTERVAL '2 hours'" +
                " WHERE user_id = '${expiredUser.id}'"
        )

        app = buildApp(securityService = securityService)
    }

    private fun createTestUser(name: String, email: String): User =
        User(
            id = UUID.randomUUID(),
            username = name,
            email = email,
            passwordHash = encoder.encode(testPassword()),
            role = UserRole.USER,
        )

    @AfterEach fun teardown() = cleanup()

    @Test
    fun `expired bearer token returns 401 with X-Session-Expired header`() {
        val response = app(Request(GET, "/api/v1/sync").header("Authorization", "Bearer $expiredToken"))

        assertEquals(Status.UNAUTHORIZED, response.status)
        assertEquals("true", response.header("X-Session-Expired"), "X-Session-Expired header must be set to true")
    }

    @Test
    fun `active bearer token is not expired and accesses sync endpoint`() {
        val response = app(Request(GET, "/api/v1/sync").header("Authorization", "Bearer $activeToken"))

        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `fresh user with null lastActivityAt is not expired`() {
        val freshUser =
            User(
                id = UUID.randomUUID(),
                username = "freshuser",
                email = "fresh@test.com",
                passwordHash = "x".repeat(60),
                role = UserRole.USER,
            )
        userRepository.save(freshUser)
        val freshToken = securityService.createSession(freshUser.id)

        val response = app(Request(GET, "/api/v1/sync").header("Authorization", "Bearer $freshToken"))

        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `expired session cookie on HTML route redirects to auth with expired param`() {
        val response = app(Request(GET, "/").cookie(Cookie(WebContext.SESSION_COOKIE, expiredToken)))

        assertEquals(Status.FOUND, response.status, "Expired session should cause redirect")
        val location = response.header("location").orEmpty()
        assertTrue(location.contains("expired"), "Redirect location should indicate session expired, got: $location")
    }

    @Test
    fun `active session cookie on HTML route is accepted`() {
        val response = app(Request(GET, "/").cookie(Cookie(WebContext.SESSION_COOKIE, activeToken)))

        assertEquals(Status.OK, response.status, "Active session should succeed")
    }

    @Test
    fun `expired bearer token response body mentions expiry`() {
        val response = app(Request(GET, "/api/v1/sync").header("Authorization", "Bearer $expiredToken"))

        val body = response.bodyString()
        assertTrue(
            body.contains("expired", ignoreCase = true) || body.contains("Session"),
            "Response body should mention expiry, got: $body",
        )
    }
}
