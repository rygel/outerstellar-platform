package io.github.rygel.outerstellar.platform.web

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import kotlin.test.Test
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.hamkrest.hasBody
import org.http4k.hamkrest.hasHeader
import org.http4k.hamkrest.hasStatus

class GlobalErrorHandlerTest {

    private val brokenRenderer: org.http4k.template.TemplateRenderer = {
        throw TemplateRenderTestException("Template not found: ErrorPage.kte")
    }
    private val errorPageFactory = ErrorPageFactory()

    @Test
    fun `error handler returns emergency error page when template rendering fails`() {
        val handler =
            Filters.globalErrorHandler(errorPageFactory, brokenRenderer).then {
                throw HandlerTestException("something broke in the handler")
            }

        val response = handler(pageRequest("/test-page", "request-123"))

        assertThat(response, hasStatus(Status.INTERNAL_SERVER_ERROR))
        assertThat(response, hasHeader("content-type", "text/html; charset=utf-8"))
        assertThat(response, hasBody(containsSubstring("<h1>Internal Server Error</h1>")))
        assertThat(response, hasBody(containsSubstring("The error has been logged.")))
        assertThat(response, hasBody(containsSubstring("Reference: request-123")))
        assertThat(response, hasBody(!containsSubstring("something broke in the handler")))
    }

    @Test
    fun `error handler returns emergency error page with request reference when exception has no message`() {
        val handler = Filters.globalErrorHandler(errorPageFactory, brokenRenderer).then { throw HandlerTestException() }

        val response = handler(pageRequest("/test-page", "request-456"))

        assertThat(response, hasStatus(Status.INTERNAL_SERVER_ERROR))
        assertThat(response, hasBody(containsSubstring("<h1>Internal Server Error</h1>")))
        assertThat(response, hasBody(containsSubstring("The error has been logged.")))
        assertThat(response, hasBody(containsSubstring("Reference: request-456")))
    }

    @Test
    fun `404 handler returns emergency not found page when template rendering fails`() {
        val handler = Filters.globalErrorHandler(errorPageFactory, brokenRenderer).then { Response(Status.NOT_FOUND) }

        val response = handler(pageRequest("/missing-page", "request-789"))

        assertThat(response, hasStatus(Status.NOT_FOUND))
        assertThat(response, hasHeader("content-type", "text/html; charset=utf-8"))
        assertThat(response, hasBody(containsSubstring("<h1>Page not found</h1>")))
        assertThat(response, hasBody(containsSubstring("The requested page does not exist.")))
        assertThat(response, hasBody(containsSubstring("Reference: request-789")))
    }

    @Test
    fun `error handler returns JSON for API requests even when template rendering would fail`() {
        val handler =
            Filters.globalErrorHandler(errorPageFactory, brokenRenderer).then {
                throw HandlerTestException("api failure")
            }

        val response = handler(Request(GET, "/api/v1/test"))

        assertThat(response, hasStatus(Status.INTERNAL_SERVER_ERROR))
        assertThat(response, hasHeader("content-type", "application/json; charset=utf-8"))
    }

    @Test
    fun `error handler returns plain text for HTMX requests when template rendering fails`() {
        val handler =
            Filters.globalErrorHandler(errorPageFactory, brokenRenderer).then {
                throw HandlerTestException("htmx failure")
            }

        val response = handler(Request(GET, "/page").header("HX-Request", "true"))

        assertThat(response, hasStatus(Status.INTERNAL_SERVER_ERROR))
        assertThat(response, hasBody("Action failed"))
    }

    private class HandlerTestException(message: String? = null) : Exception(message)

    private class TemplateRenderTestException(message: String) : Exception(message)

    private fun pageRequest(path: String, requestId: String): Request {
        val request = Request(GET, path).header("X-Request-Id", requestId)
        val context = RequestContext(request)
        return request.with(RequestContext.KEY of context).with(ShellRenderer.KEY of ShellRenderer(context))
    }
}
