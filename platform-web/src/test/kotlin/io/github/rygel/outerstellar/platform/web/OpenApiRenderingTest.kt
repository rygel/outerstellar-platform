package io.github.rygel.outerstellar.platform.web

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import kotlin.test.Test
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.cookie
import org.http4k.hamkrest.hasStatus

class OpenApiRenderingTest : WebTest() {
    // Issue #558: OpenAPI document routes previously returned a generic 500 with
    // "Serializer for class 'EmptyArray' is not found" (http4k OpenApi3/kotlinx.serialization
    // incompatibility, http4k/http4k#750). They now degrade to a clear 503 with an explanatory
    // message so consumers get an honest response instead of an alarming 500.

    @Test
    fun `admin OpenAPI document degrades to 503 instead of 500`() {
        val app = buildApp()
        val (token, _, _) = withAuthenticatedUser(role = "ADMIN")
        val response = app(Request(GET, "/api/v1/admin/api-openapi.json").cookie(RequestContext.SESSION_COOKIE, token))

        assertThat(response, hasStatus(Status.SERVICE_UNAVAILABLE))
        assertThat(response.bodyString(), containsSubstring("OpenAPI spec is unavailable"))
    }

    @Test
    fun `public API OpenAPI document degrades to 503 instead of 500`() {
        val app = buildApp()
        val response = app(Request(GET, "/api/openapi.json"))

        assertThat(response, hasStatus(Status.SERVICE_UNAVAILABLE))
        assertThat(response.bodyString(), containsSubstring("OpenAPI spec is unavailable"))
    }

    @Test
    fun `sync API OpenAPI document degrades to 503 instead of 500`() {
        val app = buildApp()
        val response = app(Request(GET, "/api/v1/sync/openapi.json"))

        assertThat(response, hasStatus(Status.SERVICE_UNAVAILABLE))
        assertThat(response.bodyString(), containsSubstring("OpenAPI spec is unavailable"))
    }
}
