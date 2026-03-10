package dev.outerstellar.starter.web

import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.filter.ServerFilters
import org.http4k.filter.MicrometerMetrics
import org.http4k.filter.OpenTelemetryTracing
import org.http4k.format.Jackson
import org.http4k.template.TemplateRenderer
import org.slf4j.LoggerFactory
import java.time.Duration

import dev.outerstellar.starter.model.OuterstellarException
import dev.outerstellar.starter.model.ValidationException
import dev.outerstellar.starter.security.UserRepository
import dev.outerstellar.starter.security.SecurityRules

private const val COOKIE_MAX_AGE_DAYS = 365L

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

    fun stateFilter(
        devDashboardEnabled: Boolean, 
        userRepository: UserRepository
    ): Filter = Filter { next: HttpHandler ->
        { request ->
            val context = WebContext(request, devDashboardEnabled, userRepository)
            val response = next(request.with(WebContext.KEY of context))
            
            var updatedResponse = response
            
            val cookieMaxAge = Duration.ofDays(COOKIE_MAX_AGE_DAYS).toSeconds()

            request.query("lang")?.let { 
                val cookie = Cookie(WebContext.LANG_COOKIE, it, maxAge = cookieMaxAge, path = "/")
                updatedResponse = updatedResponse.cookie(cookie)
            }
            request.query("theme")?.let { 
                val cookie = Cookie(WebContext.THEME_COOKIE, it, maxAge = cookieMaxAge, path = "/")
                updatedResponse = updatedResponse.cookie(cookie)
            }
            request.query("layout")?.let { 
                val cookie = Cookie(WebContext.LAYOUT_COOKIE, it, maxAge = cookieMaxAge, path = "/")
                updatedResponse = updatedResponse.cookie(cookie)
            }
            
            updatedResponse
        }
    }

    // Bridge WebContext user into SecurityRules
    val securityFilter: Filter = Filter { next: HttpHandler ->
        { request ->
            val user = try { 
                request.webContext.user 
            } catch (e: IllegalStateException) { 
                logger.debug("Failed to extract user from context: {}", e.message)
                null 
            }
            next(request.with(SecurityRules.USER_KEY of user))
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun globalErrorHandler(
        pageFactory: WebPageFactory, 
        renderer: TemplateRenderer
    ): Filter = Filter { next: HttpHandler ->
        { request ->
            try {
                val response = next(request)
                if (response.status == Status.NOT_FOUND) {
                    handleNotFound(request, pageFactory, renderer)
                } else {
                    response
                }
            } catch (e: Exception) {
                handleException(e, request, pageFactory, renderer)
            }
        }
    }

    private fun handleNotFound(
        request: org.http4k.core.Request, 
        pageFactory: WebPageFactory, 
        renderer: TemplateRenderer
    ): Response {
        return if (request.uri.path.startsWith("/api/")) {
            jsonErrorResponse(Status.NOT_FOUND, "Resource not found")
        } else {
            val ctx = try { 
                request.webContext 
            } catch (e: IllegalStateException) { 
                logger.debug("WebContext not found for error page: {}", e.message)
                WebContext(request) 
            }
            val errorPage = pageFactory.buildErrorPage(ctx, "not-found")
            Response(Status.NOT_FOUND)
                .header("content-type", "text/html; charset=utf-8")
                .body(renderer(errorPage))
        }
    }

    private fun handleException(
        e: Exception, 
        request: org.http4k.core.Request, 
        pageFactory: WebPageFactory, 
        renderer: TemplateRenderer
    ): Response {
        val status = when (e) {
            is ValidationException -> Status.BAD_REQUEST
            is OuterstellarException -> Status.BAD_REQUEST
            else -> Status.INTERNAL_SERVER_ERROR
        }

        logger.error("Error handling request ${request.uri}: ${e.message}")

        return if (request.uri.path.startsWith("/api/")) {
            jsonErrorResponse(status, e.message ?: "An unexpected error occurred")
        } else if (request.header("HX-Request") == "true") {
            Response(status).body(e.message ?: "Action failed")
        } else {
            val ctx = try { 
                request.webContext 
            } catch (ex: IllegalStateException) { 
                logger.debug("WebContext not found for error page: {}", ex.message)
                WebContext(request) 
            }
            val errorKind = if (status == Status.INTERNAL_SERVER_ERROR) "server-error" else "not-found"
            val errorPage = pageFactory.buildErrorPage(ctx, errorKind)
            Response(status)
                .header("content-type", "text/html; charset=utf-8")
                .body(renderer(errorPage))
        }
    }

    private fun jsonErrorResponse(status: Status, message: String): Response {
        val body = Jackson.asJsonObject(mapOf("message" to message, "status" to status.code)).toString()
        return Response(status)
            .header("content-type", "application/json; charset=utf-8")
            .body(body)
    }
}
