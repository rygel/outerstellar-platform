package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.persistence.JooqNotificationRepository
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.service.NotificationService
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Page-rendering smoke tests for every platform route.
 *
 * Each test verifies:
 * - Correct HTTP status
 * - text/html content type
 * - Valid HTML shell (DOCTYPE, html, body tags)
 * - Page-specific content marker
 * - No error indicators in the response
 *
 * Routes tested:
 * - Home (/), Trash (/messages/trash)
 * - Contacts (/contacts)
 * - Notifications (/notifications)
 * - Search (/search)
 * - Settings (/settings, /settings?tab=password, /settings?tab=api-keys, /settings?tab=notifications)
 * - Admin/Users (/admin/users), Admin/Audit (/admin/audit)
 * - Dev Dashboard (/admin/dev)
 * - Auth (/auth) — logged-out
 * - 404 — unknown path
 * - Health (/health) — JSON
 */
class PlatformPageRenderingTest : WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var adminUser: User
    private lateinit var regularUser: User
    private lateinit var securityService: SecurityService
    private lateinit var adminToken: String
    private lateinit var userToken: String

    @BeforeEach
    fun setupTest() {
        adminUser =
            User(
                id = UUID.randomUUID(),
                username = "pagerender_admin",
                email = "pagerender_admin@test.com",
                passwordHash = encoder.encode(testPassword()),
                role = UserRole.ADMIN,
            )
        regularUser =
            User(
                id = UUID.randomUUID(),
                username = "pagerender_user",
                email = "pagerender_user@test.com",
                passwordHash = encoder.encode(testPassword()),
                role = UserRole.USER,
            )
        userRepository.save(adminUser)
        userRepository.save(regularUser)

        securityService =
            SecurityService(
                userRepository,
                encoder,
                sessionRepository = sessionRepository,
                apiKeyRepository = apiKeyRepository,
                resetRepository = passwordResetRepository,
                auditRepository = auditRepository,
            )
        adminToken = securityService.createSession(adminUser.id)
        userToken = securityService.createSession(regularUser.id)

        val notificationService = NotificationService(JooqNotificationRepository(testDsl))
        app =
            buildApp(
                securityService = securityService,
                overrides = TestOverrides(notificationService = notificationService),
            )
    }

    @AfterEach fun teardown() = cleanup()

    private fun adminSession() = Cookie(WebContext.SESSION_COOKIE, adminToken)

    private fun userSession() = Cookie(WebContext.SESSION_COOKIE, userToken)

    private fun assertHtmlPage(response: org.http4k.core.Response, path: String) {
        assertEquals(Status.OK, response.status, "Expected 200 for $path")
        val contentType = response.header("content-type").orEmpty()
        assertTrue(contentType.contains("text/html"), "Expected text/html for $path, got: $contentType")
        val body = response.bodyString()
        assertTrue(body.contains("<!DOCTYPE html>") || body.contains("<html"), "$path should contain HTML document")
        assertTrue(body.contains("</html>"), "$path should have closing html tag")
        assertTrue(!body.contains("Exception") && !body.contains("Stacktrace"), "$path should not contain error traces")
    }

    // ---- Home ----

    @Test
    fun `home page renders with message composer`() {
        val response = app(Request(GET, "/").cookie(userSession()))
        assertHtmlPage(response, "/")
        val body = response.bodyString()
        assertTrue(
            body.contains("message-list-container") || body.contains("server-side message"),
            "/ should contain message list or composer marker",
        )
    }

    // ---- Trash ----

    @Test
    fun `trash page renders`() {
        val response = app(Request(GET, "/messages/trash").cookie(userSession()))
        assertHtmlPage(response, "/messages/trash")
        val body = response.bodyString()
        assertTrue(body.contains("Trash") || body.contains("trash"), "/messages/trash should contain trash marker")
    }

    // ---- Contacts ----

    @Test
    fun `contacts page renders with directory heading`() {
        val response = app(Request(GET, "/contacts").cookie(userSession()))
        assertHtmlPage(response, "/contacts")
        val body = response.bodyString()
        assertTrue(body.contains("Contacts") || body.contains("contacts"), "/contacts should contain contacts marker")
    }

    // ---- Notifications ----

    @Test
    fun `notifications page renders`() {
        val response = app(Request(GET, "/notifications").cookie(userSession()))
        assertHtmlPage(response, "/notifications")
        val body = response.bodyString()
        assertTrue(
            body.contains("Notification") || body.contains("notification"),
            "/notifications should contain notification marker",
        )
    }

    // ---- Search ----

    @Test
    fun `search page renders with empty query`() {
        val response = app(Request(GET, "/search?q=").cookie(userSession()))
        assertHtmlPage(response, "/search")
        val body = response.bodyString()
        assertTrue(body.contains("Search") || body.contains("search"), "/search should contain search marker")
    }

    @Test
    fun `search page renders with query`() {
        val response = app(Request(GET, "/search?q=test").cookie(userSession()))
        assertHtmlPage(response, "/search?q=test")
    }

    // ---- Settings ----

    @Test
    fun `settings page renders default profile tab`() {
        val response = app(Request(GET, "/settings").cookie(userSession()))
        assertHtmlPage(response, "/settings")
        val body = response.bodyString()
        assertTrue(body.contains("Settings") || body.contains("settings"), "/settings should contain settings marker")
        assertTrue(body.contains("profile"), "/settings default tab should be profile")
    }

    @Test
    fun `settings page renders password tab`() {
        val response = app(Request(GET, "/settings?tab=password").cookie(userSession()))
        assertHtmlPage(response, "/settings?tab=password")
        val body = response.bodyString()
        assertTrue(body.contains("password"), "/settings?tab=password should contain password marker")
    }

    @Test
    fun `settings page renders api-keys tab`() {
        val response = app(Request(GET, "/settings?tab=api-keys").cookie(userSession()))
        assertHtmlPage(response, "/settings?tab=api-keys")
        val body = response.bodyString()
        assertTrue(
            body.contains("api-key") || body.contains("API"),
            "/settings?tab=api-keys should contain api-key marker",
        )
    }

    @Test
    fun `settings page renders notifications tab`() {
        val response = app(Request(GET, "/settings?tab=notifications").cookie(userSession()))
        assertHtmlPage(response, "/settings?tab=notifications")
        val body = response.bodyString()
        assertTrue(
            body.contains("Notification") || body.contains("notification"),
            "/settings?tab=notifications should contain notification marker",
        )
    }

    // ---- Admin: Users ----

    @Test
    fun `admin users page renders for admin`() {
        val response = app(Request(GET, "/admin/users").cookie(adminSession()))
        assertHtmlPage(response, "/admin/users")
        val body = response.bodyString()
        assertTrue(
            body.contains("pagerender_admin") || body.contains("pagerender_user") || body.contains("Users"),
            "/admin/users should contain user data or Users heading",
        )
    }

    // ---- Admin: Audit ----

    @Test
    fun `admin audit page renders for admin`() {
        val response = app(Request(GET, "/admin/audit").cookie(adminSession()))
        assertHtmlPage(response, "/admin/audit")
        val body = response.bodyString()
        assertTrue(body.contains("Audit") || body.contains("audit"), "/admin/audit should contain audit marker")
    }

    // ---- Dev Dashboard ----

    @Test
    fun `dev dashboard renders for admin`() {
        val response = app(Request(GET, "/admin/dev").cookie(adminSession()))
        assertHtmlPage(response, "/admin/dev")
        val body = response.bodyString()
        assertTrue(body.contains("Outbox") || body.contains("outbox"), "/admin/dev should contain outbox section")
    }

    // ---- Auth (logged-out) ----

    @Test
    fun `auth page renders without session`() {
        val response = app(Request(GET, "/auth"))
        assertHtmlPage(response, "/auth")
        val body = response.bodyString()
        assertTrue(
            body.contains("Sign") || body.contains("sign") || body.contains("Register") || body.contains("register"),
            "/auth should contain auth form marker",
        )
    }

    // ---- 404 ----

    @Test
    fun `unknown path returns 404`() {
        val response = app(Request(GET, "/this-page-does-not-exist"))
        assertEquals(Status.NOT_FOUND, response.status)
    }

    // ---- Health (JSON) ----

    @Test
    fun `health endpoint returns JSON with UP status`() {
        val response = app(Request(GET, "/health"))
        assertEquals(Status.OK, response.status)
        val contentType = response.header("content-type").orEmpty()
        assertTrue(contentType.contains("application/json"), "/health should return JSON")
        val body = response.bodyString()
        assertTrue(body.contains("UP"), "/health should report UP status")
    }

    // ---- Security headers on pages ----

    @Test
    fun `authenticated page includes standard security headers`() {
        val response = app(Request(GET, "/").cookie(userSession()))
        assertEquals("nosniff", response.header("X-Content-Type-Options"))
        assertEquals("DENY", response.header("X-Frame-Options"))
        assertNotNull(response.header("X-Request-Id"), "Pages should include X-Request-Id")
    }
}
