package dev.outerstellar.platform.web

import dev.outerstellar.platform.analytics.AnalyticsService
import dev.outerstellar.platform.model.InsufficientPermissionException
import dev.outerstellar.platform.model.OuterstellarException
import dev.outerstellar.platform.model.ValidationException
import dev.outerstellar.platform.security.SecurityRules
import dev.outerstellar.platform.security.UserRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.SameSite
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
import org.slf4j.MDC

private const val COOKIE_MAX_AGE_DAYS = 365L
private const val REQUEST_ID_HEADER = "X-Request-Id"

object Filters {
    private val logger = LoggerFactory.getLogger(Filters::class.java)

    val correlationId: Filter = Filter { next: HttpHandler ->
        { request ->
            val requestId =
                request.header(REQUEST_ID_HEADER) ?: java.util.UUID.randomUUID().toString()
            MDC.put("requestId", requestId.take(8))
            MDC.put("method", request.method.name)
            MDC.put("path", request.uri.path)
            try {
                val response = next(request.header(REQUEST_ID_HEADER, requestId))
                response.header(REQUEST_ID_HEADER, requestId)
            } finally {
                MDC.clear()
            }
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
                                "script-src 'self' 'unsafe-inline'; " +
                                "style-src 'self' 'unsafe-inline'; " +
                                "font-src 'self'; " +
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

    fun stateFilter(
        devDashboardEnabled: Boolean,
        userRepository: UserRepository,
        appVersion: String = "dev",
        jwtService: dev.outerstellar.platform.security.JwtService? = null,
        pluginNavItems: List<PluginNavItem> = emptyList(),
    ): Filter = Filter { next: HttpHandler ->
        { request ->
            val context =
                WebContext(
                    request,
                    devDashboardEnabled,
                    userRepository,
                    appVersion,
                    jwtService,
                    pluginNavItems,
                )
            val contextUser =
                try {
                    context.user
                } catch (e: Exception) {
                    null
                }
            if (contextUser != null) {
                MDC.put("userId", contextUser.id.toString().take(8))
                MDC.put("username", contextUser.username)
            }
            val response = next(request.with(WebContext.KEY of context))

            val cookieMaxAge = Duration.ofDays(COOKIE_MAX_AGE_DAYS).toSeconds()

            val langCookie =
                request
                    .query("lang")
                    ?.takeIf { it in setOf("en", "fr") }
                    ?.let { Cookie(WebContext.LANG_COOKIE, it, maxAge = cookieMaxAge, path = "/") }
            val themeCookie =
                request
                    .query("theme")
                    ?.takeIf { v -> ThemeCatalog.allThemes().any { it.id == v } }
                    ?.let { Cookie(WebContext.THEME_COOKIE, it, maxAge = cookieMaxAge, path = "/") }
            val layoutCookie =
                request
                    .query("layout")
                    ?.takeIf { it in setOf("nice", "cozy", "compact") }
                    ?.let {
                        Cookie(WebContext.LAYOUT_COOKIE, it, maxAge = cookieMaxAge, path = "/")
                    }
            val shellCookie =
                request
                    .query("shell")
                    ?.takeIf { it in setOf("sidebar", "topbar") }
                    ?.let { Cookie(WebContext.SHELL_COOKIE, it, maxAge = cookieMaxAge, path = "/") }

            var updatedResponse = response
            if (langCookie != null) updatedResponse = updatedResponse.cookie(langCookie)
            if (themeCookie != null) updatedResponse = updatedResponse.cookie(themeCookie)
            if (layoutCookie != null) updatedResponse = updatedResponse.cookie(layoutCookie)
            if (shellCookie != null) updatedResponse = updatedResponse.cookie(shellCookie)

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

    /**
     * Double-submit cookie CSRF protection for HTML form routes.
     * - Ensures every response carries a `_csrf` cookie with a random token.
     * - On unsafe methods (POST/PUT/DELETE/PATCH): rejects requests where the form field `_csrf` or
     *   header `X-CSRF-Token` does not match the cookie.
     * - Exempts `/api/v1/` routes (Bearer-token auth) and `/oauth/` routes.
     */
    fun csrfProtection(sessionCookieSecure: Boolean, enabled: Boolean = true): Filter =
        Filter { next ->
            { request ->
                if (!enabled) return@Filter next(request)

                val unsafeMethods = setOf(Method.POST, Method.PUT, Method.DELETE, Method.PATCH)
                val path = request.uri.path
                val exempt = path.startsWith("/api/v1/") || path.startsWith("/oauth/")

                if (request.method in unsafeMethods && !exempt) {
                    val cookieToken = request.cookie(WebContext.CSRF_COOKIE)?.value
                    val formToken = request.form("_csrf")
                    val headerToken = request.header("X-CSRF-Token")
                    val submitted = formToken ?: headerToken

                    if (cookieToken == null || submitted == null || cookieToken != submitted) {
                        logger.warn("CSRF check failed for {} {}", request.method, path)
                        Response(Status.FORBIDDEN).body("Invalid or missing CSRF token")
                    } else {
                        next(request)
                    }
                } else {
                    // On safe methods: ensure the CSRF cookie is present; set it if missing.
                    // IMPORTANT: pre-generate the token and inject it into the request's Cookie
                    // header *before* calling the handler, so that WebContext.csrfToken reads the
                    // same value that we set in the response cookie. Without this, WebContext would
                    // generate an independent random UUID for the meta tag while the filter sets a
                    // different one in the cookie — making the first-visit CSRF check always fail.
                    val existingToken = request.cookie(WebContext.CSRF_COOKIE)?.value
                    if (existingToken == null) {
                        val newToken = UUID.randomUUID().toString()
                        val existingCookieHeader = request.header("Cookie")
                        val augmentedRequest =
                            request.header(
                                "Cookie",
                                if (existingCookieHeader != null) {
                                    "${WebContext.CSRF_COOKIE}=$newToken; $existingCookieHeader"
                                } else {
                                    "${WebContext.CSRF_COOKIE}=$newToken"
                                },
                            )
                        val response = next(augmentedRequest)
                        response.cookie(
                            Cookie(
                                WebContext.CSRF_COOKIE,
                                newToken,
                                path = "/",
                                secure = sessionCookieSecure,
                                httpOnly =
                                    false, // must be readable by JS for HTMX header injection
                                sameSite = SameSite.Strict,
                            )
                        )
                    } else {
                        next(request)
                    }
                }
            }
        }

    fun analyticsPageView(analytics: AnalyticsService): Filter = Filter { next ->
        { request ->
            val response = next(request)
            if (
                request.method == org.http4k.core.Method.GET &&
                    !request.uri.path.startsWith("/api/") &&
                    !request.uri.path.startsWith("/static/") &&
                    !request.uri.path.startsWith("/ws/")
            ) {
                try {
                    val userId = request.webContext.user?.id?.toString()
                    if (userId != null) {
                        analytics.page(userId, request.uri.path)
                    }
                } catch (_: Exception) {
                    // Not authenticated or context unavailable — skip page view
                }
            }
            response
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
                is InsufficientPermissionException -> Status.FORBIDDEN
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
