package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.app
import io.github.rygel.outerstellar.platform.infra.createRenderer
import io.github.rygel.outerstellar.platform.model.AuthTokenResponse
import io.github.rygel.outerstellar.platform.model.LoginRequest
import io.github.rygel.outerstellar.platform.model.RegisterRequest
import io.github.rygel.outerstellar.platform.persistence.JooqApiKeyRepository
import io.github.rygel.outerstellar.platform.persistence.JooqAuditRepository
import io.github.rygel.outerstellar.platform.persistence.JooqMessageRepository
import io.github.rygel.outerstellar.platform.persistence.JooqPasswordResetRepository
import io.github.rygel.outerstellar.platform.persistence.JooqSessionRepository
import io.github.rygel.outerstellar.platform.persistence.JooqUserRepository
import io.github.rygel.outerstellar.platform.security.BCryptPasswordEncoder
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.security.UserRole
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.OPTIONS
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.cookie
import org.http4k.core.with
import org.http4k.format.Jackson.auto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for web UI rendering, navigation, CORS, security headers, correlation IDs, health check, rate
 * limiting, and session timeout.
 */
class UserManagementWebUiIntegrationTest : H2WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var userRepository: JooqUserRepository
    private lateinit var encoder: BCryptPasswordEncoder
    private lateinit var securityService: SecurityService

    private val loginLens = Body.auto<LoginRequest>().toLens()
    private val registerLens = Body.auto<RegisterRequest>().toLens()
    private val tokenLens = Body.auto<AuthTokenResponse>().toLens()

    @BeforeEach
    fun setupTest() {
        userRepository = JooqUserRepository(testDsl)
        val repository = JooqMessageRepository(testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val transactionManager = StubTransactionManager()
        val messageService =
            io.github.rygel.outerstellar.platform.service.MessageService(repository, outbox, transactionManager, cache)
        encoder = BCryptPasswordEncoder(logRounds = 4)
        val auditRepository = JooqAuditRepository(testDsl)
        val resetRepository = JooqPasswordResetRepository(testDsl)
        val apiKeyRepository = JooqApiKeyRepository(testDsl)
        securityService =
            SecurityService(
                userRepository,
                encoder,
                auditRepository,
                resetRepository,
                apiKeyRepository,
                sessionRepository = JooqSessionRepository(testDsl),
            )
        val contactService =
            io.mockk.mockk<io.github.rygel.outerstellar.platform.service.ContactService>(relaxed = true)
        val pageFactory = WebPageFactory(repository, messageService, contactService, securityService)

        app =
            app(
                messageService,
                contactService,
                outbox,
                cache,
                createRenderer(),
                pageFactory,
                testConfig,
                securityService,
                userRepository,
            )
                .http!!
    }

    @AfterEach fun teardown() = cleanup()

    private data class RegisteredUser(val id: UUID, val token: String)

    private fun registerUser(username: String, password: String): RegisteredUser {
        val response =
            app(Request(POST, "/api/v1/auth/register").with(registerLens of RegisterRequest(username, password)))
        assertEquals(Status.OK, response.status)
        val auth = tokenLens(response)
        val userId = userRepository.findByUsername(username)!!.id
        return RegisteredUser(userId, auth.token)
    }

    private data class AdminInfo(val id: UUID, val token: String, val password: String)

    private fun seedAdmin(): AdminInfo {
        val adminId = UUID.randomUUID()
        val password = "adminpass123"
        userRepository.save(
            User(
                id = adminId,
                username = "admin",
                email = "admin@test.com",
                passwordHash = encoder.encode(password),
                role = UserRole.ADMIN,
            )
        )
        val token = securityService.createSession(adminId)
        return AdminInfo(adminId, token, password)
    }

    // ---- User Admin (HTML routes) ----

    @Test
    fun `admin can access user admin page`() {
        val admin = seedAdmin()
        val response =
            app(Request(GET, "/admin/users").cookie(org.http4k.core.cookie.Cookie("app_session", admin.id.toString())))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("User Administration"))
    }

    @Test
    fun `non-admin cannot access user admin page`() {
        val userAuth = registerUser("nonadminuser", "password123")
        val response =
            app(
                Request(GET, "/admin/users")
                    .cookie(org.http4k.core.cookie.Cookie("app_session", userAuth.id.toString()))
            )
        assertEquals(Status.FORBIDDEN, response.status)
    }

    @Test
    fun `admin can toggle user enabled via HTML form`() {
        val admin = seedAdmin()
        val userAuth = registerUser("toggleuser", "password123")
        app(
            Request(POST, "/admin/users/${userAuth.id}/toggle-enabled")
                .cookie(org.http4k.core.cookie.Cookie("app_session", admin.id.toString()))
        )
        val user = userRepository.findByUsername("toggleuser")!!
        assertFalse(user.enabled)
    }

    @Test
    fun `admin can toggle user role via HTML form`() {
        val admin = seedAdmin()
        val userAuth = registerUser("roleuser", "password123")
        app(
            Request(POST, "/admin/users/${userAuth.id}/toggle-role")
                .cookie(org.http4k.core.cookie.Cookie("app_session", admin.id.toString()))
        )
        val user = userRepository.findByUsername("roleuser")!!
        assertEquals(UserRole.ADMIN, user.role)
    }

    // ---- Session Timeout ----

    @Test
    fun `session timeout redirects HTML requests to auth page`() {
        val admin = seedAdmin()
        testDsl.execute(
            "UPDATE users SET last_activity_at = " + "TIMESTAMP '2020-01-01 00:00:00' " + "WHERE id = '${admin.id}'"
        )
        val response = app(Request(GET, "/").cookie(org.http4k.core.cookie.Cookie("app_session", admin.id.toString())))
        assertEquals(Status.FOUND, response.status)
        assertEquals("/auth?expired=true", response.header("location"))
    }

    @Test
    fun `active session is not expired`() {
        val admin = seedAdmin()
        testDsl.execute(
            "UPDATE users SET last_activity_at = " +
                "TIMESTAMPADD(MINUTE, -5, CURRENT_TIMESTAMP) " +
                "WHERE id = '${admin.id}'"
        )
        val response =
            app(Request(GET, "/admin/users").cookie(org.http4k.core.cookie.Cookie("app_session", admin.id.toString())))
        assertEquals(Status.OK, response.status)
    }

    // ---- Navigation ----

    @Test
    fun `admin user sees Users nav link`() {
        val admin = seedAdmin()
        val response = app(Request(GET, "/").cookie(org.http4k.core.cookie.Cookie("app_session", admin.id.toString())))
        assertTrue(response.bodyString().contains("/admin/users"))
    }

    @Test
    fun `regular user does not see Users nav link`() {
        val userAuth = registerUser("navuser", "password123")
        val response =
            app(Request(GET, "/").cookie(org.http4k.core.cookie.Cookie("app_session", userAuth.id.toString())))
        assertFalse(response.bodyString().contains("/admin/users"))
    }

    // ---- Rate Limiting ----

    @Test
    fun `rate limiter blocks after exceeding limit`() {
        var blockedCount = 0
        repeat(12) {
            val response =
                app(
                    Request(POST, "/api/v1/auth/login")
                        .header("X-Forwarded-For", "10.0.0.99")
                        .with(loginLens of LoginRequest("nobody", "nopass"))
                )
            if (response.status == Status.TOO_MANY_REQUESTS) {
                blockedCount++
            }
        }
        assertTrue(blockedCount > 0)
    }

    // ---- CORS ----

    @Test
    fun `cors preflight returns correct headers`() {
        val response =
            app(
                Request(OPTIONS, "/api/v1/auth/login")
                    .header("Origin", "http://localhost:3000")
                    .header("Access-Control-Request-Method", "POST")
            )
        assertEquals(Status.NO_CONTENT, response.status)
        assertNotNull(response.header("Access-Control-Allow-Origin"))
    }

    // ---- Security Headers ----

    @Test
    fun `security headers present on responses`() {
        val response = app(Request(GET, "/health"))
        assertEquals("nosniff", response.header("X-Content-Type-Options"))
        assertEquals("DENY", response.header("X-Frame-Options"))
    }

    // ---- Correlation ID ----

    @Test
    fun `correlation id is generated when not provided`() {
        val response = app(Request(GET, "/health"))
        assertNotNull(response.header("X-Request-Id"))
    }

    @Test
    fun `correlation id is forwarded when provided`() {
        val customId = "test-correlation-id-12345"
        val response = app(Request(GET, "/health").header("X-Request-Id", customId))
        assertEquals(customId, response.header("X-Request-Id"))
    }

    // ---- Health Check ----

    @Test
    fun `health endpoint returns json with db status`() {
        val response = app(Request(GET, "/health"))
        assertEquals(Status.OK, response.status)
        val body = response.bodyString()
        assertTrue(body.contains("\"status\""))
        assertTrue(body.contains("\"database\""))
    }

    // ---- Web UI ----

    @Test
    fun `home page renders inside layout shell`() {
        val response = app(Request(GET, "/"))
        assertEquals(Status.OK, response.status)
        val body = response.bodyString()
        assertTrue(body.contains("class=\"shell\""))
        assertTrue(body.contains("class=\"sidebar\""))
    }

    @Test
    fun `error pages render inside layout shell`() {
        val response = app(Request(GET, "/nonexistent-page"))
        assertEquals(Status.NOT_FOUND, response.status)
        assertTrue(response.bodyString().contains("class=\"shell\""))
    }
}
