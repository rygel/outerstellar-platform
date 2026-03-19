package dev.outerstellar.platform

import dev.outerstellar.platform.analytics.AnalyticsService
import dev.outerstellar.platform.analytics.NoOpAnalyticsService
import dev.outerstellar.platform.persistence.MessageCache
import dev.outerstellar.platform.persistence.OutboxRepository
import dev.outerstellar.platform.security.AppleOAuthProvider
import dev.outerstellar.platform.security.SecurityRules
import dev.outerstellar.platform.security.SecurityService
import dev.outerstellar.platform.security.UserRepository
import dev.outerstellar.platform.security.UserRole
import dev.outerstellar.platform.service.MessageService
import dev.outerstellar.platform.web.AuthApi
import dev.outerstellar.platform.web.AuthRoutes
import dev.outerstellar.platform.web.ComponentRoutes
import dev.outerstellar.platform.web.ContactsRoutes
import dev.outerstellar.platform.web.DevDashboardRoutes
import dev.outerstellar.platform.web.DeviceRegistrationApi
import dev.outerstellar.platform.web.ErrorRoutes
import dev.outerstellar.platform.web.Filters
import dev.outerstellar.platform.web.HomeRoutes
import dev.outerstellar.platform.web.NotificationApi
import dev.outerstellar.platform.web.NotificationRoutes
import dev.outerstellar.platform.web.OAuthRoutes
import dev.outerstellar.platform.web.PluginContext
import dev.outerstellar.platform.web.StarterPlugin
import dev.outerstellar.platform.web.SyncApi
import dev.outerstellar.platform.web.SyncWebSocket
import dev.outerstellar.platform.web.UserAdminApi
import dev.outerstellar.platform.web.UserAdminRoutes
import dev.outerstellar.platform.web.WebPageFactory
import dev.outerstellar.platform.web.rateLimitFilter
import dev.outerstellar.platform.web.webContext
import org.http4k.contract.bindContract
import org.http4k.contract.contract
import org.http4k.contract.openapi.ApiInfo
import org.http4k.contract.openapi.v3.OpenApi3
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.format.Jackson
import org.http4k.routing.ResourceLoader
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.routing.websocket.bind as wsBind
import org.http4k.routing.websockets
import org.http4k.server.PolyHandler
import org.http4k.template.TemplateRenderer
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("dev.outerstellar.platform.App")

@Suppress(
    "LongParameterList",
    "LongMethod",
    "TooGenericExceptionCaught",
    "SwallowedException",
    "DEPRECATION",
)
fun app(
    messageService: MessageService,
    contactService: dev.outerstellar.platform.service.ContactService,
    outboxRepository: OutboxRepository,
    cache: MessageCache,
    jteRenderer: TemplateRenderer,
    pageFactory: WebPageFactory,
    config: AppConfig,
    securityService: SecurityService,
    userRepository: UserRepository,
    deviceTokenRepository: dev.outerstellar.platform.security.DeviceTokenRepository? = null,
    analytics: AnalyticsService = NoOpAnalyticsService(),
    notificationService: dev.outerstellar.platform.service.NotificationService? = null,
    jwtService: dev.outerstellar.platform.security.JwtService? = null,
    plugin: StarterPlugin? = null,
): PolyHandler {
    logger.info("Initializing Outerstellar application")

    val bearerAuthFilter = Filter { next ->
        { req ->
            val token = req.header("Authorization")?.removePrefix("Bearer ")
            if (token == null) {
                Response(Status.UNAUTHORIZED).body("API token required")
            } else {
                when (val result = securityService.lookupSession(token)) {
                    is dev.outerstellar.platform.security.SessionLookup.Active ->
                        next(req.with(SecurityRules.USER_KEY of result.user))
                    is dev.outerstellar.platform.security.SessionLookup.Expired ->
                        Response(Status.UNAUTHORIZED)
                            .header("X-Session-Expired", "true")
                            .body("Session expired")
                    is dev.outerstellar.platform.security.SessionLookup.NotFound -> {
                        val apiKeyUser = securityService.authenticateApiKey(token)
                        if (apiKeyUser != null) {
                            next(req.with(SecurityRules.USER_KEY of apiKeyUser))
                        } else {
                            Response(Status.UNAUTHORIZED).body("API token required")
                        }
                    }
                }
            }
        }
    }

    // 1. Data/Sync API (JSON)
    val apiRoutes = contract {
        renderer = OpenApi3(ApiInfo("Outerstellar Sync API", "v1.0"), Jackson)
        descriptionPath = "/api/openapi.json"
        routes += AuthApi(securityService).routes

        // SyncContract is a RoutingHttpHandler, but we bind it separately below
        routes += emptyList<org.http4k.contract.ContractRoute>()
    }

    // 2. Main UI (Full HTML Pages)
    val uiRoutes = contract {
        renderer = OpenApi3(ApiInfo("Outerstellar UI", "v1.0"), Jackson)
        descriptionPath = "/ui/openapi.json"
        routes += HomeRoutes(messageService, pageFactory, jteRenderer).routes
        routes += ContactsRoutes(pageFactory, jteRenderer, contactService).routes
        routes +=
            AuthRoutes(
                    pageFactory,
                    jteRenderer,
                    securityService,
                    config.sessionCookieSecure,
                    analytics,
                )
                .routes
        routes +=
            OAuthRoutes(
                    providers = mapOf("apple" to AppleOAuthProvider()),
                    securityService = securityService,
                    sessionCookieSecure = config.sessionCookieSecure,
                )
                .routes
        routes += ErrorRoutes(pageFactory, jteRenderer).routes
        if (notificationService != null) {
            routes += NotificationRoutes(pageFactory, jteRenderer, notificationService).routes
        }
        if (plugin != null) {
            val pluginContext =
                PluginContext(
                    jteRenderer,
                    config,
                    securityService,
                    userRepository,
                    analytics,
                    notificationService,
                )
            routes += plugin.routes(pluginContext)
        }

        // Global Logout
        routes +=
            ("/logout" bindContract POST).to { request: org.http4k.core.Request ->
                Response(Status.FOUND)
                    .header("location", request.webContext.url("/"))
                    .header(
                        "Set-Cookie",
                        dev.outerstellar.platform.web.SessionCookie.clear(
                            config.sessionCookieSecure
                        ),
                    )
            }
    }

    // 3. Protected Admin Routes
    val adminContract = contract {
        renderer = OpenApi3(ApiInfo("Outerstellar Admin", "v1.0"), Jackson)
        descriptionPath = "/admin/openapi.json"
        security =
            object : org.http4k.security.Security {
                override val filter = Filter { next ->
                    SecurityRules.authenticated(SecurityRules.hasRole(UserRole.ADMIN, next))
                }
            }
        routes +=
            DevDashboardRoutes(
                    outboxRepository,
                    cache,
                    pageFactory,
                    jteRenderer,
                    config.devDashboardEnabled,
                )
                .routes
        routes += UserAdminRoutes(pageFactory, jteRenderer, securityService).routes
    }

    val bearerSecurity =
        object : org.http4k.security.Security {
            override val filter = bearerAuthFilter
        }

    val bearerAdminSecurity =
        object : org.http4k.security.Security {
            override val filter = Filter { next ->
                bearerAuthFilter.then(
                    Filter { inner -> SecurityRules.hasRole(UserRole.ADMIN, inner) }
                )(next)
            }
        }

    val syncContract = contract {
        renderer = OpenApi3(ApiInfo("Sync", "v1.0"), Jackson)
        descriptionPath = "/api/v1/sync/openapi.json"
        security = bearerSecurity
        routes += SyncApi(messageService, contactService, analytics).routes
        routes += AuthApi(securityService).bearerRoutes
        if (deviceTokenRepository != null) {
            routes += DeviceRegistrationApi(deviceTokenRepository).routes
        }
        if (notificationService != null) {
            routes += NotificationApi(notificationService).routes
        }
    }

    // Bearer + Admin protected API routes (user admin)
    val bearerAdminApiContract = contract {
        renderer = OpenApi3(ApiInfo("Outerstellar Admin API", "v1.0"), Jackson)
        descriptionPath = "/api/v1/admin/api-openapi.json"
        security = bearerAdminSecurity
        routes += UserAdminApi(securityService).routes
    }

    // 4. HTMX Components (HTML Fragments)
    val componentRoutes = contract {
        renderer = OpenApi3(ApiInfo("Outerstellar Components", "v1.0"), Jackson)
        descriptionPath = "/components/openapi.json"
        routes += ComponentRoutes(pageFactory, jteRenderer).routes
    }

    // Filtered admin handler
    val filteredAdminHandler =
        Filter { next -> SecurityRules.authenticated(SecurityRules.hasRole(UserRole.ADMIN, next)) }
            .then(adminContract)

    val metricsHandler =
        Filter { next -> SecurityRules.authenticated(SecurityRules.hasRole(UserRole.ADMIN, next)) }
            .then {
                Response(Status.OK).body(dev.outerstellar.platform.web.Metrics.registry.scrape())
            }

    val baseApp: HttpHandler =
        routes(
            static(ResourceLoader.Classpath("static")),
            bearerAdminApiContract,
            apiRoutes,
            syncContract,
            uiRoutes,
            componentRoutes,
            "/" bind filteredAdminHandler,
            "/health" bind
                GET to
                {
                    val checks = mutableMapOf<String, Any>("status" to "UP")
                    try {
                        val userCount = userRepository.findAll().size
                        checks["database"] = mapOf("status" to "UP", "users" to userCount)
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        checks["status"] = "DOWN"
                        checks["database"] =
                            mapOf("status" to "DOWN", "error" to (e.message ?: "unknown"))
                    }
                    checks["timestamp"] = java.time.Instant.now().toString()
                    val status =
                        if (checks["status"] == "UP") Status.OK else Status.SERVICE_UNAVAILABLE
                    Response(status)
                        .header("content-type", "application/json; charset=utf-8")
                        .body(Jackson.asJsonObject(checks).toString())
                },
            "/metrics" bind GET to metricsHandler,
        )

    val httpHandler =
        Filters.correlationId
            .then(Filters.cors(config.corsOrigins))
            .then(Filters.securityHeaders)
            .then(Filters.telemetry)
            .then(rateLimitFilter())
            .then(Filters.csrfProtection(config.sessionCookieSecure, config.csrfEnabled))
            .then(Filters.devAutoLogin(config.devMode, userRepository))
            .then(
                Filters.stateFilter(
                    config.devDashboardEnabled,
                    userRepository,
                    config.version,
                    jwtService,
                    plugin?.navItems ?: emptyList(),
                )
            )
            .then(Filters.analyticsPageView(analytics))
            .then(
                Filters.sessionTimeout(
                    config.sessionTimeoutMinutes,
                    userRepository,
                    config.sessionCookieSecure,
                )
            )
            .then(Filters.securityFilter)
            .then(Filters.requestLogging)
            .then(Filters.serverMetrics)
            .then(Filters.globalErrorHandler(pageFactory, jteRenderer))
            .then(baseApp)

    val wsHandler = websockets("/ws/sync" wsBind SyncWebSocket.handler)

    return PolyHandler(httpHandler, wsHandler)
}
