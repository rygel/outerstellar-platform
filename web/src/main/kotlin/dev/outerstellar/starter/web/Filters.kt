package dev.outerstellar.starter.web

import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.format.Jackson
import org.http4k.template.TemplateRenderer
import org.slf4j.LoggerFactory

object Filters {
    private val logger = LoggerFactory.getLogger(Filters::class.java)

    fun globalErrorHandler(pageFactory: WebPageFactory, renderer: TemplateRenderer): Filter = Filter { next: HttpHandler ->
        { request ->
            try {
                val response = next(request)
                if (response.status == Status.NOT_FOUND) {
                    if (request.uri.path.startsWith("/api/")) {
                        jsonErrorResponse(Status.NOT_FOUND, "Resource not found")
                    } else {
                        val ctx = WebContext(request)
                        val errorPage = pageFactory.buildErrorPage(ctx, "not-found")
                        Response(Status.NOT_FOUND)
                            .header("content-type", "text/html; charset=utf-8")
                            .body(renderer(errorPage))
                    }
                } else {
                    response
                }
            } catch (e: Exception) {
                logger.error("Unhandled exception", e)
                if (request.uri.path.startsWith("/api/")) {
                    jsonErrorResponse(Status.INTERNAL_SERVER_ERROR, e.message ?: "An unexpected error occurred")
                } else {
                    val ctx = WebContext(request)
                    val errorPage = pageFactory.buildErrorPage(ctx, "server-error")
                    Response(Status.INTERNAL_SERVER_ERROR)
                        .header("content-type", "text/html; charset=utf-8")
                        .body(renderer(errorPage))
                }
            }
        }
    }

    private fun jsonErrorResponse(status: Status, message: String): Response {
        val body = Jackson.asJsonObject(mapOf("message" to message, "status" to status.code)).toString()
        return Response(status)
            .header("content-type", "application/json; charset=utf-8")
            .body(body)
    }
}
