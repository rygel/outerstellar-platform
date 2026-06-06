package io.github.rygel.outerstellar.platform.web

import com.natpryce.hamkrest.assertion.assertThat
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.hamkrest.hasStatus

class ErrorPagesIntegrationTest : WebTest() {

    private val app by lazy { buildApp() }

    @Test
    fun `GET errors-not-found returns 200 with HTML`() {
        val response = app(Request(GET, "/errors/not-found"))
        assertThat(response, hasStatus(Status.OK))
        val body = response.bodyString()
        assertTrue(body.isNotBlank(), "Error page should have content")
    }

    @Test
    fun `GET errors-server-error returns 200 with HTML`() {
        val response = app(Request(GET, "/errors/server-error"))
        assertThat(response, hasStatus(Status.OK))
        assertTrue(response.bodyString().isNotBlank())
    }

    @Test
    fun `GET errors-forbidden returns 200 with HTML`() {
        val response = app(Request(GET, "/errors/forbidden"))
        assertThat(response, hasStatus(Status.OK))
        assertTrue(response.bodyString().isNotBlank())
    }

    @Test
    fun `GET errors page is a full themed page containing layout shell elements`() {
        val body = app(Request(GET, "/errors/not-found")).bodyString()
        assertTrue(
            body.contains("<!DOCTYPE html>") || body.contains("<html") || body.contains("<body"),
            "Error page should be a full HTML document, got: ${body.take(200)}",
        )
    }

    @Test
    fun `GET errors-components-help-not-found returns 200`() {
        val response = app(Request(GET, "/errors/components/help/not-found"))
        assertThat(response, hasStatus(Status.OK))
    }

    @Test
    fun `GET errors-components-help-server-error returns 200`() {
        val response = app(Request(GET, "/errors/components/help/server-error"))
        assertThat(response, hasStatus(Status.OK))
    }

    @Test
    fun `GET errors-components-help-forbidden returns 200`() {
        val response = app(Request(GET, "/errors/components/help/forbidden"))
        assertThat(response, hasStatus(Status.OK))
    }

    @Test
    fun `GET errors-components-help with unknown kind returns 400`() {
        val response = app(Request(GET, "/errors/components/help/totally-unknown-error-kind"))
        assertThat(response, hasStatus(Status.BAD_REQUEST))
        assertThat(response, bodyContains("Unknown error help kind"))
    }

    @Test
    fun `GET errors page with unknown kind returns 400`() {
        val response = app(Request(GET, "/errors/totally-unknown-error-kind"))
        assertThat(response, hasStatus(Status.BAD_REQUEST))
        assertThat(response, bodyContains("Unknown error page kind"))
    }

    @Test
    fun `unknown HTML path returns 404`() {
        val response = app(Request(GET, "/nonexistent-page-xyzabc"))
        assertThat(response, hasStatus(Status.NOT_FOUND))
    }

    @Test
    fun `404 for HTML path returns text-html`() {
        val response = app(Request(GET, "/this-does-not-exist"))
        assertThat(response, hasContentType("text/html"))
    }

    @Test
    fun `404 for API path returns JSON`() {
        val response = app(Request(GET, "/api/v1/nonexistent"))
        assertThat(response, hasContentType("application/json"))
    }

    @Test
    fun `404 API JSON body has message field`() {
        val response = app(Request(GET, "/api/v1/nonexistent"))
        val body = response.bodyString()
        assertTrue(
            body.contains("message") || body.contains("error"),
            "API 404 JSON should have a message field, got: $body",
        )
    }

    @Test
    fun `404 response still carries X-Request-Id header`() {
        val response = app(Request(GET, "/nonexistent-path-123"))
        val requestId = response.header("X-Request-Id")
        assertNotNull(requestId, "X-Request-Id should be present even on 404 responses")
        assertTrue(requestId.isNotBlank())
    }

    @Test
    fun `404 HTML page contains themed error content`() {
        val body = app(Request(GET, "/completely-unknown-path")).bodyString()
        assertTrue(body.isNotBlank(), "404 page should have content")
    }

    @Test
    fun `GET health returns 200`() {
        val response = app(Request(GET, "/health"))
        assertThat(response, hasStatus(Status.OK))
    }

    @Test
    fun `GET health response is JSON`() {
        val response = app(Request(GET, "/health"))
        assertThat(response, hasContentType("application/json"))
    }

    @Test
    fun `GET health JSON body contains status UP`() {
        assertThat(app(Request(GET, "/health")), bodyContains("UP"))
    }

    @Test
    fun `GET health JSON body contains database key`() {
        assertThat(app(Request(GET, "/health")), bodyContains("database"))
    }

    @Test
    fun `GET health JSON body contains timestamp`() {
        assertThat(app(Request(GET, "/health")), bodyContains("timestamp"))
    }

    @Test
    fun `404 response includes X-Content-Type-Options`() {
        val response = app(Request(GET, "/no-such-page"))
        assertThat(response, org.http4k.hamkrest.hasHeader("X-Content-Type-Options", "nosniff"))
    }

    @Test
    fun `404 response includes X-Frame-Options`() {
        val response = app(Request(GET, "/no-such-page"))
        assertThat(response, org.http4k.hamkrest.hasHeader("X-Frame-Options", "DENY"))
    }

    @Test
    fun `POST to unknown API path returns 404 JSON`() {
        val response =
            app(Request(POST, "/api/v1/unknown-endpoint").header("content-type", "application/json").body("{}"))
        assertThat(response, hasStatus(Status.NOT_FOUND))
        assertThat(response, hasContentType("application/json"))
    }

    @Test
    fun `404 API JSON body includes requestId`() {
        val response = app(Request(GET, "/api/v1/nonexistent").header("X-Request-Id", "test-req-123"))
        assertThat(response, bodyContains("requestId"))
        assertThat(response, bodyContains("test-req-123"))
    }
}
