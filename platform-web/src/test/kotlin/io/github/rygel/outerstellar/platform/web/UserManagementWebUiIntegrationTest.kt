package io.github.rygel.outerstellar.platform.web

import com.natpryce.hamkrest.assertion.assertThat
import io.github.rygel.outerstellar.platform.model.AuthTokenResponse
import io.github.rygel.outerstellar.platform.model.LoginRequest
import io.github.rygel.outerstellar.platform.model.RegisterRequest
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.OPTIONS
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.cookie
import org.http4k.core.with
import org.http4k.format.KotlinxSerialization.auto
import org.http4k.hamkrest.hasStatus
import org.junit.jupiter.api.BeforeEach

/**
 * Integration tests for web UI rendering, navigation, CORS, security headers, correlation IDs, health check, rate
 * limiting, and session timeout.
 */
class UserManagementWebUiIntegrationTest : WebTest() {

    private lateinit var app: HttpHandler

    private val loginLens = Body.auto<LoginRequest>().toLens()
    private val registerLens = Body.auto<RegisterRequest>().toLens()
    private val tokenLens = Body.auto<AuthTokenResponse>().toLens()

    @BeforeEach
    fun setupTest() {
        app = buildApp()
    }

    private data class RegisteredUser(val id: UUID, val token: String, val sessionToken: String)

    private fun registerUser(username: String, password: String): RegisteredUser {
        val response =
            app(Request(POST, "/api/v1/auth/register").with(registerLens of RegisterRequest(username, password)))
        assertThat(response, hasStatus(Status.OK))
        val auth = tokenLens(response)
        val userId = userRepository.findByUsername(username)!!.id
        val sessionToken = sessionSvc.createSession(userId)
        return RegisteredUser(userId, auth.token, sessionToken)
    }

    private data class AdminInfo(val id: UUID, val token: String, val password: String)

    private fun seedAdmin(): AdminInfo {
        val adminId = UUID.randomUUID()
        val password = "Adm1nP@ss!"
        userRepository.save(
            User(
                id = adminId,
                username = "admin",
                email = "admin@test.com",
                passwordHash = encoder.encode(password),
                role = UserRole.ADMIN,
            )
        )
        val token = sessionSvc.createSession(adminId)
        return AdminInfo(adminId, token, password)
    }

    // ---- User Admin (HTML routes) ----

    @Test
    fun `admin can access user admin page`() {
        val admin = seedAdmin()
        val response =
            app(Request(GET, "/admin/users").cookie(org.http4k.core.cookie.Cookie("app_session", admin.token)))
        assertThat(response, hasStatus(Status.OK))
        assertTrue(response.bodyString().contains("User Administration"))
    }

    @Test
    fun `non-admin cannot access user admin page`() {
        val userAuth = registerUser("nonadminuser", testPassword())
        val response =
            app(
                Request(GET, "/admin/users").cookie(org.http4k.core.cookie.Cookie("app_session", userAuth.sessionToken))
            )
        assertThat(response, hasStatus(Status.FORBIDDEN))
    }

    @Test
    fun `admin can toggle user enabled via HTML form`() {
        val admin = seedAdmin()
        val userAuth = registerUser("toggleuser", testPassword())
        app(
            Request(POST, "/admin/users/${userAuth.id}/toggle-enabled")
                .cookie(org.http4k.core.cookie.Cookie("app_session", admin.token))
        )
        val user = userRepository.findByUsername("toggleuser")!!
        assertFalse(user.enabled)
    }

    @Test
    fun `admin can toggle user role via HTML form`() {
        val admin = seedAdmin()
        val userAuth = registerUser("roleuser", testPassword())
        app(
            Request(POST, "/admin/users/${userAuth.id}/toggle-role")
                .cookie(org.http4k.core.cookie.Cookie("app_session", admin.token))
        )
        val user = userRepository.findByUsername("roleuser")!!
        assertEquals(UserRole.ADMIN, user.role)
    }

    // ---- Session Timeout ----

    @Test
    fun `session timeout redirects HTML requests to auth page`() {
        val admin = seedAdmin()
        testJdbi.useHandle<Exception> { handle ->
            handle.execute(
                "UPDATE plt_sessions SET expires_at = TIMESTAMP '2020-01-01 00:00:00' WHERE user_id = '${admin.id}'"
            )
        }
        val response = app(Request(GET, "/").cookie(org.http4k.core.cookie.Cookie("app_session", admin.token)))
        assertThat(response, hasStatus(Status.FOUND))
        assertThat(response, org.http4k.hamkrest.hasHeader("location", "/auth?expired=true"))
    }

    @Test
    fun `active session is not expired`() {
        val admin = seedAdmin()
        val response =
            app(Request(GET, "/admin/users").cookie(org.http4k.core.cookie.Cookie("app_session", admin.token)))
        assertThat(response, hasStatus(Status.OK))
    }

    // ---- Navigation ----

    @Test
    fun `admin user sees Users nav link`() {
        val admin = seedAdmin()
        val response = app(Request(GET, "/").cookie(org.http4k.core.cookie.Cookie("app_session", admin.token)))
        assertTrue(response.bodyString().contains("/admin/users"))
    }

    @Test
    fun `regular user does not see Users nav link`() {
        val userAuth = registerUser("navuser", testPassword())
        val response =
            app(Request(GET, "/").cookie(org.http4k.core.cookie.Cookie("app_session", userAuth.sessionToken)))
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
        assertThat(response, hasStatus(Status.NO_CONTENT))
        assertNotNull(response.header("Access-Control-Allow-Origin"))
    }

    // ---- Security Headers ----

    @Test
    fun `security headers present on responses`() {
        val response = app(Request(GET, "/health"))
        assertThat(response, org.http4k.hamkrest.hasHeader("X-Content-Type-Options", "nosniff"))
        assertThat(response, org.http4k.hamkrest.hasHeader("X-Frame-Options", "DENY"))
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
        assertThat(response, hasStatus(Status.OK))
        val body = response.bodyString()
        assertTrue(body.contains("\"status\""))
        assertTrue(body.contains("\"database\""))
    }

    // ---- Web UI ----

    @Test
    fun `home page renders inside layout shell`() {
        val admin = seedAdmin()
        val response = app(Request(GET, "/").cookie(org.http4k.core.cookie.Cookie("app_session", admin.token)))
        assertThat(response, hasStatus(Status.OK))
        val body = response.bodyString()
        assertTrue(body.contains("drawer lg:drawer-open"))
        assertTrue(body.contains("drawer-side"))
    }

    @Test
    fun `error pages render inside layout shell`() {
        val response = app(Request(GET, "/nonexistent-page"))
        assertThat(response, hasStatus(Status.NOT_FOUND))
        assertTrue(response.bodyString().contains("drawer lg:drawer-open"))
    }
}
