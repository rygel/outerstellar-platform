package io.github.rygel.outerstellar.platform.web

import kotlin.test.Test
import kotlin.test.assertEquals
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.BeforeEach

class UnauthenticatedRouteAccessTest : WebTest() {

    private lateinit var app: org.http4k.core.HttpHandler

    @BeforeEach
    fun setupTest() {
        app = buildApp()
    }

    private fun assertRedirectsToAuth(request: Request) {
        val response = app(request)
        assertEquals(Status.FOUND, response.status, "Expected redirect to auth for ${request.method} ${request.uri}")
        val location = response.header("location") ?: ""
        assert(location.contains("/auth")) {
            "Expected redirect to /auth, got: $location for ${request.method} ${request.uri}"
        }
    }

    private fun assertUnauthorized(request: Request) {
        val response = app(request)
        assertEquals(Status.UNAUTHORIZED, response.status, "Expected 401 for ${request.method} ${request.uri}")
    }

    @Test
    fun `GET home page redirects to auth when not logged in`() {
        assertRedirectsToAuth(Request(Method.GET, "/"))
    }

    @Test
    fun `GET messages trash redirects to auth when not logged in`() {
        assertRedirectsToAuth(Request(Method.GET, "/messages/trash"))
    }

    @Test
    fun `POST create message redirects to auth when not logged in`() {
        assertRedirectsToAuth(
            Request(Method.POST, "/messages")
                .header("content-type", "application/x-www-form-urlencoded")
                .body("author=test&content=test")
        )
    }

    @Test
    fun `GET contacts page redirects to auth when not logged in`() {
        assertRedirectsToAuth(Request(Method.GET, "/contacts"))
    }

    @Test
    fun `GET search page redirects to auth when not logged in`() {
        assertRedirectsToAuth(Request(Method.GET, "/search?q=test"))
    }

    @Test
    fun `GET search API returns 401 when not logged in`() {
        assertUnauthorized(Request(Method.GET, "/api/v1/search?q=test"))
    }

    @Test
    fun `GET message list component redirects to auth when not logged in`() {
        assertRedirectsToAuth(Request(Method.GET, "/components/message-list"))
    }
}
