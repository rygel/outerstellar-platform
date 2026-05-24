package io.github.rygel.outerstellar.platform

import com.natpryce.hamkrest.assertion.assertThat
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.web.RequestContext
import io.github.rygel.outerstellar.platform.web.WebTest
import java.util.UUID
import kotlin.test.assertTrue
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.hamkrest.hasStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HomePageEndToEndTest : WebTest() {

    private lateinit var sessionCookie: Cookie

    @BeforeEach
    fun setupUser() {
        val sec = createSecurityService()
        val user =
            User(
                id = UUID.randomUUID(),
                username = "hometest",
                email = "home@test.com",
                passwordHash = encoder.encode("testpass1"),
                role = UserRole.USER,
            )
        userRepository.save(user)
        sessionCookie = Cookie(RequestContext.SESSION_COOKIE, sessionSvc.createSession(user.id))
    }

    @Test
    fun `home page is available on running server`() {
        messageRepository.seedMessages()

        val app = buildApp()

        val response = app(Request(GET, "/").cookie(sessionCookie))

        assertThat(response, hasStatus(Status.OK))
        assertTrue(response.bodyString().contains("Outerstellar Platform"), "Body should contain brand name")
        assertTrue(response.header("content-type")?.contains("text/html") == true)
    }
}
