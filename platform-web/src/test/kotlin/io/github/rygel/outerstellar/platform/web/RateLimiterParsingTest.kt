package io.github.rygel.outerstellar.platform.web

import kotlin.test.Test
import kotlin.test.assertEquals
import org.http4k.core.Method.POST
import org.http4k.core.Request

class RateLimiterParsingTest {
    @Test
    fun `malformed form encoding skips broken fields and still parses valid email`() {
        val request =
            Request(POST, "/api/v1/auth/login")
                .header("content-type", "application/x-www-form-urlencoded")
                .body("username=%ZZ&email=valid@example.com")

        assertEquals("valid@example.com", extractAccountIdentifier(request))
    }
}
