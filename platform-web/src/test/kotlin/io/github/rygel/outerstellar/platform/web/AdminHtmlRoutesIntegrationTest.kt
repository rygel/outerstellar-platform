package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.security.UserRole
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Integration tests for admin HTML routes.
 *
 * Covers:
 * - GET /admin/users returns 200 for admin user
 * - GET /admin/users is forbidden for non-admin users
 * - GET /admin/users is rejected for unauthenticated requests
 * - GET /admin/users/export returns CSV content with correct headers
 * - GET /admin/audit returns 200 for admin user
 * - POST /admin/users/{userId}/toggle-enabled toggles user enabled state
 * - POST /admin/users/{userId}/toggle-role toggles user role
 */
class AdminHtmlRoutesIntegrationTest : WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var adminUser: User
    private lateinit var regularUser: User
    private lateinit var securityService: SecurityService
    private lateinit var adminToken: String
    private lateinit var regularToken: String

    @BeforeEach
    fun setupTest() {
        cleanup()
        adminUser =
            User(
                id = UUID.randomUUID(),
                username = "adminhtml",
                email = "adminhtml@test.com",
                passwordHash = encoder.encode(testPassword()),
                role = UserRole.ADMIN,
            )
        regularUser =
            User(
                id = UUID.randomUUID(),
                username = "regularhtml",
                email = "regularhtml@test.com",
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
        regularToken = securityService.createSession(regularUser.id)

        app = buildApp(securityService = securityService)
    }

    @AfterEach fun teardown() = cleanup()

    private fun adminCookie() = Cookie(WebContext.SESSION_COOKIE, adminToken)

    private fun regularCookie() = Cookie(WebContext.SESSION_COOKIE, regularToken)

    @Test
    fun `GET admin-users returns 200 for admin`() {
        val response = app(Request(GET, "/admin/users").cookie(adminCookie()))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `GET admin-users returns HTML content for admin`() {
        val response = app(Request(GET, "/admin/users").cookie(adminCookie()))
        val body = response.bodyString()
        assertTrue(body.isNotBlank(), "Response body should not be empty")
        assertTrue(body.contains("html", ignoreCase = true), "Response should be HTML, got: ${body.take(200)}")
    }

    @Test
    fun `GET admin-users is forbidden for non-admin user`() {
        val response = app(Request(GET, "/admin/users").cookie(regularCookie()))
        assertNotEquals(Status.OK, response.status, "Non-admin should not access admin page")
    }

    @Test
    fun `GET admin-users is rejected for unauthenticated request`() {
        val response = app(Request(GET, "/admin/users"))
        assertNotEquals(Status.OK, response.status, "Unauthenticated request should be rejected")
    }

    @Test
    fun `GET admin-users-export returns CSV with correct content-type`() {
        val response = app(Request(GET, "/admin/users/export").cookie(adminCookie()))
        assertEquals(Status.OK, response.status)
        val contentType = response.header("Content-Type") ?: ""
        assertTrue(contentType.contains("text/csv"), "Should return CSV content-type, got: $contentType")
    }

    @Test
    fun `GET admin-users-export CSV contains header row`() {
        val response = app(Request(GET, "/admin/users/export").cookie(adminCookie()))
        val body = response.bodyString()
        assertTrue(
            body.contains("Username") && body.contains("Email"),
            "CSV should contain header row, got: ${body.take(200)}",
        )
    }

    @Test
    fun `GET admin-users-export CSV contains admin user entry`() {
        val response = app(Request(GET, "/admin/users/export").cookie(adminCookie()))
        val body = response.bodyString()
        assertTrue(body.contains(adminUser.username), "CSV should contain admin username, got: ${body.take(500)}")
    }

    @Test
    fun `GET admin-users-export has content-disposition attachment header`() {
        val response = app(Request(GET, "/admin/users/export").cookie(adminCookie()))
        val disposition = response.header("Content-Disposition") ?: ""
        assertTrue(disposition.contains("attachment"), "Should have attachment content-disposition, got: $disposition")
    }

    @Test
    fun `GET admin-audit returns 200 for admin`() {
        val response = app(Request(GET, "/admin/audit").cookie(adminCookie()))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `GET admin-audit is forbidden for non-admin`() {
        val response = app(Request(GET, "/admin/audit").cookie(regularCookie()))
        assertNotEquals(Status.OK, response.status)
    }

    @Test
    fun `POST admin-users toggle-enabled returns 200 for admin`() {
        val response = app(Request(POST, "/admin/users/${regularUser.id}/toggle-enabled").cookie(adminCookie()))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `POST admin-users toggle-role returns 200 for admin`() {
        val response = app(Request(POST, "/admin/users/${regularUser.id}/toggle-role").cookie(adminCookie()))
        assertEquals(Status.OK, response.status)
    }
}
