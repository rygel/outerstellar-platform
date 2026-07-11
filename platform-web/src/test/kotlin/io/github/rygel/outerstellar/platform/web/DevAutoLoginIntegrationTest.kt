package io.github.rygel.outerstellar.platform.web

import com.natpryce.hamkrest.assertion.assertThat
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertTrue
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.RequestSource
import org.http4k.core.Status
import org.http4k.core.cookie.cookies
import org.http4k.hamkrest.hasStatus
import org.junit.jupiter.api.BeforeEach

class DevAutoLoginIntegrationTest : WebTest() {
    private val app by lazy { buildApp(config = testConfig.copy(devMode = true), defaultRequestSource = null) }

    @BeforeEach
    fun createAdmin() {
        userRepository.save(
            User(
                id = UUID.randomUUID(),
                username = "admin",
                email = "admin@test.com",
                passwordHash = testPasswordHash,
                role = UserRole.ADMIN,
            )
        )
    }

    @Test
    fun `remote request cannot auto-login by claiming a localhost Host header`() {
        val response = app(Request(GET, "/").source(RequestSource("203.0.113.10")).header("Host", "localhost"))

        assertThat(response, hasStatus(Status.FOUND))
        assertTrue(response.cookies().none { it.name == RequestContext.SESSION_COOKIE })
    }

    @Test
    fun `request without a source address fails closed`() {
        val response = app(Request(GET, "/").header("Host", "localhost"))

        assertThat(response, hasStatus(Status.FOUND))
        assertTrue(response.cookies().none { it.name == RequestContext.SESSION_COOKIE })
    }

    @Test
    fun `forwarded request from a loopback proxy cannot auto-login`() {
        listOf("Forwarded", "X-Forwarded-For", "X-Real-IP").forEach { header ->
            val response = app(Request(GET, "/").source(RequestSource("127.0.0.1")).header(header, "203.0.113.10"))

            assertThat(response, hasStatus(Status.FOUND))
            assertTrue(response.cookies().none { it.name == RequestContext.SESSION_COOKIE })
        }
    }

    @Test
    fun `loopback request receives an authenticated administrator session`() {
        val response = app(Request(GET, "/").source(RequestSource("127.0.0.1")))

        assertThat(response, hasStatus(Status.OK))
        assertTrue(response.cookies().any { it.name == RequestContext.SESSION_COOKIE })
        assertTrue(response.bodyString().contains("Outerstellar Platform"))
    }
}
