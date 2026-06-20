package io.github.rygel.outerstellar.platform.web

import com.natpryce.hamkrest.assertion.assertThat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.KotlinxSerialization.auto
import org.http4k.hamkrest.hasStatus

class AuthApiIntegrationTest : WebTest() {

    private val app by lazy { buildApp() }

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
                            io.github.rygel.outerstellar.platform.model.RegisterRequest("api-user@test.com", password)
                    )
            )
        assertThat(registerResponse, hasStatus(Status.OK))
        assertEquals("api-user@test.com", tokenLens(registerResponse).username)

        val loginResponse =
            app(
                Request(POST, "/api/v1/auth/login")
                    .with(
                        loginLens of
                            io.github.rygel.outerstellar.platform.model.LoginRequest("api-user@test.com", password)
                    )
            )
        assertThat(loginResponse, hasStatus(Status.OK))
        assertTrue(tokenLens(loginResponse).token.isNotBlank())
    }
}
