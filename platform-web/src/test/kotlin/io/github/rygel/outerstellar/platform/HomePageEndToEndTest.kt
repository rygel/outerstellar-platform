package io.github.rygel.outerstellar.platform

import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.web.WebContext
import io.github.rygel.outerstellar.platform.web.WebTest
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HomePageEndToEndTest : WebTest() {

    private lateinit var sessionCookie: Cookie

    @BeforeEach
    fun setupUser() {
        val sec =
            SecurityService(
                userRepository,
                encoder,
                sessionRepository = sessionRepository,
                apiKeyRepository = apiKeyRepository,
                resetRepository = passwordResetRepository,
                auditRepository = auditRepository,
            )
        val user =
            User(
                id = UUID.randomUUID(),
                username = "hometest",
                email = "home@test.com",
                passwordHash = encoder.encode("testpass1"),
                role = UserRole.USER,
            )
        userRepository.save(user)
        sessionCookie = Cookie(WebContext.SESSION_COOKIE, sec.createSession(user.id))
    }

    @Test
    fun `home page is available on running server`() {
        messageRepository.seedMessages()

        val app = buildApp()

        val response = app(Request(GET, "/").cookie(sessionCookie))

        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("Outerstellar Platform"), "Body should contain brand name")
        assertTrue(response.header("content-type")?.contains("text/html") == true)
    }
}
