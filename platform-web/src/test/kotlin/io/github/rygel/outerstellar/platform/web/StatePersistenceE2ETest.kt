package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.security.SecurityService
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.core.cookie.cookies
import org.junit.jupiter.api.BeforeEach

class StatePersistenceE2ETest : WebTest() {
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
                username = "langtest",
                email = "lang@test.com",
                passwordHash = encoder.encode("testpass1"),
                role = UserRole.USER,
            )
        userRepository.save(user)
        sessionCookie = Cookie(WebContext.SESSION_COOKIE, sec.createSession(user.id))
    }

    @Test
    fun `language preference is persisted in cookie`() {
        val app = buildApp()

        val response = app(Request(GET, "/?lang=fr").cookie(sessionCookie))
        assertEquals(Status.OK, response.status)

        val langCookie = response.cookies().find { it.name == "app_lang" }
        assertEquals("fr", langCookie?.value)
    }
}
