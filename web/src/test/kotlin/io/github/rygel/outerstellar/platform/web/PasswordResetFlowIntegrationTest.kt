package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.app
import io.github.rygel.outerstellar.platform.infra.createRenderer
import io.github.rygel.outerstellar.platform.persistence.JooqMessageRepository
import io.github.rygel.outerstellar.platform.persistence.JooqPasswordResetRepository
import io.github.rygel.outerstellar.platform.persistence.JooqSessionRepository
import io.github.rygel.outerstellar.platform.persistence.JooqUserRepository
import io.github.rygel.outerstellar.platform.security.BCryptPasswordEncoder
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.security.UserRole
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.mockk.mockk
import org.http4k.core.HttpHandler
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
class PasswordResetFlowIntegrationTest : H2WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var userRepository: JooqUserRepository
    private lateinit var resetRepository: JooqPasswordResetRepository
    private lateinit var securityService: SecurityService
    private lateinit var encoder: BCryptPasswordEncoder
    private lateinit var testUser: User

    @BeforeEach
    fun setupTest() {
        encoder = BCryptPasswordEncoder(logRounds = 4)
        userRepository = JooqUserRepository(testDsl)
        resetRepository = JooqPasswordResetRepository(testDsl)
        val repository = JooqMessageRepository(testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val txManager = StubTransactionManager()
        val messageService = MessageService(repository, outbox, txManager, cache)
        val contactService = mockk<ContactService>(relaxed = true)
        securityService =
            SecurityService(
                userRepository,
                encoder,
                resetRepository = resetRepository,
                sessionRepository = JooqSessionRepository(testDsl),
            )
        val pageFactory = WebPageFactory(repository, messageService, contactService, securityService)

        testUser =
            User(
                id = UUID.randomUUID(),
                username = "resetflowuser",
                email = "resetflow@test.com",
                passwordHash = encoder.encode("oldpassword"),
                role = UserRole.USER,
            )
        userRepository.save(testUser)

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

    /** Fetch the most recent reset token from the DB. */
    private fun fetchResetToken(): String? =
        testDsl
            .fetchOne("SELECT token FROM password_reset_tokens WHERE used = false ORDER BY created_at DESC LIMIT 1")
            ?.get(0, String::class.java)

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
        val tokensBefore = testDsl.fetchCount(testDsl.selectFrom(org.jooq.impl.DSL.table("password_reset_tokens")))

        app(
            Request(POST, "/api/v1/auth/reset-request")
                .header("content-type", "application/json")
                .body("""{"email":"${testUser.email}"}""")
        )

        val tokensAfter = testDsl.fetchCount(testDsl.selectFrom(org.jooq.impl.DSL.table("password_reset_tokens")))
        assertEquals(tokensBefore + 1, tokensAfter, "One token should be stored in the DB")
    }

    @Test
    fun `POST reset-confirm with valid token changes password and returns 200`() {
        // Step 1: Request reset — token is stored in DB
        app(
            Request(POST, "/api/v1/auth/reset-request")
                .header("content-type", "application/json")
                .body("""{"email":"${testUser.email}"}""")
        )

        // Step 2: Retrieve token from DB
        val token = fetchResetToken()
        assertNotNull(token, "Token should be stored in DB after reset request")

        // Step 3: Confirm reset
        val response =
            app(
                Request(POST, "/api/v1/auth/reset-confirm")
                    .header("content-type", "application/json")
                    .body("""{"token":"$token","newPassword":"newSecurePass99"}""")
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
        // Request reset
        app(
            Request(POST, "/api/v1/auth/reset-request")
                .header("content-type", "application/json")
                .body("""{"email":"${testUser.email}"}""")
        )
        val token = fetchResetToken()
        assertNotNull(token)

        // Confirm reset with new password
        app(
            Request(POST, "/api/v1/auth/reset-confirm")
                .header("content-type", "application/json")
                .body("""{"token":"$token","newPassword":"brandnewpass123"}""")
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
        // Request reset
        app(
            Request(POST, "/api/v1/auth/reset-request")
                .header("content-type", "application/json")
                .body("""{"email":"${testUser.email}"}""")
        )
        val token = fetchResetToken()
        assertNotNull(token)

        // Confirm reset
        app(
            Request(POST, "/api/v1/auth/reset-confirm")
                .header("content-type", "application/json")
                .body("""{"token":"$token","newPassword":"brandnewpass123"}""")
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
        app(
            Request(POST, "/api/v1/auth/reset-request")
                .header("content-type", "application/json")
                .body("""{"email":"${testUser.email}"}""")
        )
        val token = fetchResetToken()
        assertNotNull(token)

        val response =
            app(
                Request(POST, "/api/v1/auth/reset-confirm")
                    .header("content-type", "application/json")
                    .body("""{"token":"$token","newPassword":"short"}""")
            )

        assertTrue(response.status.code >= 400, "Weak password should be rejected, got: ${response.status}")
    }
}
