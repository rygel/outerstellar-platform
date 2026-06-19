package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.RouteHeaderOverride
import io.github.rygel.outerstellar.platform.SecurityHeadersConfig
import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
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
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
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
import org.http4k.lens.RequestKey
import org.http4k.template.TemplateRenderer
import org.slf4j.LoggerFactory
import org.slf4j.MDC

private val analyticsLogger = LoggerFactory.getLogger("outerstellar.Filters.analytics")

private const val COOKIE_MAX_AGE_DAYS = 365L
private const val REQUEST_ID_HEADER = "X-Request-Id"
private const val LOG_ID_LENGTH = 8
private const val STATIC_ASSET_MAX_AGE = 31536000L
private const val CSP_NONCE_BYTES = 16
private const val DEFAULT_CSP_POLICY =
    "default-src 'self'; " +
        "script-src 'self' {nonce}; " +
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
    private val secureRandom = SecureRandom()
    val CSP_NONCE_KEY = RequestKey.optional<String>("request.cspNonce")

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

    /**
     * Rejects requests whose body exceeds [maxBytes] before the body is buffered into memory, returning 413. Defends
     * against oversized-body DoS (heap exhaustion / GC thrash from a single large POST). The check is on the
     * Content-Length header so the body is never read for oversized requests. Requests without a Content-Length (e.g.
     * chunked/streamed) are left to the handler, which is the http4k default; a hard cap on those would require
     * consuming the stream, which we avoid to keep the filter allocation-free.
     */
    fun maxBodySize(maxBytes: Long): Filter = Filter { next: HttpHandler ->
        { request ->
            val declared = request.header("Content-Length")?.toLongOrNull()
            if (declared != null && declared > maxBytes) {
                Response(Status.REQUEST_ENTITY_TOO_LARGE)
                    .header("content-type", "text/plain; charset=utf-8")
                    .body("Request body of $declared bytes exceeds the limit of $maxBytes bytes.")
            } else {
                next(request)
            }
        }
    }

    fun cors(allowedOrigins: String, headerConfig: SecurityHeadersConfig = SecurityHeadersConfig()): Filter =
        Filter { next: HttpHandler ->
            { request ->
                val path = request.uri.path
                val override = headerConfig.findOverride(path)
                val effectiveOrigins = override?.corsAllowedOrigins?.joinToString(", ") ?: allowedOrigins
                if (effectiveOrigins.isBlank()) return@Filter next(request)
                if (request.method == org.http4k.core.Method.OPTIONS) {
                    Response(Status.NO_CONTENT)
                        .header("Access-Control-Allow-Origin", effectiveOrigins)
                        .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                        .header("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Request-Id")
                        .header("Access-Control-Max-Age", "3600")
                } else {
                    val response = next(request)
                    response
                        .header("Access-Control-Allow-Origin", effectiveOrigins)
                        .header("Access-Control-Expose-Headers", "X-Request-Id, X-Session-Expired")
                }
            }
        }

    fun securityHeaders(
        cspPolicy: String = DEFAULT_CSP_POLICY,
        headerConfig: SecurityHeadersConfig = SecurityHeadersConfig(),
    ): Filter = Filter { next: HttpHandler ->
        { request ->
            val cspNonce = generateCspNonce()
            val requestWithNonce = request.with(CSP_NONCE_KEY of cspNonce)
            val response = next(requestWithNonce)
            val path = request.uri.path
            val override = headerConfig.findOverride(path)
            val effectivePermissionsPolicy = override?.permissionsPolicy ?: headerConfig.permissionsPolicy
            val effectiveReferrerPolicy = override?.referrerPolicy ?: headerConfig.referrerPolicy
            val effectiveFrameOptions = override?.xFrameOptions ?: headerConfig.xFrameOptions
            val effectiveContentTypeOptions = override?.xContentTypeOptions ?: headerConfig.xContentTypeOptions
            val effectiveHsts = override?.strictTransportSecurity ?: headerConfig.strictTransportSecurity
            response
                .header("X-Content-Type-Options", effectiveContentTypeOptions)
                .header("X-Frame-Options", effectiveFrameOptions)
                .header("Referrer-Policy", effectiveReferrerPolicy)
                .header("Permissions-Policy", effectivePermissionsPolicy)
                .let { resp ->
                    if (effectiveHsts.isNotBlank()) {
                        resp.header("Strict-Transport-Security", effectiveHsts)
                    } else {
                        resp
                    }
                }
                .let { resp ->
                    if (!path.startsWith("/api/")) {
                        val effectiveCsp = override?.csp ?: cspPolicy
                        resp.header("Content-Security-Policy", effectiveCsp.withCspNonce(cspNonce))
                    } else {
                        resp
                    }
                }
        }
    }

    private fun generateCspNonce(): String {
        val bytes = ByteArray(CSP_NONCE_BYTES)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun String.withCspNonce(cspNonce: String): String =
        if (contains("{nonce}")) {
            replace("{nonce}", "'nonce-$cspNonce'")
        } else {
            this
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
        sessionTimeoutMinutes: Int = 30,
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
                        response.header(
                            "Set-Cookie",
                            SessionCookie.create(token, sessionCookieSecure, sessionTimeoutMinutes * 60L),
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

    @Suppress("UnusedParameter")
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
                            // The _csrf cookie backs a double-submit token: the browser must read it
                            // client-side and submit the value back in the X-CSRF-Token header / _csrf
                            // form field. That requires the cookie to be *stored* over HTTP too (localhost
                            // dev, TLS-terminating reverse proxies). A Secure flag would make the browser
                            // refuse to store it over http:// per RFC 6265bis, silently breaking every
                            // state-changing request. CSRF protection comes from token-matching, not from
                            // transport — so the cookie must NOT be Secure. (The session cookie, which is
                            // auto-sent and transport-only, correctly keeps Secure.) httpOnly stays false
                            // so JS can read the token; SameSite=Strict provides the cross-site defence.
                            secure = false,
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
            val shellRenderer = request.shellRenderer
            val errorPage = errorPageFactory.buildErrorPage(shellRenderer, "not-found")
            try {
                Response(Status.NOT_FOUND).header("content-type", "text/html; charset=utf-8").body(renderer(errorPage))
            } catch (renderEx: Exception) {
                logErrorPageRenderFailure(
                    request,
                    Status.NOT_FOUND,
                    originalException = null,
                    renderException = renderEx,
                )
                emergencyErrorResponse(
                    status = Status.NOT_FOUND,
                    title = "Page not found",
                    message = "The requested page does not exist.",
                    request = request,
                )
            }
        }
    }

    private fun handleException(
        e: Exception,
        request: org.http4k.core.Request,
        errorPageFactory: ErrorPageFactory,
        renderer: TemplateRenderer,
    ): Response {
        // The OpenAPI spec-rendering endpoints (any path ending in openapi.json) fail on http4k 6.53
        // because the OpenApi3 renderer is incompatible with the kotlinx.serialization format
        // (http4k/http4k#750): the spec model contains internal JSON-element types (EmptyArray /
        // JsonLiteral) that have no registered serializer. Until that's fixed upstream, degrade to a
        // clear 503 instead of a generic 500 so consumers get an honest, non-alarming response and the
        // root cause is visible. See issue #558.
        val isOpenApiSerializationFailure =
            e is kotlinx.serialization.SerializationException && request.uri.path.endsWith("openapi.json")
        if (isOpenApiSerializationFailure) {
            logger.warn(
                "OpenAPI spec unavailable at {} — http4k OpenApi3/kotlinx.serialization incompatibility (http4k#750): {}",
                request.uri,
                e.message,
            )
            return jsonErrorResponse(
                Status.SERVICE_UNAVAILABLE,
                "OpenAPI spec is unavailable on this platform version (pending an http4k fix; see http4k#750).",
                request,
            )
        }

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
            val shellRenderer = request.shellRenderer
            val errorKind = if (status == Status.INTERNAL_SERVER_ERROR) "server-error" else "not-found"
            val errorPage = errorPageFactory.buildErrorPage(shellRenderer, errorKind)
            try {
                Response(status).header("content-type", "text/html; charset=utf-8").body(renderer(errorPage))
            } catch (renderEx: Exception) {
                logErrorPageRenderFailure(request, status, originalException = e, renderException = renderEx)
                plainTextOriginalErrorResponse(status, e)
            }
        }
    }

    private fun plainTextOriginalErrorResponse(status: Status, originalException: Exception): Response {
        val message = originalException.message ?: "An unexpected error occurred"
        return Response(status)
            .header("content-type", "text/plain; charset=utf-8")
            .body("Internal Server Error: $message")
    }

    private fun logErrorPageRenderFailure(
        request: org.http4k.core.Request,
        status: Status,
        originalException: Exception?,
        renderException: Exception,
    ) {
        val requestId = request.header(REQUEST_ID_HEADER) ?: "-"
        logger.error(
            "Error page rendering failed requestId={} method={} path={} status={} originalException={} originalMessage={} renderException={}: {}",
            requestId.take(LOG_ID_LENGTH),
            request.method,
            request.uri.path,
            status.code,
            originalException?.javaClass?.name ?: "-",
            originalException?.message ?: "-",
            renderException.javaClass.name,
            renderException.message,
            renderException,
        )
    }

    private fun emergencyErrorResponse(
        status: Status,
        title: String,
        message: String,
        request: org.http4k.core.Request,
    ): Response {
        val requestId = request.header(REQUEST_ID_HEADER) ?: "unavailable"
        val body =
            """
            <!doctype html>
            <html lang="en">
              <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>${escapeHtml(title)}</title>
              </head>
              <body>
                <main>
                  <h1>${escapeHtml(title)}</h1>
                  <p>${escapeHtml(message)}</p>
                  <p>Reference: ${escapeHtml(requestId)}</p>
                </main>
              </body>
            </html>
            """
                .trimIndent()
        return Response(status).header("content-type", "text/html; charset=utf-8").body(body)
    }

    private fun escapeHtml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

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

fun SecurityHeadersConfig.findOverride(path: String): RouteHeaderOverride? = perRouteOverrides.firstOrNull {
    PathPatternMatcher.matches(it.pattern, path)
}
