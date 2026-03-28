package io.github.rygel.outerstellar.platform.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.http4k.core.HttpHandler
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.Jackson.auto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class AuthApiIntegrationTest : H2WebTest() {

    private lateinit var app: HttpHandler

    @BeforeEach
    fun setupTest() {
        app = buildApp()
    }

    @AfterEach fun teardown() = cleanup()

    @Test
    fun `register api creates user and allows login`() {
        val registerLens =
            org.http4k.core.Body.auto<io.github.rygel.outerstellar.platform.model.RegisterRequest>().toLens()
        val loginLens = org.http4k.core.Body.auto<io.github.rygel.outerstellar.platform.model.LoginRequest>().toLens()
        val tokenLens =
            org.http4k.core.Body.auto<io.github.rygel.outerstellar.platform.model.AuthTokenResponse>().toLens()

        val password = testPassword()
        val registerResponse =
            app(
                Request(POST, "/api/v1/auth/register")
                    .with(
                        registerLens of
                            io.github.rygel.outerstellar.platform.model.RegisterRequest("api-user", password)
                    )
            )
        assertEquals(Status.OK, registerResponse.status)
        assertEquals("api-user", tokenLens(registerResponse).username)

        val loginResponse =
            app(
                Request(POST, "/api/v1/auth/login")
                    .with(loginLens of io.github.rygel.outerstellar.platform.model.LoginRequest("api-user", password))
            )
        assertEquals(Status.OK, loginResponse.status)
        assertTrue(tokenLens(loginResponse).token.isNotBlank())
    }
}
