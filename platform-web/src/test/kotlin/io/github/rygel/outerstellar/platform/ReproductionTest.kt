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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ReproductionTest : WebTest() {

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
                username = "reprotest",
                email = "repro@test.com",
                passwordHash = encoder.encode("testpass1"),
                role = UserRole.USER,
            )
        userRepository.save(user)
        sessionCookie = Cookie(WebContext.SESSION_COOKIE, sec.createSession(user.id))
    }

    @AfterEach fun teardown() = cleanup()

    @Test
    fun `reproduce theme synchronization issue`() {
        val app = buildApp()

        val response = app(Request(GET, "/?theme=dracula").cookie(sessionCookie))
        assertEquals(Status.OK, response.status)
        val body = response.bodyString()
        assertTrue(body.contains("""data-theme="dracula""""), "Should set data-theme to dracula")
    }
}
