package io.github.rygel.outerstellar.platform.web

import com.natpryce.hamkrest.assertion.assertThat
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.service.ContactService
import io.mockk.mockk
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertTrue
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.hamkrest.hasStatus
import org.junit.jupiter.api.BeforeEach

class MessageActionE2ETest : WebTest() {
    private lateinit var sessionCookie: Cookie
    private lateinit var sec: SecurityService

    @BeforeEach
    fun setupUser() {
        sec = createSecurityService()
        val user =
            User(
                id = UUID.randomUUID(),
                username = "msgtest",
                email = "msg@test.com",
                passwordHash = encoder.encode("testpass1"),
                role = UserRole.USER,
            )
        userRepository.save(user)
        sessionCookie = Cookie(WebContext.SESSION_COOKIE, sec.createSession(user.id))
    }

    private fun buildTestApp() =
        buildApp(
            securityService = sec,
            overrides = TestOverrides(contactService = mockk<ContactService>(relaxed = true)),
        )

    @Test
    fun `can create a message via form`() {
        val app = buildTestApp()

        val response =
            app(
                Request(POST, "/messages")
                    .form("author", "Test Author")
                    .form("content", "Test Content")
                    .cookie(sessionCookie)
            )

        assertThat(response, hasStatus(Status.OK))
        assertTrue(response.bodyString().contains("Test Author"))
        assertTrue(response.bodyString().contains("Test Content"))
    }

    @Test
    fun `can delete a message`() {
        val app = buildTestApp()
        val msg = messageRepository.createLocalMessage("Author", "Content to delete")

        val response = app(Request(POST, "/messages/${msg.syncId}/delete").cookie(sessionCookie))

        assertThat(response, hasStatus(Status.OK))
    }

    @Test
    fun `can get edit form for a message`() {
        val app = buildTestApp()
        val msg = messageRepository.createLocalMessage("Edit Author", "Edit content")

        val response = app(Request(GET, "/messages/${msg.syncId}/edit").cookie(sessionCookie))

        assertThat(response, hasStatus(Status.OK))
        val body = response.bodyString()
        assertTrue(body.contains("Edit Author"), "Form should contain existing author")
        assertTrue(body.contains("Edit content"), "Form should contain existing content")
    }

    @Test
    fun `can update a message`() {
        val app = buildTestApp()
        val msg = messageRepository.createLocalMessage("Old Author", "Old content")

        val response =
            app(
                Request(POST, "/messages/${msg.syncId}/update")
                    .form("author", "New Author")
                    .form("content", "New content")
                    .cookie(sessionCookie)
            )

        assertThat(response, hasStatus(Status.OK))
        val body = response.bodyString()
        assertTrue(body.contains("New Author"), "Updated list should show new author")
        assertTrue(body.contains("New content"), "Updated list should show new content")
    }
}
