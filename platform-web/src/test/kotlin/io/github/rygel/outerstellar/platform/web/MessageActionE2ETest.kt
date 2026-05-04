package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.UserRepository
import io.github.rygel.outerstellar.platform.service.ContactService
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.body.form
import org.junit.jupiter.api.AfterEach

class MessageActionE2ETest : H2WebTest() {
    @AfterEach
    fun teardown() {
        cleanup()
    }

    @Test
    fun `can create a message via form`() {
        val app =
            buildApp(
                securityService = mockk<SecurityService>(relaxed = true),
                userRepository = mockk<UserRepository>(relaxed = true),
                contactService = mockk<ContactService>(relaxed = true),
            )

        val response = app(Request(POST, "/messages").form("author", "Test Author").form("content", "Test Content"))

        assertEquals(Status.FOUND, response.status)

        val redirectResponse = app(Request(GET, "/"))
        assertEquals(Status.OK, redirectResponse.status)
        assertTrue(redirectResponse.bodyString().contains("Test Author"))
        assertTrue(redirectResponse.bodyString().contains("Test Content"))
    }
}
