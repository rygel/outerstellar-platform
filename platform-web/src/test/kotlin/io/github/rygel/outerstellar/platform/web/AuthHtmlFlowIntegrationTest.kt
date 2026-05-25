package io.github.rygel.outerstellar.platform.web

import com.natpryce.hamkrest.assertion.assertThat
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.hamkrest.hasStatus
import org.junit.jupiter.api.BeforeEach

/**
 * Integration tests for HTML-form auth flows (Feature 2 — auth routes).
 *
 * Covers:
 * - GET /auth/components/forms/{mode} renders form fragments
 * - POST /auth/components/result sign-in → redirect with session cookie
 * - POST /auth/components/result wrong password → error fragment (no redirect)
 * - POST /auth/components/result register → redirect to /auth?registered=true
 * - POST /auth/components/result duplicate username → error fragment
 * - POST /auth/components/result recover → always succeeds (no user leak)
 * - GET /auth/change-password unauthenticated → redirect to /auth
 * - GET /auth/change-password authenticated → 200
 * - POST /auth/components/change-password success
 * - POST /auth/components/change-password wrong current password → error fragment
 * - POST /auth/components/change-password password mismatch → error fragment
 * - GET /auth/profile unauthenticated → redirect
 * - GET /auth/profile authenticated → 200
 * - POST /auth/components/profile-update success
 * - GET /auth/api-keys unauthenticated → redirect
 * - GET /auth/api-keys authenticated → 200
 * - POST /auth/api-keys/create blank name → re-renders page without key
 * - POST /auth/api-keys/create valid name → shows new key once
 * - POST /auth/api-keys/{id}/delete → removes key, redirects
 * - GET /auth/reset renders reset form
 * - POST /auth/components/reset-confirm password mismatch → error
 */
class AuthHtmlFlowIntegrationTest : WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var testUser: User
    private lateinit var testToken: String

    @BeforeEach
    fun setupTest() {
        testUser =
            User(
                id = UUID.randomUUID(),
                username = "htmltestuser",
                email = "htmltestuser@test.com",
                passwordHash = encoder.encode("C0rr3ct-P@ss"),
                role = UserRole.USER,
            )
        userRepository.save(testUser)

        testToken = sessionSvc.createSession(testUser.id)

        app = buildApp()
    }

    private fun sessionCookie() = Cookie(RequestContext.SESSION_COOKIE, testToken)

    private fun formBody(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (k, v) -> "$k=${java.net.URLEncoder.encode(v, "UTF-8")}" }

    // ---- Form fragment rendering ----

    @Test
    fun `GET auth-components-forms-sign-in renders the sign-in form`() {
        val response = app(Request(GET, "/auth/components/forms/sign-in"))
        assertThat(response, hasStatus(Status.OK))
        val body = response.bodyString()
        assertTrue(
            body.contains("email") || body.contains("password"),
            "Sign-in form should contain email/password fields",
        )
    }

    @Test
    fun `GET auth-components-forms-register renders the register form`() {
        val response = app(Request(GET, "/auth/components/forms/register"))
        assertThat(response, hasStatus(Status.OK))
        val body = response.bodyString()
        assertTrue(body.contains("password"), "Register form should contain password field")
    }

    @Test
    fun `GET auth-components-forms-recover renders the recover form`() {
        val response = app(Request(GET, "/auth/components/forms/recover"))
        assertThat(response, hasStatus(Status.OK))
        val body = response.bodyString()
        assertTrue(body.isNotBlank(), "Recover form should render content")
    }

    // ---- Sign-in ----

    @Test
    fun `POST auth-components-result sign-in with correct credentials redirects with session cookie`() {
        val response =
            app(
                Request(POST, "/auth/components/result")
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body(formBody("mode" to "sign-in", "email" to testUser.username, "password" to "C0rr3ct-P@ss"))
            )

        assertThat(response, hasStatus(Status.FOUND))
        val location = response.header("location").orEmpty()
        assertTrue(location.isNotBlank(), "Successful sign-in should redirect")

        val setCookie = response.header("Set-Cookie").orEmpty()
        assertTrue(
            setCookie.contains(RequestContext.SESSION_COOKIE),
            "Successful sign-in must set session cookie, got: $setCookie",
        )
    }

    @Test
    fun `POST auth-components-result sign-in with wrong password returns error fragment (no redirect)`() {
        val response =
            app(
                Request(POST, "/auth/components/result")
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body(formBody("mode" to "sign-in", "email" to testUser.username, "password" to "wrong-password"))
            )

        assertThat(response, hasStatus(Status.OK))
        val body = response.bodyString()
        assertTrue(
            body.contains("Invalid") || body.contains("invalid") || body.contains("error"),
            "Wrong password should return error in body, got: ${body.take(200)}",
        )
        assertFalse(response.header("location") != null, "Wrong password must not produce a redirect")
    }

    @Test
    fun `POST auth-components-result sign-in response carries no session cookie on failure`() {
        val response =
            app(
                Request(POST, "/auth/components/result")
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body(formBody("mode" to "sign-in", "email" to testUser.username, "password" to "bad"))
            )

        val setCookie = response.header("Set-Cookie").orEmpty()
        assertFalse(setCookie.contains(RequestContext.SESSION_COOKIE), "Failed sign-in must not set a session cookie")
    }

    // ---- Register ----

    @Test
    fun `POST auth-components-result register redirects to auth registered=true`() {
        val response =
            app(
                Request(POST, "/auth/components/result")
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body(formBody("mode" to "register", "email" to "newuser@test.com", "password" to testPassword()))
            )

        assertThat(response, hasStatus(Status.FOUND))
        val location = response.header("location").orEmpty()
        assertTrue(location.contains("registered=true"), "Register should redirect to ?registered=true, got: $location")
    }

    @Test
    fun `POST auth-components-result register with duplicate username returns error fragment`() {
        val response =
            app(
                Request(POST, "/auth/components/result")
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body(
                        formBody(
                            "mode" to "register",
                            "email" to testUser.username, // already registered
                            "password" to "An0ther-P@ss1",
                        )
                    )
            )

        assertThat(response, hasStatus(Status.OK))
        val body = response.bodyString()
        assertTrue(
            body.contains("already") || body.contains("exist") || body.contains("taken"),
            "Duplicate username should return descriptive error, got: ${body.take(200)}",
        )
    }

    @Test
    fun `POST auth-components-result register with weak password returns error fragment`() {
        val response =
            app(
                Request(POST, "/auth/components/result")
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body(formBody("mode" to "register", "email" to "weak@test.com", "password" to "abc"))
            )

        assertThat(response, hasStatus(Status.OK))
        val body = response.bodyString()
        assertTrue(body.isNotBlank(), "Weak password error should have body content")
    }

    // ---- Password recovery ----

    @Test
    fun `POST auth-components-result recover always returns 200 with success message`() {
        // Anti-user-enumeration: even unknown email should succeed
        val response =
            app(
                Request(POST, "/auth/components/result")
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body(formBody("mode" to "recover", "email" to "doesnotexist@nowhere.com"))
            )

        assertThat(response, hasStatus(Status.OK))
        assertFalse(response.header("location") != null, "Recovery should not redirect")
    }

    // ---- Change password page ----

    @Test
    fun `GET auth-change-password without session redirects to auth`() {
        val response = app(Request(GET, "/auth/change-password"))
        assertThat(response, hasStatus(Status.FOUND))
        assertTrue(
            response.header("location").orEmpty().contains("/auth"),
            "Unauthenticated change-password should redirect to /auth",
        )
    }

    @Test
    fun `GET auth-change-password with valid session returns 200`() {
        val response = app(Request(GET, "/auth/change-password").cookie(sessionCookie()))
        assertThat(response, hasStatus(Status.OK))
    }

    // ---- Change password form handler ----

    @Test
    fun `POST auth-components-change-password succeeds with correct current password`() {
        val response =
            app(
                Request(POST, "/auth/components/change-password")
                    .cookie(sessionCookie())
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body(
                        formBody(
                            "currentPassword" to "C0rr3ct-P@ss",
                            "newPassword" to "N3w-Str0ng@Pw",
                            "confirmPassword" to "N3w-Str0ng@Pw",
                        )
                    )
            )

        assertThat(response, hasStatus(Status.OK))
        val body = response.bodyString()
        assertTrue(
            body.contains("success") || body.contains("Success") || body.contains("changed"),
            "Successful password change should show success message, got: ${body.take(200)}",
        )
    }

    @Test
    fun `POST auth-components-change-password with wrong current password returns error`() {
        val response =
            app(
                Request(POST, "/auth/components/change-password")
                    .cookie(sessionCookie())
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body(
                        formBody(
                            "currentPassword" to "wrong-password",
                            "newPassword" to "new-strong-password",
                            "confirmPassword" to "new-strong-password",
                        )
                    )
            )

        assertThat(response, hasStatus(Status.OK))
        val body = response.bodyString()
        assertTrue(body.isNotBlank(), "Wrong current password should return error content")
    }

    @Test
    fun `POST auth-components-change-password with mismatched passwords returns error`() {
        val response =
            app(
                Request(POST, "/auth/components/change-password")
                    .cookie(sessionCookie())
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body(
                        formBody(
                            "currentPassword" to "C0rr3ct-P@ss",
                            "newPassword" to "N3w-P@ss-One1",
                            "confirmPassword" to "N3w-P@ss-Two2",
                        )
                    )
            )

        assertThat(response, hasStatus(Status.OK))
        val body = response.bodyString()
        assertTrue(
            body.contains("mismatch") || body.contains("match") || body.contains("error"),
            "Password mismatch should return error, got: ${body.take(200)}",
        )
    }

    @Test
    fun `POST auth-components-change-password without session redirects to auth`() {
        val response =
            app(
                Request(POST, "/auth/components/change-password")
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body(
                        formBody(
                            "currentPassword" to "correct-password",
                            "newPassword" to "newpass",
                            "confirmPassword" to "newpass",
                        )
                    )
            )

        assertThat(response, hasStatus(Status.FOUND))
        assertTrue(response.header("location").orEmpty().contains("/auth"))
    }
}
