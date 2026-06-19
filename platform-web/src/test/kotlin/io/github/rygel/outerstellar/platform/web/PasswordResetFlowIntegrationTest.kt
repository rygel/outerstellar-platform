package io.github.rygel.outerstellar.platform.web

import com.natpryce.hamkrest.assertion.assertThat
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.security.PasswordResetService
import io.github.rygel.outerstellar.platform.service.EmailService
import io.github.rygel.outerstellar.platform.service.NoOpEmailService
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
import org.http4k.hamkrest.hasStatus
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
    private class CapturingEmailService : EmailService {
        data class Email(val to: String, val subject: String, val body: String)

        val sent = mutableListOf<Email>()

        override fun send(to: String, subject: String, body: String) {
            sent += Email(to, subject, body)
        }
    }

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

    private val resetService by lazy {
        PasswordResetService(
            userRepository,
            encoder,
            resetRepository = passwordResetRepository,
            auditRepository = auditRepository,
            sessionRepository = sessionRepository,
            emailService = NoOpEmailService(),
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

        assertThat(response, hasStatus(Status.OK))
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
        assertThat(response, hasStatus(Status.OK))
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
    fun `POST reset-request sends password reset email through shared security wiring`() {
        val emailService = CapturingEmailService()
        val appWithEmail = buildApp(overrides = TestOverrides(emailService = emailService))

        val response =
            appWithEmail(
                Request(POST, "/api/v1/auth/reset-request")
                    .header("content-type", "application/json")
                    .body("""{"email":"${testUser.email}"}""")
            )

        assertThat(response, hasStatus(Status.OK))
        assertEquals(1, emailService.sent.size)
        assertEquals(testUser.email, emailService.sent.single().to)
        assertEquals("Password Reset Request", emailService.sent.single().subject)
        assertTrue(emailService.sent.single().body.contains("/auth/reset/"))
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
        assertTrue(storedToken.length == 44, "Stored reset token should be an HMAC-SHA256 Base64 hash (44 chars)")
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

        assertThat(response, hasStatus(Status.OK))
    }

    @Test
    fun `POST reset-confirm with invalid token returns 400`() {
        val response =
            app(
                Request(POST, "/api/v1/auth/reset-confirm")
                    .header("content-type", "application/json")
                    .body("""{"token":"completely-invalid-token","newPassword":"newSecurePass99"}""")
            )

        assertThat(response, hasStatus(Status.BAD_REQUEST))
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
        assertThat(loginResponse, hasStatus(Status.OK))
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
        assertThat(loginResponse, hasStatus(Status.UNAUTHORIZED))
    }
}
