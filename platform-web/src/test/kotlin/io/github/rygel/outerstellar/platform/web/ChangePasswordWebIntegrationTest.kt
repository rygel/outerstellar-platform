package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.User
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
 * Integration tests for the change-password web form endpoints.
 *
 * Covers:
 * - GET /auth/change-password redirects unauthenticated users to /auth
 * - GET /auth/change-password returns 200 for authenticated users
 * - POST /auth/components/change-password returns 401 without session
 * - POST /auth/components/change-password with wrong current password returns error fragment
 * - POST /auth/components/change-password with password mismatch returns error fragment
 * - POST /auth/components/change-password with weak new password returns error fragment
 * - POST /auth/components/change-password succeeds and returns success fragment
 * - New password works for subsequent API login after successful change
 */
class ChangePasswordWebIntegrationTest : WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var testUser: User
    private lateinit var securityService: SecurityService
    private lateinit var testToken: String

    @BeforeEach
    fun setupTest() {
        testUser =
            User(
                id = UUID.randomUUID(),
                username = "changepwuser@test.com",
                email = "changepwuser@test.com",
                passwordHash = encoder.encode("OldPass123!"),
                role = UserRole.USER,
            )
        userRepository.save(testUser)

        securityService =
            SecurityService(
                userRepository,
                encoder,
                sessionRepository = sessionRepository,
                apiKeyRepository = apiKeyRepository,
                resetRepository = passwordResetRepository,
                auditRepository = auditRepository,
            )

        testToken = securityService.createSession(testUser.id)

        app = buildApp(securityService = securityService)
    }

    @AfterEach fun teardown() = cleanup()

    private fun sessionCookie() = Cookie(WebContext.SESSION_COOKIE, testToken)

    private fun changePasswordRequest(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String,
    ): org.http4k.core.Response =
        app(
            Request(POST, "/auth/components/change-password")
                .cookie(sessionCookie())
                .header("content-type", "application/x-www-form-urlencoded")
                .body(
                    "currentPassword=${encode(currentPassword)}" +
                        "&newPassword=${encode(newPassword)}" +
                        "&confirmPassword=${encode(confirmPassword)}"
                )
        )

    private fun encode(value: String) = java.net.URLEncoder.encode(value, "UTF-8")

    @Test
    fun `GET change-password redirects unauthenticated to auth`() {
        val response = app(Request(GET, "/auth/change-password"))
        assertEquals(Status.FOUND, response.status)
        val location = response.header("location") ?: ""
        assertTrue(location.contains("/auth"), "Should redirect to /auth, got: $location")
    }

    @Test
    fun `GET change-password returns 200 for authenticated user`() {
        val response = app(Request(GET, "/auth/change-password").cookie(sessionCookie()))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `GET change-password returns HTML for authenticated user`() {
        val response = app(Request(GET, "/auth/change-password").cookie(sessionCookie()))
        val body = response.bodyString()
        assertTrue(body.contains("html", ignoreCase = true), "Response should be HTML")
    }

    @Test
    fun `POST change-password without session returns 401`() {
        val response =
            app(
                Request(POST, "/auth/components/change-password")
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body("currentPassword=old&newPassword=new&confirmPassword=new")
            )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `POST change-password with correct passwords returns success fragment`() {
        val response = changePasswordRequest("OldPass123!", "NewPass456!", "NewPass456!")
        assertEquals(Status.OK, response.status)
        val body = response.bodyString()
        // Should render a success fragment (not an error)
        assertTrue(
            body.contains("success", ignoreCase = true) || body.contains("panel-success"),
            "Should return success fragment, got: ${body.take(300)}",
        )
    }

    @Test
    fun `POST change-password with mismatched confirm returns error fragment`() {
        val response = changePasswordRequest("OldPass123!", "NewPass456!", "DifferentPass!")
        assertEquals(Status.OK, response.status)
        val body = response.bodyString()
        assertTrue(
            body.contains("mismatch", ignoreCase = true) ||
                body.contains("panel-danger") ||
                body.contains("error", ignoreCase = true),
            "Should return mismatch error fragment, got: ${body.take(300)}",
        )
    }

    @Test
    fun `POST change-password with wrong current password returns error`() {
        val response = changePasswordRequest("WrongCurrentPass!", "NewPass456!", "NewPass456!")
        // SecurityService.changePassword with wrong current password should result in an error
        val body = response.bodyString()
        assertTrue(
            body.contains("panel-danger") ||
                body.contains("error", ignoreCase = true) ||
                body.contains("invalid", ignoreCase = true) ||
                response.status != Status.OK && response.status.code >= 400,
            "Wrong current password should produce an error, got status=${response.status} body=${body.take(300)}",
        )
    }

    @Test
    fun `POST change-password with weak new password returns error fragment`() {
        val response = changePasswordRequest("OldPass123!", "weak", "weak")
        assertEquals(Status.OK, response.status)
        val body = response.bodyString()
        assertTrue(
            body.contains("panel-danger") || body.contains("error", ignoreCase = true),
            "Weak password should return error fragment, got: ${body.take(300)}",
        )
    }

    @Test
    fun `new password authenticates successfully after change`() {
        val newPassword = "BrandNew789!"

        // Change the password
        val changeResponse = changePasswordRequest("OldPass123!", newPassword, newPassword)
        assertEquals(Status.OK, changeResponse.status)
        val changeBody = changeResponse.bodyString()
        assertTrue(
            changeBody.contains("success", ignoreCase = true) || changeBody.contains("panel-success"),
            "Password change should succeed before testing new password",
        )

        // Verify old password no longer works
        val oldAuth = securityService.authenticate(testUser.email, "OldPass123!")
        assertTrue(oldAuth == null, "Old password should no longer authenticate")

        // Verify new password works
        val newAuth = securityService.authenticate(testUser.email, newPassword)
        assertNotNull(newAuth, "New password should authenticate successfully")
    }
}
