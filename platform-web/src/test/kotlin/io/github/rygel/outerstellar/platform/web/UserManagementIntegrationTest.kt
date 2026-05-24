package io.github.rygel.outerstellar.platform.web

import com.natpryce.hamkrest.assertion.assertThat
import io.github.rygel.outerstellar.platform.model.AuthTokenResponse
import io.github.rygel.outerstellar.platform.model.ChangePasswordRequest
import io.github.rygel.outerstellar.platform.model.LoginRequest
import io.github.rygel.outerstellar.platform.model.RegisterRequest
import io.github.rygel.outerstellar.platform.model.SetUserEnabledRequest
import io.github.rygel.outerstellar.platform.model.SetUserRoleRequest
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.model.UserSummary
import io.github.rygel.outerstellar.platform.security.SecurityService
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Method.PUT
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.KotlinxSerialization.auto
import org.http4k.hamkrest.hasStatus
import org.junit.jupiter.api.BeforeEach

class UserManagementIntegrationTest : WebTest() {

    private lateinit var app: HttpHandler
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
        securityService = createSecurityService()

        app = buildApp(securityService = securityService)
    }

    // ---- Helper methods ----

    private data class RegisteredUser(val id: UUID, val token: String)

    private fun registerUser(username: String, password: String): RegisteredUser {
        val response =
            app(Request(POST, "/api/v1/auth/register").with(registerLens of RegisterRequest(username, password)))
        assertThat(response, hasStatus(Status.OK))
        val auth = tokenLens(response)
        val userId = userRepository.findByUsername(username)!!.id
        return RegisteredUser(userId, auth.token)
    }

    private fun loginUser(username: String, password: String): AuthTokenResponse {
        val response = app(Request(POST, "/api/v1/auth/login").with(loginLens of LoginRequest(username, password)))
        assertThat(response, hasStatus(Status.OK))
        return tokenLens(response)
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

    private fun bearerRequest(method: org.http4k.core.Method, path: String, token: String) =
        Request(method, path).header("Authorization", "Bearer $token")

    // ---- Password Change (API) ----

    @Test
    fun `change password via API succeeds with correct current password`() {
        val auth = registerUser("pwduser", "0ldP@ssw0rd1!")

        val response =
            app(
                bearerRequest(PUT, "/api/v1/auth/password", auth.token)
                    .with(changePasswordLens of ChangePasswordRequest("0ldP@ssw0rd1!", "N3wP@ssw0rd1!"))
            )
        assertThat(response, hasStatus(Status.OK))

        // Verify old password no longer works
        val failLogin =
            app(Request(POST, "/api/v1/auth/login").with(loginLens of LoginRequest("pwduser", "0ldP@ssw0rd1!")))
        assertThat(failLogin, hasStatus(Status.UNAUTHORIZED))

        // Verify new password works
        val successLogin = loginUser("pwduser", "N3wP@ssw0rd1!")
        assertTrue(successLogin.token.isNotBlank())
    }

    @Test
    fun `change password fails with wrong current password`() {
        val auth = registerUser("pwduser2", "C0rr3ctP@ss1!")

        val response =
            app(
                bearerRequest(PUT, "/api/v1/auth/password", auth.token)
                    .with(changePasswordLens of ChangePasswordRequest("wrongpassword", "N3wP@ssw0rd1!"))
            )
        assertThat(response, hasStatus(Status.BAD_REQUEST))
    }

    @Test
    fun `change password fails with too short new password`() {
        val auth = registerUser("pwduser3", "C0rr3ctP@ss1!")

        val response =
            app(
                bearerRequest(PUT, "/api/v1/auth/password", auth.token)
                    .with(changePasswordLens of ChangePasswordRequest("C0rr3ctP@ss1!", "short"))
            )
        assertThat(response, hasStatus(Status.BAD_REQUEST))
    }

    @Test
    fun `change password requires authentication`() {
        val response =
            app(
                Request(PUT, "/api/v1/auth/password")
                    .with(changePasswordLens of ChangePasswordRequest("anything", "N3wP@ssw0rd1!"))
            )
        assertThat(response, hasStatus(Status.UNAUTHORIZED))
    }

    // ---- Password Change (HTML form) ----

    // HTML form change-password tests use session cookies which require
    // the full filter chain including RequestContext user lookup. These are
    // covered by manual testing and the API-level tests above.

    // ---- User Admin (API) ----

    @Test
    fun `admin can list all users`() {
        val admin = seedAdmin()
        registerUser("user1", testPassword())
        registerUser("user2", testPassword())

        val response = app(bearerRequest(GET, "/api/v1/admin/users", admin.token))
        assertThat(response, hasStatus(Status.OK))

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
        assertThat(response, hasStatus(Status.FORBIDDEN))
    }

    @Test
    fun `admin can disable a user`() {
        val admin = seedAdmin()
        val password = testPassword()
        val userAuth = registerUser("disableuser", password)

        val response =
            app(
                bearerRequest(PUT, "/api/v1/admin/users/${userAuth.id}/enabled", admin.token)
                    .with(setUserEnabledLens of SetUserEnabledRequest(false))
            )
        assertThat(response, hasStatus(Status.OK))

        // Verify the disabled user cannot log in
        val loginResponse =
            app(Request(POST, "/api/v1/auth/login").with(loginLens of LoginRequest("disableuser", password)))
        assertThat(loginResponse, hasStatus(Status.UNAUTHORIZED))
    }

    @Test
    fun `admin can re-enable a user`() {
        val admin = seedAdmin()
        val password = testPassword()
        val userAuth = registerUser("reenableuser", password)

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
        assertThat(response, hasStatus(Status.OK))

        // User can log in again
        val loginResponse = loginUser("reenableuser", password)
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
        assertThat(response, hasStatus(Status.BAD_REQUEST))
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
        assertThat(response, hasStatus(Status.OK))

        // Verify the promoted user can now list users (admin privilege)
        val listResponse = app(bearerRequest(GET, "/api/v1/admin/users", userAuth.token))
        assertThat(listResponse, hasStatus(Status.OK))
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
        assertThat(response, hasStatus(Status.OK))

        // Verify the demoted user can no longer list users
        val listResponse = app(bearerRequest(GET, "/api/v1/admin/users", userAuth.token))
        assertThat(listResponse, hasStatus(Status.FORBIDDEN))
    }

    @Test
    fun `admin cannot demote self`() {
        val admin = seedAdmin()

        val response =
            app(
                bearerRequest(PUT, "/api/v1/admin/users/${admin.id}/role", admin.token)
                    .with(setUserRoleLens of SetUserRoleRequest("USER"))
            )
        assertThat(response, hasStatus(Status.BAD_REQUEST))
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
        assertEquals(UserRole.USER, user.role)
        assertTrue(user.enabled)
        assertTrue(user.id.isNotBlank())
    }

    // HTML admin routes, session timeout, navigation, rate limiting, CORS,
    // security headers, correlation IDs, health, and web UI tests have been
    // moved to UserManagementWebUiIntegrationTest.
}
