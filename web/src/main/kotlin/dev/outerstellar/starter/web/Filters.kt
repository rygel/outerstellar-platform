package dev.outerstellar.starter.web

import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.filter.*
import org.http4k.format.Jackson
import org.http4k.template.TemplateRenderer
import org.slf4j.LoggerFactory
import java.time.Duration

object Filters {
    private val logger = LoggerFactory.getLogger(Filters::class.java)

    val requestLogging: Filter = Filter { next: HttpHandler ->
        { request ->
            val start = System.currentTimeMillis()
            val response = next(request)
            val duration = System.currentTimeMillis() - start
            logger.info("${request.method} ${request.uri} -> ${response.status} (${duration}ms)")
            response
        }
    }

    val serverMetrics: Filter = ServerFilters.MicrometerMetrics.RequestCounter(Metrics.registry)
        .then(ServerFilters.MicrometerMetrics.RequestTimer(Metrics.registry))

    val telemetry: Filter = ServerFilters.OpenTelemetryTracing(Telemetry.openTelemetry)

    fun stateFilter(devDashboardEnabled: Boolean): Filter = Filter { next: HttpHandler ->
        { request ->
            val context = WebContext(request, devDashboardEnabled)
            val response = next(request.with(WebContext.KEY of context))
            
            // Persist preferences to cookies if they were provided in the query string
            var updatedResponse = response
            
            request.query("lang")?.let { 
                updatedResponse = updatedResponse.cookie(Cookie(WebContext.LANG_COOKIE, it, maxAge = Duration.ofDays(365).toSeconds(), path = "/")) 
            }
            request.query("theme")?.let { 
                updatedResponse = updatedResponse.cookie(Cookie(WebContext.THEME_COOKIE, it, maxAge = Duration.ofDays(365).toSeconds(), path = "/")) 
            }
            request.query("layout")?.let { 
                updatedResponse = updatedResponse.cookie(Cookie(WebContext.LAYOUT_COOKIE, it, maxAge = Duration.ofDays(365).toSeconds(), path = "/")) 
            }
            
            updatedResponse
        }
    }

    fun globalErrorHandler(pageFactory: WebPageFactory, renderer: TemplateRenderer): Filter = Filter { next: HttpHandler ->
        { request ->
            try {
                val response = next(request)
                if (response.status == Status.NOT_FOUND) {
                    if (request.uri.path.startsWith("/api/")) {
                        jsonErrorResponse(Status.NOT_FOUND, "Resource not found")
                    } else {
                        // Use request.webContext (from stateFilter) if available, otherwise fallback
                        val ctx = try { request.webContext } catch (e: Exception) { WebContext(request) }
                        val errorPage = pageFactory.buildErrorPage(ctx, "not-found")
                        Response(Status.NOT_FOUND)
                            .header("content-type", "text/html; charset=utf-8")
                            .body(renderer(errorPage))
                    }
                } else {
                    response
                }
            } catch (e: Exception) {
                logger.error("Unhandled exception for ${request.uri}", e)
                if (request.uri.path.startsWith("/api/")) {
                    jsonErrorResponse(Status.INTERNAL_SERVER_ERROR, e.message ?: "An unexpected error occurred")
                } else {
                    val ctx = try { request.webContext } catch (e: Exception) { WebContext(request) }
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
