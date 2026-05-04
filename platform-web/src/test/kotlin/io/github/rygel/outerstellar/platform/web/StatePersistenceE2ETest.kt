package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.UserRepository
import io.github.rygel.outerstellar.platform.service.ContactService
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.cookies
import org.junit.jupiter.api.AfterEach

class StatePersistenceE2ETest : H2WebTest() {
    @AfterEach
    fun teardown() {
        cleanup()
    }

    @Test
    fun `language preference is persisted in cookie`() {
        val app =
            buildApp(
                securityService = mockk<SecurityService>(relaxed = true),
                userRepository = mockk<UserRepository>(relaxed = true),
                contactService = mockk<ContactService>(relaxed = true),
            )

        val response = app(Request(GET, "/?lang=fr"))
        assertEquals(Status.OK, response.status)

        val langCookie = response.cookies().find { it.name == "app_lang" }
        assertEquals("fr", langCookie?.value)
    }
}
