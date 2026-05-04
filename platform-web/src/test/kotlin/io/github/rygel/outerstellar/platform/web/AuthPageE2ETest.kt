package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.UserRepository
import io.github.rygel.outerstellar.platform.service.ContactService
import io.mockk.mockk
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthPageE2ETest : WebTest() {
    @AfterEach
    fun teardown() {
        cleanup()
    }

    @Test
    fun `auth page renders correctly`() {
        val app =
            buildApp(
                securityService = mockk<SecurityService>(relaxed = true),
                overrides =
                    TestOverrides(
                        userRepository = mockk<UserRepository>(relaxed = true),
                        contactService = mockk<ContactService>(relaxed = true),
                    ),
            )
        val response = app(Request(GET, "/auth"))

        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("Auth Examples"))
    }
}
