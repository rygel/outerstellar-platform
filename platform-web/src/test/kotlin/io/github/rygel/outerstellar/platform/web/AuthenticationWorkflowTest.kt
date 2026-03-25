package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.security.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.core.cookie.cookie
import org.http4k.core.cookie.cookies
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class AuthenticationWorkflowTest : H2WebTest() {

    private lateinit var app: HttpHandler

    @BeforeEach
    fun setupTest() {
        app = buildApp()
    }

    @AfterEach fun teardown() = cleanup()

    @Test
    fun `user registration and login with redirect workflow`() {
        // 1. Try to access admin dashboard -> should redirect to auth with returnTo
        val initialResponse = app(Request(GET, "/admin/dev"))
        assertEquals(Status.FOUND, initialResponse.status)
        val location = initialResponse.header("location")!!
        assertTrue(
            location.contains("/auth") && location.contains("returnTo"),
            "Expected auth redirect with returnTo, got: $location",
        )

        // 2. Register a new admin user
        val password = testPassword()
        val regResponse =
            app(
                Request(POST, "/auth/components/result")
                    .form("mode", "register")
                    .form("name", "superadmin")
                    .form("email", "admin@test.com")
                    .form("password", password)
                    .form("confirmPassword", password)
            )
        // Registration redirects to /auth?registered=true
        assertEquals(Status.FOUND, regResponse.status)
        assertTrue(regResponse.header("location")!!.contains("registered=true"))

        // Update user to ADMIN role manually for test
        val user = userRepository.findByUsername("admin@test.com")!!
        userRepository.save(user.copy(role = UserRole.ADMIN))

        // 3. Login with returnTo
        val loginResponse =
            app(
                Request(POST, "/auth/components/result")
                    .query("returnTo", "/admin/dev")
                    .form("mode", "sign-in")
                    .form("email", "admin@test.com")
                    .form("password", password)
            )

        assertEquals(Status.FOUND, loginResponse.status)
        assertEquals("/admin/dev", loginResponse.header("location"))

        val sessionCookie = loginResponse.cookies().find { it.name == "app_session" }
        assertNotNull(sessionCookie, "Session cookie should be present in response")
        val setCookieHeader = loginResponse.header("Set-Cookie").orEmpty()
        assertTrue(setCookieHeader.contains("HttpOnly"))
        assertTrue(setCookieHeader.contains("SameSite=Strict"))

        // 4. Access admin dashboard with session
        val adminResponse = app(Request(GET, "/admin/dev").cookie(sessionCookie))
        assertEquals(Status.OK, adminResponse.status)
        assertTrue(adminResponse.bodyString().contains("Developer Dashboard"))
    }

    @Test
    fun `standard user cannot access admin dashboard`() {
        // Register standard user
        val password = testPassword()
        app(
            Request(POST, "/auth/components/result")
                .form("mode", "register")
                .form("name", "regular")
                .form("email", "user@test.com")
                .form("password", password)
                .form("confirmPassword", password)
        )

        val loginResponse =
            app(
                Request(POST, "/auth/components/result")
                    .form("mode", "sign-in")
                    .form("email", "user@test.com")
                    .form("password", password)
            )
        val sessionCookie = loginResponse.cookies().find { it.name == "app_session" }!!

        // Try to access admin dashboard -> should be Forbidden (403)
        val adminResponse = app(Request(GET, "/admin/dev").cookie(sessionCookie))
        assertEquals(Status.FORBIDDEN, adminResponse.status)
    }

    @Test
    fun `sign-in blocks external returnTo redirects`() {
        val password = testPassword()
        app(
            Request(POST, "/auth/components/result")
                .form("mode", "register")
                .form("name", "safeuser")
                .form("email", "safe@test.com")
                .form("password", password)
                .form("confirmPassword", password)
        )

        val loginResponse =
            app(
                Request(POST, "/auth/components/result")
                    .query("returnTo", "//evil.example/path")
                    .form("mode", "sign-in")
                    .form("email", "safe@test.com")
                    .form("password", password)
            )

        assertEquals(Status.FOUND, loginResponse.status)
        assertEquals("/", loginResponse.header("location"))
    }
}
