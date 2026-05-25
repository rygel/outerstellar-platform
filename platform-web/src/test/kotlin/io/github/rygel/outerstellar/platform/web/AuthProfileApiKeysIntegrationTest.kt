package io.github.rygel.outerstellar.platform.web

import com.natpryce.hamkrest.assertion.assertThat
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
 * Integration tests for profile, notification preferences, account deletion, API keys page, and password reset HTML
 * form flows.
 */
class AuthProfileApiKeysIntegrationTest : WebTest() {

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
                passwordHash = encoder.encode("correct-password"),
                role = UserRole.USER,
            )
        userRepository.save(testUser)

        testToken = sessionSvc.createSession(testUser.id)

        app = buildApp()
    }

    private fun sessionCookie() = Cookie(RequestContext.SESSION_COOKIE, testToken)

    private fun formBody(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (k, v) -> "$k=${java.net.URLEncoder.encode(v, "UTF-8")}" }

    // ---- Profile ----

    @Test
    fun `GET auth-profile without session redirects to auth`() {
        val response = app(Request(GET, "/auth/profile"))
        assertThat(response, hasStatus(Status.FOUND))
        assertTrue(response.header("location").orEmpty().contains("/auth"))
    }

    @Test
    fun `GET auth-profile with valid session returns 200`() {
        val response = app(Request(GET, "/auth/profile").cookie(sessionCookie()))
        assertThat(response, hasStatus(Status.OK))
    }

    @Test
    fun `POST profile-update changes email successfully`() {
        val response =
            app(
                Request(POST, "/auth/components/profile-update")
                    .cookie(sessionCookie())
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body(formBody("email" to "updated@test.com"))
            )

        assertThat(response, hasStatus(Status.OK))
        val saved = userRepository.findById(testUser.id)
        assertEquals("updated@test.com", saved?.email)
    }

    @Test
    fun `POST profile-update without session redirects to auth`() {
        val response =
            app(
                Request(POST, "/auth/components/profile-update")
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body(formBody("email" to "noauth@test.com"))
            )
        assertThat(response, hasStatus(Status.FOUND))
        assertTrue(response.header("location").orEmpty().contains("/auth"))
    }

    @Test
    fun `POST profile-update persists username change`() {
        app(
            Request(POST, "/auth/components/profile-update")
                .cookie(sessionCookie())
                .header("content-type", "application/x-www-form-urlencoded")
                .body(formBody("email" to testUser.email, "username" to "updated_username"))
        )
        assertEquals("updated_username", userRepository.findById(testUser.id)?.username)
    }

    @Test
    fun `POST profile-update returns error when username is taken`() {
        val other =
            User(
                id = UUID.randomUUID(),
                username = "taken_user",
                email = "taken@test.com",
                passwordHash = testPasswordHash,
                role = UserRole.USER,
            )
        userRepository.save(other)

        val response =
            app(
                Request(POST, "/auth/components/profile-update")
                    .cookie(sessionCookie())
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body(formBody("email" to testUser.email, "username" to "taken_user"))
            )

        assertThat(response, hasStatus(Status.OK))
        assertEquals("htmltestuser", userRepository.findById(testUser.id)?.username)
    }

    @Test
    fun `POST profile-update persists avatar URL`() {
        app(
            Request(POST, "/auth/components/profile-update")
                .cookie(sessionCookie())
                .header("content-type", "application/x-www-form-urlencoded")
                .body(formBody("email" to testUser.email, "avatarUrl" to "https://example.com/avatar.png"))
        )
        assertEquals("https://example.com/avatar.png", userRepository.findById(testUser.id)?.avatarUrl)
    }

    // ---- Notification preferences ----

    @Test
    fun `POST notification-preferences persists flags off`() {
        app(
            Request(POST, "/auth/notification-preferences")
                .cookie(sessionCookie())
                .header("content-type", "application/x-www-form-urlencoded")
                .body(formBody())
        )
        val saved = userRepository.findById(testUser.id)!!
        assertFalse(saved.emailNotificationsEnabled)
        assertFalse(saved.pushNotificationsEnabled)
    }

    @Test
    fun `POST notification-preferences persists flags on`() {
        app(
            Request(POST, "/auth/notification-preferences")
                .cookie(sessionCookie())
                .header("content-type", "application/x-www-form-urlencoded")
                .body(formBody("emailNotifications" to "on", "pushNotifications" to "on"))
        )
        val saved = userRepository.findById(testUser.id)!!
        assertTrue(saved.emailNotificationsEnabled)
        assertTrue(saved.pushNotificationsEnabled)
    }

    @Test
    fun `POST notification-preferences without session redirects to auth`() {
        val response =
            app(
                Request(POST, "/auth/notification-preferences")
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body(formBody("emailNotifications" to "on"))
            )
        assertThat(response, hasStatus(Status.FOUND))
        assertTrue(response.header("location").orEmpty().contains("/auth"))
    }

    // ---- Delete account ----

    @Test
    fun `POST account-delete removes account and redirects`() {
        val response =
            app(
                Request(POST, "/auth/account/delete")
                    .cookie(sessionCookie())
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body(formBody("currentPassword" to "correct-password"))
            )
        assertThat(response, hasStatus(Status.FOUND))
        assertTrue(response.header("location").orEmpty().contains("deleted=true"))
        assertNull(userRepository.findById(testUser.id))
    }

    @Test
    fun `POST account-delete without session redirects to auth`() {
        val response =
            app(
                Request(POST, "/auth/account/delete")
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body(formBody("currentPassword" to "correct-password"))
            )
        assertThat(response, hasStatus(Status.FOUND))
        assertTrue(response.header("location").orEmpty().contains("/auth"))
    }

    @Test
    fun `POST account-delete blocks last admin`() {
        val adminUser =
            User(
                id = UUID.randomUUID(),
                username = "only_admin",
                email = "admin@test.com",
                passwordHash = encoder.encode("admin123"),
                role = UserRole.ADMIN,
            )
        userRepository.save(adminUser)
        val adminToken = sessionSvc.createSession(adminUser.id)
        val adminCookie = Cookie(RequestContext.SESSION_COOKIE, adminToken)

        val response =
            app(
                Request(POST, "/auth/account/delete")
                    .cookie(adminCookie)
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body(formBody("currentPassword" to "admin123"))
            )
        assertThat(response, hasStatus(Status.OK))
        assertNotNull(userRepository.findById(adminUser.id))
    }

    // ---- API keys page ----

    @Test
    fun `GET api-keys without session redirects to auth`() {
        val response = app(Request(GET, "/auth/api-keys"))
        assertThat(response, hasStatus(Status.FOUND))
        assertTrue(response.header("location").orEmpty().contains("/auth"))
    }

    @Test
    fun `GET api-keys with valid session returns 200`() {
        val response = app(Request(GET, "/auth/api-keys").cookie(sessionCookie()))
        assertThat(response, hasStatus(Status.OK))
    }

    @Test
    fun `POST api-keys-create with blank name does not expose a key`() {
        val response =
            app(
                Request(POST, "/auth/api-keys/create")
                    .cookie(sessionCookie())
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body(formBody("name" to ""))
            )
        assertThat(response, hasStatus(Status.OK))
        assertFalse(response.bodyString().contains("osk_"))
    }

    @Test
    fun `POST api-keys-create with valid name shows raw key`() {
        val response =
            app(
                Request(POST, "/auth/api-keys/create")
                    .cookie(sessionCookie())
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body(formBody("name" to "My Integration Key"))
            )
        assertThat(response, hasStatus(Status.OK))
        assertTrue(response.bodyString().contains("osk_"))
    }

    @Test
    fun `POST api-keys-create without session redirects to auth`() {
        val response =
            app(
                Request(POST, "/auth/api-keys/create")
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body(formBody("name" to "Key"))
            )
        assertThat(response, hasStatus(Status.FOUND))
        assertTrue(response.header("location").orEmpty().contains("/auth"))
    }

    // ---- Password reset page ----

    @Test
    fun `GET auth-reset returns 200`() {
        assertEquals(Status.OK, app(Request(GET, "/auth/reset/some-token-value")).status)
    }

    @Test
    fun `GET auth-reset with token param renders the form`() {
        val response = app(Request(GET, "/auth/reset/some-token-value"))
        assertThat(response, hasStatus(Status.OK))
        assertTrue(response.bodyString().isNotBlank())
    }

    @Test
    fun `POST reset-confirm with mismatched passwords returns error`() {
        val response =
            app(
                Request(POST, "/auth/components/reset-confirm")
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body(
                        formBody(
                            "token" to "any-token",
                            "newPassword" to "pass-alpha",
                            "confirmPassword" to "pass-beta",
                        )
                    )
            )
        assertThat(response, hasStatus(Status.OK))
    }

    @Test
    fun `POST reset-confirm with invalid token returns error`() {
        val pwd = testPassword()
        val response =
            app(
                Request(POST, "/auth/components/reset-confirm")
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body(formBody("token" to "not-a-real-token", "newPassword" to pwd, "confirmPassword" to pwd))
            )
        assertThat(response, hasStatus(Status.OK))
    }
}
