package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.banner.BannerProvider
import io.github.rygel.outerstellar.platform.model.InsufficientPermissionException
import io.github.rygel.outerstellar.platform.model.OuterstellarException
import io.github.rygel.outerstellar.platform.model.ThemeCatalog
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.ValidationException
import io.github.rygel.outerstellar.platform.persistence.QueryCount
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.security.SecurityRules
import io.github.rygel.outerstellar.platform.security.SessionService
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID
import org.http4k.core.Body
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
import org.http4k.format.KotlinxSerialization
import org.http4k.template.TemplateRenderer
import org.slf4j.LoggerFactory
import org.slf4j.MDC

private val analyticsLogger = LoggerFactory.getLogger("outerstellar.Filters.analytics")

private const val COOKIE_MAX_AGE_DAYS = 365L
private const val REQUEST_ID_HEADER = "X-Request-Id"
private const val LOG_ID_LENGTH = 8
private const val STATIC_ASSET_MAX_AGE = 31536000L
private const val DEFAULT_CSP_POLICY =
    "default-src 'self'; " +
        "script-src 'self'; " +
        "style-src 'self' 'unsafe-inline'; " +
        "font-src 'self'; " +
        "connect-src 'self' wss:; " +
        "img-src 'self' data:; " +
        "base-uri 'self'; " +
        "form-action 'self'"

private fun isNonPagePath(path: String): Boolean =
    path.startsWith("/api/") || path.startsWith("/static/") || path.startsWith("/ws/")

val etagCachingFilter: Filter = Filter { next: HttpHandler ->
    { request ->
        val response = next(request)
        val contentType = response.header("content-type") ?: ""
        val isCacheable =
            contentType.contains("text/css") ||
                contentType.contains("javascript") ||
                contentType.contains("font/") ||
                contentType.contains("image/")
        if (response.status == Status.OK && response.header("ETag") == null && isCacheable) {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = response.body.stream.readBytes()
            digest.update(bytes)
            val hash = digest.digest().take(8).joinToString("") { "%02x".format(it) }
            val etag = "\"$hash\""
            val ifNoneMatch = request.header("If-None-Match")
            if (ifNoneMatch == etag) {
                Response(Status.NOT_MODIFIED)
            } else {
                response.body(Body(ByteBuffer.wrap(bytes))).header("ETag", etag)
            }
        } else {
            response
        }
    }
}

val staticCacheControlFilter: Filter = Filter { next: HttpHandler ->
    { request ->
        val response = next(request)
        if (isStaticAsset(request.uri.path)) {
            response.header("Cache-Control", "public, max-age=$STATIC_ASSET_MAX_AGE, immutable")
        } else {
            response
        }
    }
}

fun analyticsPageViewFilter(analytics: AnalyticsService): Filter = Filter { next ->
    { request ->
        val response = next(request)
        val isTrackablePage = request.method == Method.GET && !isNonPagePath(request.uri.path)
        if (isTrackablePage) {
            try {
                val userId = request.requestContext.user?.id?.toString()
                if (userId != null) {
                    analytics.page(userId, request.uri.path)
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                analyticsLogger.debug("Failed to record page view: {}", e.message)
            }
        }
        response
    }
}

private fun isStaticAsset(path: String): Boolean =
    path.endsWith(".css") ||
        path.endsWith(".js") ||
        path.endsWith(".woff2") ||
        path.endsWith(".woff") ||
        path.endsWith(".ttf") ||
        path.endsWith(".png") ||
        path.endsWith(".svg") ||
        path.endsWith(".ico")

private fun preferenceCookie(
    value: String?,
    name: String,
    maxAge: Long,
    secure: Boolean,
    validator: (String) -> Boolean,
): Cookie? =
    value?.takeIf(validator)?.let {
        Cookie(name, it, maxAge = maxAge, path = "/", sameSite = SameSite.Strict, secure = secure)
    }

private fun persistUserPreferences(
    user: User?,
    langCookie: Cookie?,
    themeCookie: Cookie?,
    layoutCookie: Cookie?,
    userRepository: UserRepository,
) {
    if (user == null) return
    val hasChange = langCookie != null || themeCookie != null || layoutCookie != null
    if (!hasChange) return
    userRepository.updatePreferences(
        user.id,
        langCookie?.value ?: user.language,
        themeCookie?.value ?: user.theme,
        layoutCookie?.value ?: user.layout,
    )
}

object Filters {
    private val logger = LoggerFactory.getLogger(Filters::class.java)

    val correlationId: Filter = Filter { next: HttpHandler ->
        { request ->
            val requestId = request.header(REQUEST_ID_HEADER) ?: java.util.UUID.randomUUID().toString()
            MDC.put("requestId", requestId.take(LOG_ID_LENGTH))
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
            if (allowedOrigins.isBlank()) return@Filter next(request)
            if (request.method == org.http4k.core.Method.OPTIONS) {
                Response(Status.NO_CONTENT)
                    .header("Access-Control-Allow-Origin", allowedOrigins)
                    .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                    .header("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Request-Id")
                    .header("Access-Control-Max-Age", "3600")
            } else {
                val response = next(request)
                response
                    .header("Access-Control-Allow-Origin", allowedOrigins)
                    .header("Access-Control-Expose-Headers", "X-Request-Id, X-Session-Expired")
            }
        }
    }

    fun securityHeaders(cspPolicy: String = DEFAULT_CSP_POLICY): Filter = Filter { next: HttpHandler ->
        { request ->
            next(request)
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Frame-Options", "DENY")
                .header("Referrer-Policy", "strict-origin-when-cross-origin")
                .header("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
                .header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
                .let { response ->
                    if (!request.uri.path.startsWith("/api/")) {
                        response.header("Content-Security-Policy", cspPolicy)
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
            val queries = QueryCount.drain()
            if (queries > 0) {
                logger.info(
                    "[{}] {} {} -> {} ({}ms, {} DB queries)",
                    requestId.take(LOG_ID_LENGTH),
                    request.method,
                    request.uri,
                    response.status,
                    duration,
                    queries,
                )
            } else {
                logger.info(
                    "[{}] {} {} -> {} ({}ms)",
                    requestId.take(LOG_ID_LENGTH),
                    request.method,
                    request.uri,
                    response.status,
                    duration,
                )
            }
            response
        }
    }

    val serverMetrics: Filter =
        ServerFilters.MicrometerMetrics.RequestCounter(Metrics.registry)
            .then(ServerFilters.MicrometerMetrics.RequestTimer(Metrics.registry))

    val telemetry: Filter = ServerFilters.OpenTelemetryTracing(Telemetry.openTelemetry)

    fun devAutoLogin(
        enabled: Boolean,
        userRepository: UserRepository,
        sessionService: SessionService,
        sessionCookieSecure: Boolean,
    ): Filter = Filter { next ->
        { request ->
            val host = request.header("Host")
            val isLoopback =
                request.header("X-Forwarded-For") == null &&
                    (host == null || host.startsWith("localhost") || host.startsWith("127.0.0.1"))
            if (enabled && isLoopback && request.cookie(RequestContext.SESSION_COOKIE) == null) {
                val admin = userRepository.findByUsername("admin")
                if (admin != null) {
                    val token = sessionService.createSession(admin.id)
                    val response = next(request.cookie(Cookie(RequestContext.SESSION_COOKIE, token)))
                    if (response.cookies().none { it.name == RequestContext.SESSION_COOKIE }) {
                        response.header("Set-Cookie", SessionCookie.create(token, sessionCookieSecure))
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

    @Suppress("LongParameterList")
    fun stateFilter(
        devDashboardEnabled: Boolean,
        userRepository: UserRepository,
        appVersion: String = "dev",
        jwtService: io.github.rygel.outerstellar.platform.security.JwtService? = null,
        shellConfig: ShellConfig = ShellConfig(),
        cookieSecure: Boolean = true,
        sessionService: SessionService? = null,
    ): Filter = Filter { next: HttpHandler ->
        { request ->
            val requestContext = RequestContext(request, userRepository, jwtService, sessionService)
            val shellRenderer = ShellRenderer(requestContext, devDashboardEnabled, appVersion, shellConfig)
            val contextUser =
                try {
                    requestContext.user
                } catch (e: IllegalStateException) {
                    logger.debug("Could not resolve user from context: {}", e.message)
                    null
                }
            if (contextUser != null) {
                MDC.put("userId", contextUser.id.toString().take(LOG_ID_LENGTH))
                MDC.put("username", contextUser.username)
            }
            val response =
                next(request.with(RequestContext.KEY of requestContext).with(ShellRenderer.KEY of shellRenderer))

            val cookieMaxAge = Duration.ofDays(COOKIE_MAX_AGE_DAYS).toSeconds()

            val langCookie =
                preferenceCookie(request.query("lang"), RequestContext.LANG_COOKIE, cookieMaxAge, cookieSecure) {
                    it in RequestContext.SUPPORTED_LANGUAGES
                }
            val themeCookie =
                preferenceCookie(request.query("theme"), RequestContext.THEME_COOKIE, cookieMaxAge, cookieSecure) { v ->
                    ThemeCatalog.isValidTheme(v)
                }
            val layoutCookie =
                preferenceCookie(request.query("layout"), RequestContext.LAYOUT_COOKIE, cookieMaxAge, cookieSecure) {
                    it in RequestContext.SUPPORTED_LAYOUTS
                }
            val shellCookie =
                preferenceCookie(request.query("shell"), RequestContext.SHELL_COOKIE, cookieMaxAge, cookieSecure) {
                    it in RequestContext.SUPPORTED_SHELLS
                }

            persistUserPreferences(contextUser, langCookie, themeCookie, layoutCookie, userRepository)

            var updatedResponse = response
            if (langCookie != null) updatedResponse = updatedResponse.cookie(langCookie)
            if (themeCookie != null) updatedResponse = updatedResponse.cookie(themeCookie)
            if (layoutCookie != null) updatedResponse = updatedResponse.cookie(layoutCookie)
            if (shellCookie != null) updatedResponse = updatedResponse.cookie(shellCookie)

            updatedResponse
        }
    }

    fun sessionTimeout(sessionCookieSecure: Boolean): Filter = Filter { next: HttpHandler ->
        { request ->
            val ctx =
                try {
                    request.requestContext
                } catch (e: IllegalStateException) {
                    logger.debug("Could not resolve RequestContext for session timeout check: {}", e.message)
                    null
                }

            if (ctx?.sessionExpired == true && ctx.user == null) {
                if (request.uri.path.startsWith("/api/")) {
                    Response(Status.UNAUTHORIZED).header("X-Session-Expired", "true").body("Session expired")
                } else {
                    Response(Status.FOUND)
                        .header("location", "/auth?expired=true")
                        .header("Set-Cookie", SessionCookie.clear(sessionCookieSecure))
                }
            } else {
                next(request)
            }
        }
    }

    val securityFilter: Filter = Filter { next: HttpHandler ->
        { request ->
            val user =
                try {
                    request.requestContext.user
                } catch (e: IllegalStateException) {
                    logger.debug("Failed to extract user from context: {}", e.message)
                    null
                }
            next(request.with(SecurityRules.USER_KEY of user))
        }
    }

    fun csrfProtection(sessionCookieSecure: Boolean, enabled: Boolean = true): Filter = Filter { next ->
        { request ->
            if (!enabled) return@Filter next(request)

            val unsafeMethods = setOf(Method.POST, Method.PUT, Method.DELETE, Method.PATCH)
            val path = request.uri.path
            val exempt = path.startsWith("/api/v1/") || path.startsWith("/auth/oauth/")

            if (request.method in unsafeMethods && !exempt) {
                val cookieToken = request.cookie(RequestContext.CSRF_COOKIE)?.value
                val formToken = request.form("_csrf")
                val headerToken = request.header("X-CSRF-Token")
                val submitted = formToken ?: headerToken

                if (
                    cookieToken == null ||
                        submitted == null ||
                        !java.security.MessageDigest.isEqual(cookieToken.toByteArray(), submitted.toByteArray())
                ) {
                    logger.warn("CSRF check failed for {} {}", request.method, path)
                    Response(Status.FORBIDDEN).body("Invalid or missing CSRF token")
                } else {
                    next(request)
                }
            } else {
                val existingToken = request.cookie(RequestContext.CSRF_COOKIE)?.value
                if (existingToken == null) {
                    val newToken = UUID.randomUUID().toString()
                    val existingCookieHeader = request.header("Cookie")
                    val augmentedRequest =
                        request.header(
                            "Cookie",
                            if (existingCookieHeader != null) {
                                "${RequestContext.CSRF_COOKIE}=$newToken; $existingCookieHeader"
                            } else {
                                "${RequestContext.CSRF_COOKIE}=$newToken"
                            },
                        )
                    val response = next(augmentedRequest)
                    response.cookie(
                        Cookie(
                            RequestContext.CSRF_COOKIE,
                            newToken,
                            path = "/",
                            secure = sessionCookieSecure,
                            httpOnly = false,
                            sameSite = SameSite.Strict,
                        )
                    )
                } else {
                    next(request)
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun globalErrorHandler(errorPageFactory: ErrorPageFactory, renderer: TemplateRenderer): Filter =
        Filter { next: HttpHandler ->
            { request ->
                try {
                    val response = next(request)
                    if (response.status == Status.NOT_FOUND) {
                        handleNotFound(request, errorPageFactory, renderer)
                    } else {
                        response
                    }
                } catch (e: Exception) {
                    handleException(e, request, errorPageFactory, renderer)
                }
            }
        }

    private fun handleNotFound(
        request: org.http4k.core.Request,
        errorPageFactory: ErrorPageFactory,
        renderer: TemplateRenderer,
    ): Response {
        return if (request.uri.path.startsWith("/api/")) {
            jsonErrorResponse(Status.NOT_FOUND, "Resource not found", request)
        } else {
            val ctx =
                try {
                    request.requestContext
                } catch (e: IllegalStateException) {
                    logger.debug("RequestContext not found for error page: {}", e.message)
                    RequestContext(request)
                }
            val shellRenderer = runCatching { request.shellRenderer }.getOrDefault(ShellRenderer(ctx))
            val errorPage = errorPageFactory.buildErrorPage(shellRenderer, "not-found")
            Response(Status.NOT_FOUND).header("content-type", "text/html; charset=utf-8").body(renderer(errorPage))
        }
    }

    private fun handleException(
        e: Exception,
        request: org.http4k.core.Request,
        errorPageFactory: ErrorPageFactory,
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
            val safeMessage =
                if (e is OuterstellarException) e.message ?: "An unexpected error occurred"
                else "An unexpected error occurred"
            jsonErrorResponse(status, safeMessage, request)
        } else if (request.header("HX-Request") == "true") {
            val safeMessage = if (e is OuterstellarException) e.message ?: "Action failed" else "Action failed"
            Response(status).body(safeMessage)
        } else {
            val ctx =
                try {
                    request.requestContext
                } catch (ex: IllegalStateException) {
                    logger.debug("RequestContext not found for error page: {}", ex.message)
                    RequestContext(request)
                }
            val shellRenderer = runCatching { request.shellRenderer }.getOrDefault(ShellRenderer(ctx))
            val errorKind = if (status == Status.INTERNAL_SERVER_ERROR) "server-error" else "not-found"
            val errorPage = errorPageFactory.buildErrorPage(shellRenderer, errorKind)
            Response(status).header("content-type", "text/html; charset=utf-8").body(renderer(errorPage))
        }
    }

    private fun jsonErrorResponse(status: Status, message: String, request: org.http4k.core.Request): Response {
        val requestId = request.header(REQUEST_ID_HEADER) ?: "-"
        val body =
            KotlinxSerialization.asJsonObject(
                    mapOf("message" to message, "status" to status.code, "requestId" to requestId)
                )
                .toString()
        return Response(status).header("content-type", "application/json; charset=utf-8").body(body)
    }
}
