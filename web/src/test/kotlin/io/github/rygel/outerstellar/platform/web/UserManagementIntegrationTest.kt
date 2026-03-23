package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.app
import io.github.rygel.outerstellar.platform.infra.createRenderer
import io.github.rygel.outerstellar.platform.model.AuthTokenResponse
import io.github.rygel.outerstellar.platform.model.ChangePasswordRequest
import io.github.rygel.outerstellar.platform.model.LoginRequest
import io.github.rygel.outerstellar.platform.model.RegisterRequest
import io.github.rygel.outerstellar.platform.model.SetUserEnabledRequest
import io.github.rygel.outerstellar.platform.model.SetUserRoleRequest
import io.github.rygel.outerstellar.platform.model.UserSummary
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
import org.http4k.core.Method.POST
import org.http4k.core.Method.PUT
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.Jackson.auto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UserManagementIntegrationTest : H2WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var userRepository: JooqUserRepository
    private lateinit var encoder: BCryptPasswordEncoder
    private lateinit var securityService: SecurityService

    private val loginLens = Body.auto<LoginRequest>().toLens()
    private val registerLens = Body.auto<RegisterRequest>().toLens()
    private val tokenLens = Body.auto<AuthTokenResponse>().toLens()
    private val changePasswordLens = Body.auto<ChangePasswordRequest>().toLens()
    private val userSummaryListLens = Body.auto<List<UserSummary>>().toLens()
    private val setUserEnabledLens = Body.auto<SetUserEnabledRequest>().toLens()
    private val setUserRoleLens = Body.auto<SetUserRoleRequest>().toLens()

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

    @AfterEach
    fun teardown() {
        cleanup()
    }

    // ---- Helper methods ----

    private data class RegisteredUser(val id: UUID, val token: String)

    private fun registerUser(username: String, password: String): RegisteredUser {
        val response =
            app(Request(POST, "/api/v1/auth/register").with(registerLens of RegisterRequest(username, password)))
        assertEquals(Status.OK, response.status, "Registration should succeed for $username")
        val auth = tokenLens(response)
        val userId = userRepository.findByUsername(username)!!.id
        return RegisteredUser(userId, auth.token)
    }

    private fun loginUser(username: String, password: String): AuthTokenResponse {
        val response = app(Request(POST, "/api/v1/auth/login").with(loginLens of LoginRequest(username, password)))
        assertEquals(Status.OK, response.status, "Login should succeed for $username")
        return tokenLens(response)
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

    private fun bearerRequest(method: org.http4k.core.Method, path: String, token: String) =
        Request(method, path).header("Authorization", "Bearer $token")

    // ---- Password Change (API) ----

    @Test
    fun `change password via API succeeds with correct current password`() {
        val auth = registerUser("pwduser", "oldpassword1")

        val response =
            app(
                bearerRequest(PUT, "/api/v1/auth/password", auth.token)
                    .with(changePasswordLens of ChangePasswordRequest("oldpassword1", "newpassword1"))
            )
        assertEquals(Status.OK, response.status)

        // Verify old password no longer works
        val failLogin =
            app(Request(POST, "/api/v1/auth/login").with(loginLens of LoginRequest("pwduser", "oldpassword1")))
        assertEquals(Status.UNAUTHORIZED, failLogin.status)

        // Verify new password works
        val successLogin = loginUser("pwduser", "newpassword1")
        assertTrue(successLogin.token.isNotBlank())
    }

    @Test
    fun `change password fails with wrong current password`() {
        val auth = registerUser("pwduser2", "correctpass1")

        val response =
            app(
                bearerRequest(PUT, "/api/v1/auth/password", auth.token)
                    .with(changePasswordLens of ChangePasswordRequest("wrongpassword", "newpassword1"))
            )
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `change password fails with too short new password`() {
        val auth = registerUser("pwduser3", "correctpass1")

        val response =
            app(
                bearerRequest(PUT, "/api/v1/auth/password", auth.token)
                    .with(changePasswordLens of ChangePasswordRequest("correctpass1", "short"))
            )
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `change password requires authentication`() {
        val response =
            app(
                Request(PUT, "/api/v1/auth/password")
                    .with(changePasswordLens of ChangePasswordRequest("anything", "newpassword1"))
            )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    // ---- Password Change (HTML form) ----

    // HTML form change-password tests use session cookies which require
    // the full filter chain including WebContext user lookup. These are
    // covered by manual testing and the API-level tests above.

    // ---- User Admin (API) ----

    @Test
    fun `admin can list all users`() {
        val admin = seedAdmin()
        registerUser("user1", testPassword())
        registerUser("user2", testPassword())

        val response = app(bearerRequest(GET, "/api/v1/admin/users", admin.token))
        assertEquals(Status.OK, response.status)

        val users = userSummaryListLens(response)
        assertTrue(users.size >= 3, "Should have at least admin + 2 users")
        assertNotNull(users.find { it.username == "admin" })
        assertNotNull(users.find { it.username == "user1" })
        assertNotNull(users.find { it.username == "user2" })
    }

    @Test
    fun `non-admin cannot list users`() {
        val auth = registerUser("regularuser", testPassword())

        val response = app(bearerRequest(GET, "/api/v1/admin/users", auth.token))
        assertEquals(Status.FORBIDDEN, response.status)
    }

    @Test
    fun `admin can disable a user`() {
        val admin = seedAdmin()
        val userAuth = registerUser("disableuser", testPassword())

        val response =
            app(
                bearerRequest(PUT, "/api/v1/admin/users/${userAuth.id}/enabled", admin.token)
                    .with(setUserEnabledLens of SetUserEnabledRequest(false))
            )
        assertEquals(Status.OK, response.status)

        // Verify the disabled user cannot log in
        val loginResponse =
            app(Request(POST, "/api/v1/auth/login").with(loginLens of LoginRequest("disableuser", testPassword())))
        assertEquals(Status.UNAUTHORIZED, loginResponse.status)
    }

    @Test
    fun `admin can re-enable a user`() {
        val admin = seedAdmin()
        val userAuth = registerUser("reenableuser", testPassword())

        // Disable
        app(
            bearerRequest(PUT, "/api/v1/admin/users/${userAuth.id}/enabled", admin.token)
                .with(setUserEnabledLens of SetUserEnabledRequest(false))
        )

        // Re-enable
        val response =
            app(
                bearerRequest(PUT, "/api/v1/admin/users/${userAuth.id}/enabled", admin.token)
                    .with(setUserEnabledLens of SetUserEnabledRequest(true))
            )
        assertEquals(Status.OK, response.status)

        // User can log in again
        val loginResponse = loginUser("reenableuser", testPassword())
        assertTrue(loginResponse.token.isNotBlank())
    }

    @Test
    fun `admin cannot disable self`() {
        val admin = seedAdmin()

        val response =
            app(
                bearerRequest(PUT, "/api/v1/admin/users/${admin.id}/enabled", admin.token)
                    .with(setUserEnabledLens of SetUserEnabledRequest(false))
            )
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `admin can promote a user to admin`() {
        val admin = seedAdmin()
        val userAuth = registerUser("promoteuser", testPassword())

        val response =
            app(
                bearerRequest(PUT, "/api/v1/admin/users/${userAuth.id}/role", admin.token)
                    .with(setUserRoleLens of SetUserRoleRequest("ADMIN"))
            )
        assertEquals(Status.OK, response.status)

        // Verify the promoted user can now list users (admin privilege)
        val listResponse = app(bearerRequest(GET, "/api/v1/admin/users", userAuth.token))
        assertEquals(Status.OK, listResponse.status)
    }

    @Test
    fun `admin can demote an admin to user`() {
        val admin = seedAdmin()
        val userAuth = registerUser("demoteuser", testPassword())

        // Promote first
        app(
            bearerRequest(PUT, "/api/v1/admin/users/${userAuth.id}/role", admin.token)
                .with(setUserRoleLens of SetUserRoleRequest("ADMIN"))
        )

        // Now demote
        val response =
            app(
                bearerRequest(PUT, "/api/v1/admin/users/${userAuth.id}/role", admin.token)
                    .with(setUserRoleLens of SetUserRoleRequest("USER"))
            )
        assertEquals(Status.OK, response.status)

        // Verify the demoted user can no longer list users
        val listResponse = app(bearerRequest(GET, "/api/v1/admin/users", userAuth.token))
        assertEquals(Status.FORBIDDEN, listResponse.status)
    }

    @Test
    fun `admin cannot demote self`() {
        val admin = seedAdmin()

        val response =
            app(
                bearerRequest(PUT, "/api/v1/admin/users/${admin.id}/role", admin.token)
                    .with(setUserRoleLens of SetUserRoleRequest("USER"))
            )
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `admin user list returns correct fields`() {
        val admin = seedAdmin()
        registerUser("fieldcheck", testPassword())

        val response = app(bearerRequest(GET, "/api/v1/admin/users", admin.token))
        val users = userSummaryListLens(response)
        val user = users.find { it.username == "fieldcheck" }!!

        assertEquals("fieldcheck", user.username)
        assertEquals("fieldcheck", user.email)
        assertEquals("USER", user.role)
        assertTrue(user.enabled)
        assertTrue(user.id.isNotBlank())
    }

    // HTML admin routes, session timeout, navigation, rate limiting, CORS,
    // security headers, correlation IDs, health, and web UI tests have been
    // moved to UserManagementWebUiIntegrationTest.
}
