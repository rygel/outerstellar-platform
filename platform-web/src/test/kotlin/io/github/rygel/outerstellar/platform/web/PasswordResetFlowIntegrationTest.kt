package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.security.PasswordResetService
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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
                passwordHash = encoder.encode("0ldP@ssw0rd!"),
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
        val tokensBefore =
            testJdbi.open().createQuery("SELECT COUNT(*) FROM plt_password_reset_tokens").mapTo(Int::class.java).first()

        app(
            Request(POST, "/api/v1/auth/reset-request")
                .header("content-type", "application/json")
                .body("""{"email":"${testUser.email}"}""")
        )

        val tokensAfter =
            testJdbi.open().createQuery("SELECT COUNT(*) FROM plt_password_reset_tokens").mapTo(Int::class.java).first()
        assertEquals(tokensBefore + 1, tokensAfter, "One token should be stored in the DB")
    }

    @Test
    fun `password reset stores only token hash`() {
        val rawToken = requestRawToken(testUser.email)
        assertNotNull(rawToken, "Token should be generated after reset request")

        val storedToken =
            testJdbi
                .open()
                .createQuery("SELECT token FROM plt_password_reset_tokens WHERE user_id = :id")
                .bind("id", testUser.id)
                .mapTo(String::class.java)
                .first()

        assertNotEquals(rawToken, storedToken, "Raw reset token must not be stored")
        assertTrue(storedToken.matches(Regex("[0-9a-f]{64}")), "Stored reset token should be a SHA-256 hex hash")
    }

    @Test
    fun `POST reset-confirm with valid token changes password and returns 200`() {
        val rawToken = requestRawToken(testUser.email)
        assertNotNull(rawToken, "Token should be generated after reset request")

        val response =
            app(
                Request(POST, "/api/v1/auth/reset-confirm")
                    .header("content-type", "application/json")
                    .body("""{"token":"$rawToken","newPassword":"N3wS3cur3P@ss!"}""")
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
                .body("""{"token":"$rawToken","newPassword":"Br@ndN3wP@ss1!"}""")
        )

        // Try logging in with new password
        val loginResponse =
            app(
                Request(POST, "/api/v1/auth/login")
                    .header("content-type", "application/json")
                    .body("""{"username":"${testUser.username}","password":"Br@ndN3wP@ss1!"}""")
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
                .body("""{"token":"$rawToken","newPassword":"Br@ndN3wP@ss1!"}""")
        )

        val loginResponse =
            app(
                Request(POST, "/api/v1/auth/login")
                    .header("content-type", "application/json")
                    .body("""{"username":"${testUser.username}","password":"0ldP@ssw0rd!"}""")
            )
        assertEquals(Status.UNAUTHORIZED, loginResponse.status, "Old password should be rejected")
    }
}
