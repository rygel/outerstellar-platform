package io.github.rygel.outerstellar.platform

import io.github.rygel.outerstellar.platform.web.WebTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test

class ReproductionTest : WebTest() {

    @Test
    fun `reproduce theme synchronization issue`() {
        val app = buildApp()

        val response = app(Request(GET, "/?theme=dracula"))
        assertEquals(Status.OK, response.status)
        val body = response.bodyString()
        assertTrue(body.contains("""data-theme="dracula""""), "Should set data-theme to dracula")
    }
}
