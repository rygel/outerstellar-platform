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

import dev.outerstellar.starter.model.OuterstellarException
import dev.outerstellar.starter.model.ValidationException
import dev.outerstellar.starter.security.UserRepository
import dev.outerstellar.starter.security.SecurityRules

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

    fun stateFilter(devDashboardEnabled: Boolean, userRepository: UserRepository): Filter = Filter { next: HttpHandler ->
        { request ->
            val context = WebContext(request, devDashboardEnabled, userRepository)
            val response = next(request.with(WebContext.KEY of context))
            
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

    // New: Bridge WebContext user into SecurityRules
    val securityFilter: Filter = Filter { next: HttpHandler ->
        { request ->
            val user = try { request.webContext.user } catch (e: Exception) { null }
            next(request.with(SecurityRules.USER_KEY of user))
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
                val status = when (e) {
                    is ValidationException -> Status.BAD_REQUEST
                    is OuterstellarException -> Status.BAD_REQUEST
                    else -> Status.INTERNAL_SERVER_ERROR
                }

                logger.error("Error handling request ${request.uri}: ${e.message}")

                if (request.uri.path.startsWith("/api/")) {
                    jsonErrorResponse(status, e.message ?: "An unexpected error occurred")
                } else if (request.header("HX-Request") == "true") {
                    Response(status).body(e.message ?: "Action failed")
                } else {
                    val ctx = try { request.webContext } catch (ex: Exception) { WebContext(request) }
                    val errorPage = pageFactory.buildErrorPage(ctx, if (status == Status.INTERNAL_SERVER_ERROR) "server-error" else "not-found")
                    Response(status)
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
