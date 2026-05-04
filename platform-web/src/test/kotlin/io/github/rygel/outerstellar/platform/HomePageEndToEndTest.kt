package io.github.rygel.outerstellar.platform

import io.github.rygel.outerstellar.platform.web.H2WebTest
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomePageEndToEndTest : H2WebTest() {

    @Test
    fun `home page is available on running server`() {
        messageRepository.seedMessages()

        val app = buildApp()

        val response = app(Request(GET, "/"))

        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("Outerstellar Platform"), "Body should contain brand name")
        assertTrue(response.header("content-type")?.contains("text/html") == true)
    }
}
