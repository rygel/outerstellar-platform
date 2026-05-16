package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.security.PasswordResetService
import io.github.rygel.outerstellar.platform.security.User
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.http4k.core.HttpHandler
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Integration tests for the full API password reset flow.
 *
 * Covers:
 * - POST /api/v1/auth/reset-request with valid email returns 200
 * - POST /api/v1/auth/reset-request with unknown email still returns 200 (no user leak)
 * - POST /api/v1/auth/reset-request stores a token in the DB
 * - POST /api/v1/auth/reset-confirm with valid token resets password and returns 200
 * - POST /api/v1/auth/reset-confirm with invalid token returns 400
 * - POST /api/v1/auth/reset-confirm with mismatched/weak password returns 400
 * - After successful reset, user can log in with new password
 * - After successful reset, old password no longer works
 */
class PasswordResetFlowIntegrationTest : WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var testUser: User

    @BeforeEach
    fun setupTest() {
        testUser =
            User(
                id = UUID.randomUUID(),
                username = "resetflowuser",
                email = "resetflow@test.com",
                passwordHash = encoder.encode("oldpassword"),
                role = UserRole.USER,
            )
        userRepository.save(testUser)

        app = buildApp()
    }

    @AfterEach fun teardown() = cleanup()

    private val resetService by lazy {
        PasswordResetService(
            userRepository,
            encoder,
            resetRepository = passwordResetRepository,
            auditRepository = auditRepository,
        )
    }

    private fun requestRawToken(email: String): String? = resetService.requestPasswordReset(email)

    @Test
    fun `POST reset-request with valid email returns 200`() {
        val response =
            app(
                Request(POST, "/api/v1/auth/reset-request")
                    .header("content-type", "application/json")
                    .body("""{"email":"${testUser.email}"}""")
            )

        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `POST reset-request with unknown email still returns 200 (no user leak)`() {
        val response =
            app(
                Request(POST, "/api/v1/auth/reset-request")
                    .header("content-type", "application/json")
                    .body("""{"email":"nobody@unknown.com"}""")
            )

        // Must return 200 even for unknown emails to prevent user enumeration
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `POST reset-request stores token in database`() {
        val tokensBefore = testDsl.fetchCount(testDsl.selectFrom(org.jooq.impl.DSL.table("plt_password_reset_tokens")))

        app(
            Request(POST, "/api/v1/auth/reset-request")
                .header("content-type", "application/json")
                .body("""{"email":"${testUser.email}"}""")
        )

        val tokensAfter = testDsl.fetchCount(testDsl.selectFrom(org.jooq.impl.DSL.table("plt_password_reset_tokens")))
        assertEquals(tokensBefore + 1, tokensAfter, "One token should be stored in the DB")
    }

    @Test
    fun `POST reset-confirm with valid token changes password and returns 200`() {
        val rawToken = requestRawToken(testUser.email)
        assertNotNull(rawToken, "Token should be generated after reset request")

        val response =
            app(
                Request(POST, "/api/v1/auth/reset-confirm")
                    .header("content-type", "application/json")
                    .body("""{"token":"$rawToken","newPassword":"newSecurePass99"}""")
            )

        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `POST reset-confirm with invalid token returns 400`() {
        val response =
            app(
                Request(POST, "/api/v1/auth/reset-confirm")
                    .header("content-type", "application/json")
                    .body("""{"token":"completely-invalid-token","newPassword":"newSecurePass99"}""")
            )

        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `after successful reset user can authenticate with new password`() {
        val rawToken = requestRawToken(testUser.email)
        assertNotNull(rawToken)

        app(
            Request(POST, "/api/v1/auth/reset-confirm")
                .header("content-type", "application/json")
                .body("""{"token":"$rawToken","newPassword":"brandnewpass123"}""")
        )

        // Try logging in with new password
        val loginResponse =
            app(
                Request(POST, "/api/v1/auth/login")
                    .header("content-type", "application/json")
                    .body("""{"username":"${testUser.username}","password":"brandnewpass123"}""")
            )
        assertEquals(Status.OK, loginResponse.status, "New password should work for login")
    }

    @Test
    fun `after successful reset old password no longer works`() {
        val rawToken = requestRawToken(testUser.email)
        assertNotNull(rawToken)

        app(
            Request(POST, "/api/v1/auth/reset-confirm")
                .header("content-type", "application/json")
                .body("""{"token":"$rawToken","newPassword":"brandnewpass123"}""")
        )

        // Try logging in with OLD password
        val loginResponse =
            app(
                Request(POST, "/api/v1/auth/login")
                    .header("content-type", "application/json")
                    .body("""{"username":"${testUser.username}","password":"oldpassword"}""")
            )
        assertEquals(Status.UNAUTHORIZED, loginResponse.status, "Old password should be rejected")
    }

    @Test
    fun `POST reset-confirm with weak new password returns 400`() {
        val rawToken = requestRawToken(testUser.email)
        assertNotNull(rawToken)

        val response =
            app(
                Request(POST, "/api/v1/auth/reset-confirm")
                    .header("content-type", "application/json")
                    .body("""{"token":"$rawToken","newPassword":"short"}""")
            )

        assertTrue(response.status.code >= 400, "Weak password should be rejected, got: ${response.status}")
    }
}
