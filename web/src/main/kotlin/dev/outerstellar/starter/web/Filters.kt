package dev.outerstellar.starter.web

import dev.outerstellar.starter.model.OuterstellarException
import dev.outerstellar.starter.model.ValidationException
import dev.outerstellar.starter.security.SecurityRules
import dev.outerstellar.starter.security.UserRepository
import java.time.Duration
import java.time.Instant
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.core.cookie.cookies
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.filter.MicrometerMetrics
import org.http4k.filter.OpenTelemetryTracing
import org.http4k.filter.ServerFilters
import org.http4k.format.Jackson
import org.http4k.template.TemplateRenderer
import org.slf4j.LoggerFactory

private const val COOKIE_MAX_AGE_DAYS = 365L
private const val REQUEST_ID_HEADER = "X-Request-Id"

object Filters {
    private val logger = LoggerFactory.getLogger(Filters::class.java)

    val correlationId: Filter = Filter { next: HttpHandler ->
        { request ->
            val requestId =
                request.header(REQUEST_ID_HEADER) ?: java.util.UUID.randomUUID().toString()
            val response = next(request.header(REQUEST_ID_HEADER, requestId))
            response.header(REQUEST_ID_HEADER, requestId)
        }
    }

    fun cors(allowedOrigins: String): Filter = Filter { next: HttpHandler ->
        { request ->
            if (request.method == org.http4k.core.Method.OPTIONS) {
                Response(Status.NO_CONTENT)
                    .header("Access-Control-Allow-Origin", allowedOrigins)
                    .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                    .header(
                        "Access-Control-Allow-Headers",
                        "Authorization, Content-Type, X-Request-Id",
                    )
                    .header("Access-Control-Max-Age", "3600")
            } else {
                val response = next(request)
                response
                    .header("Access-Control-Allow-Origin", allowedOrigins)
                    .header("Access-Control-Expose-Headers", "X-Request-Id, X-Session-Expired")
            }
        }
    }

    val securityHeaders: Filter = Filter { next: HttpHandler ->
        { request ->
            next(request)
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Frame-Options", "DENY")
                .header("Referrer-Policy", "strict-origin-when-cross-origin")
                .header("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
                .let { response ->
                    if (!request.uri.path.startsWith("/api/")) {
                        response.header(
                            "Content-Security-Policy",
                            "default-src 'self'; " +
                                "script-src 'self' 'unsafe-inline' https://unpkg.com; " +
                                "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " +
                                "font-src 'self' https://cdn.jsdelivr.net; " +
                                "connect-src 'self' ws: wss:; " +
                                "img-src 'self' data:;",
                        )
                    } else {
                        response
                    }
                }
        }
    }

    val requestLogging: Filter = Filter { next: HttpHandler ->
        { request ->
            val start = System.currentTimeMillis()
            val response = next(request)
            val duration = System.currentTimeMillis() - start
            val requestId = request.header(REQUEST_ID_HEADER) ?: "-"
            logger.info(
                "[{}] {} {} -> {} ({}ms)",
                requestId.take(8),
                request.method,
                request.uri,
                response.status,
                duration,
            )
            response
        }
    }

    val serverMetrics: Filter =
        ServerFilters.MicrometerMetrics.RequestCounter(Metrics.registry)
            .then(ServerFilters.MicrometerMetrics.RequestTimer(Metrics.registry))

    val telemetry: Filter = ServerFilters.OpenTelemetryTracing(Telemetry.openTelemetry)

    fun devAutoLogin(enabled: Boolean, userRepository: UserRepository): Filter = Filter { next ->
        { request ->
            if (enabled && request.cookie(WebContext.SESSION_COOKIE) == null) {
                val admin = userRepository.findByUsername("admin")
                if (admin != null) {
                    val response =
                        next(request.cookie(Cookie(WebContext.SESSION_COOKIE, admin.id.toString())))
                    // Also ensure the cookie is set in the response so the browser keeps it
                    if (response.cookies().none { it.name == WebContext.SESSION_COOKIE }) {
                        response.cookie(
                            Cookie(WebContext.SESSION_COOKIE, admin.id.toString(), path = "/")
                        )
                    } else {
                        response
                    }
                } else {
                    next(request)
                }
            } else {
                next(request)
            }
        }
    }

    fun stateFilter(devDashboardEnabled: Boolean, userRepository: UserRepository): Filter =
        Filter { next: HttpHandler ->
            { request ->
                val context = WebContext(request, devDashboardEnabled, userRepository)
                val response = next(request.with(WebContext.KEY of context))

                val cookieMaxAge = Duration.ofDays(COOKIE_MAX_AGE_DAYS).toSeconds()

                val langCookie =
                    request
                        .query("lang")
                        ?.takeIf { it in setOf("en", "fr") }
                        ?.let {
                            Cookie(WebContext.LANG_COOKIE, it, maxAge = cookieMaxAge, path = "/")
                        }
                val themeCookie =
                    request
                        .query("theme")
                        ?.takeIf { v -> ThemeCatalog.allThemes().any { it.id == v } }
                        ?.let {
                            Cookie(WebContext.THEME_COOKIE, it, maxAge = cookieMaxAge, path = "/")
                        }
                val layoutCookie =
                    request
                        .query("layout")
                        ?.takeIf { it in setOf("nice", "cozy", "compact") }
                        ?.let {
                            Cookie(WebContext.LAYOUT_COOKIE, it, maxAge = cookieMaxAge, path = "/")
                        }

                var updatedResponse = response
                if (langCookie != null) updatedResponse = updatedResponse.cookie(langCookie)
                if (themeCookie != null) updatedResponse = updatedResponse.cookie(themeCookie)
                if (layoutCookie != null) updatedResponse = updatedResponse.cookie(layoutCookie)

                updatedResponse
            }
        }

    fun sessionTimeout(
        timeoutMinutes: Int,
        userRepository: UserRepository,
        sessionCookieSecure: Boolean,
    ): Filter = Filter { next: HttpHandler ->
        { request ->
            val user =
                try {
                    request.webContext.user
                } catch (e: IllegalStateException) {
                    null
                }

            if (user != null && user.lastActivityAt != null) {
                val elapsed = Duration.between(user.lastActivityAt, Instant.now())
                if (elapsed.toMinutes() >= timeoutMinutes) {
                    logger.info(
                        "Session expired for user {} after {} minutes",
                        user.username,
                        elapsed.toMinutes(),
                    )
                    if (request.uri.path.startsWith("/api/")) {
                        Response(Status.UNAUTHORIZED)
                            .header("X-Session-Expired", "true")
                            .body("Session expired")
                    } else {
                        Response(Status.FOUND)
                            .header("location", "/auth?expired=true")
                            .header("Set-Cookie", SessionCookie.clear(sessionCookieSecure))
                    }
                } else {
                    userRepository.updateLastActivity(user.id)
                    next(request)
                }
            } else {
                if (user != null) {
                    userRepository.updateLastActivity(user.id)
                }
                next(request)
            }
        }
    }

    // Bridge WebContext user into SecurityRules
    val securityFilter: Filter = Filter { next: HttpHandler ->
        { request ->
            val user =
                try {
                    request.webContext.user
                } catch (e: IllegalStateException) {
                    logger.debug("Failed to extract user from context: {}", e.message)
                    null
                }
            next(request.with(SecurityRules.USER_KEY of user))
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun globalErrorHandler(pageFactory: WebPageFactory, renderer: TemplateRenderer): Filter =
        Filter { next: HttpHandler ->
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
        renderer: TemplateRenderer,
    ): Response {
        return if (request.uri.path.startsWith("/api/")) {
            jsonErrorResponse(Status.NOT_FOUND, "Resource not found")
        } else {
            val ctx =
                try {
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
        renderer: TemplateRenderer,
    ): Response {
        val status =
            when (e) {
                is ValidationException -> Status.BAD_REQUEST
                is OuterstellarException -> Status.BAD_REQUEST
                else -> Status.INTERNAL_SERVER_ERROR
            }

        logger.error("Error handling request {}: {}", request.uri, e.message, e)

        return if (request.uri.path.startsWith("/api/")) {
            jsonErrorResponse(status, e.message ?: "An unexpected error occurred")
        } else if (request.header("HX-Request") == "true") {
            Response(status).body(e.message ?: "Action failed")
        } else {
            val ctx =
                try {
                    request.webContext
                } catch (ex: IllegalStateException) {
                    logger.debug("WebContext not found for error page: {}", ex.message)
                    WebContext(request)
                }
            val errorKind =
                if (status == Status.INTERNAL_SERVER_ERROR) "server-error" else "not-found"
            val errorPage = pageFactory.buildErrorPage(ctx, errorKind)
            Response(status)
                .header("content-type", "text/html; charset=utf-8")
                .body(renderer(errorPage))
        }
    }

    private fun jsonErrorResponse(status: Status, message: String): Response {
        val body =
            Jackson.asJsonObject(mapOf("message" to message, "status" to status.code)).toString()
        return Response(status).header("content-type", "application/json; charset=utf-8").body(body)
    }
}
