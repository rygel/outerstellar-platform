package io.github.rygel.outerstellar.platform

import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.analytics.NoOpAnalyticsService
import io.github.rygel.outerstellar.platform.persistence.MessageCache
import io.github.rygel.outerstellar.platform.persistence.OutboxRepository
import io.github.rygel.outerstellar.platform.security.AppleOAuthProvider
import io.github.rygel.outerstellar.platform.security.SecurityRules
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.UserRepository
import io.github.rygel.outerstellar.platform.security.UserRole
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.web.AuthApi
import io.github.rygel.outerstellar.platform.web.AuthRoutes
import io.github.rygel.outerstellar.platform.web.ComponentRoutes
import io.github.rygel.outerstellar.platform.web.ContactsRoutes
import io.github.rygel.outerstellar.platform.web.DevDashboardRoutes
import io.github.rygel.outerstellar.platform.web.DeviceRegistrationApi
import io.github.rygel.outerstellar.platform.web.ErrorRoutes
import io.github.rygel.outerstellar.platform.web.Filters
import io.github.rygel.outerstellar.platform.web.HomeRoutes
import io.github.rygel.outerstellar.platform.web.NotificationApi
import io.github.rygel.outerstellar.platform.web.NotificationRoutes
import io.github.rygel.outerstellar.platform.web.OAuthRoutes
import io.github.rygel.outerstellar.platform.web.PlatformPlugin
import io.github.rygel.outerstellar.platform.web.SearchRoutes
import io.github.rygel.outerstellar.platform.web.SettingsRoutes
import io.github.rygel.outerstellar.platform.web.PluginContext
import io.github.rygel.outerstellar.platform.web.SyncApi
import io.github.rygel.outerstellar.platform.web.SyncWebSocket
import io.github.rygel.outerstellar.platform.web.UserAdminApi
import io.github.rygel.outerstellar.platform.web.UserAdminRoutes
import io.github.rygel.outerstellar.platform.web.WebPageFactory
import io.github.rygel.outerstellar.platform.web.rateLimitFilter
import io.github.rygel.outerstellar.platform.web.webContext
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
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.routing.websockets
import org.http4k.server.PolyHandler
import org.http4k.template.TemplateRenderer
import org.slf4j.LoggerFactory
import org.http4k.routing.websocket.bind as wsBind

private val logger = LoggerFactory.getLogger("io.github.rygel.outerstellar.platform.App")

@Suppress("LongParameterList", "TooGenericExceptionCaught", "SwallowedException", "DEPRECATION")
fun app(
    messageService: MessageService,
    contactService: io.github.rygel.outerstellar.platform.service.ContactService,
    outboxRepository: OutboxRepository,
    cache: MessageCache,
    jteRenderer: TemplateRenderer,
    pageFactory: WebPageFactory,
    config: AppConfig,
    securityService: SecurityService,
    userRepository: UserRepository,
    deviceTokenRepository: io.github.rygel.outerstellar.platform.security.DeviceTokenRepository? =
        null,
    analytics: AnalyticsService = NoOpAnalyticsService(),
    notificationService: io.github.rygel.outerstellar.platform.service.NotificationService? = null,
    jwtService: io.github.rygel.outerstellar.platform.security.JwtService? = null,
    plugin: PlatformPlugin? = null,
    activityUpdater: io.github.rygel.outerstellar.platform.security.AsyncActivityUpdater? = null,
    syncWebSocket: SyncWebSocket? = null,
): PolyHandler {
    logger.info("Initializing Outerstellar application")

    val appLabel = plugin?.appLabel ?: "Outerstellar"
    val excludedRoutes = plugin?.excludeDefaultRoutes ?: emptySet()
    val httpHandler =
        assembleHttpHandler(
            appLabel,
            excludedRoutes,
            messageService,
            contactService,
            outboxRepository,
            cache,
            jteRenderer,
            pageFactory,
            config,
            securityService,
            userRepository,
            deviceTokenRepository,
            analytics,
            notificationService,
            jwtService,
            plugin,
            activityUpdater,
        )
    val wsHandler = syncWebSocket?.let { websockets("/ws/sync" wsBind it.handler) }

    return PolyHandler(httpHandler, wsHandler)
}

@Suppress("LongParameterList")
private fun assembleHttpHandler(
    appLabel: String,
    excludedRoutes: Set<String>,
    messageService: MessageService,
    contactService: io.github.rygel.outerstellar.platform.service.ContactService,
    outboxRepository: OutboxRepository,
    cache: MessageCache,
    jteRenderer: TemplateRenderer,
    pageFactory: WebPageFactory,
    config: AppConfig,
    securityService: SecurityService,
    userRepository: UserRepository,
    deviceTokenRepository: io.github.rygel.outerstellar.platform.security.DeviceTokenRepository?,
    analytics: AnalyticsService,
    notificationService: io.github.rygel.outerstellar.platform.service.NotificationService?,
    jwtService: io.github.rygel.outerstellar.platform.security.JwtService?,
    plugin: PlatformPlugin?,
    activityUpdater: io.github.rygel.outerstellar.platform.security.AsyncActivityUpdater?,
): HttpHandler {
    val (bearerSecurity, bearerAdminSecurity) = buildBearerSecurityPair(securityService)
    val apiRoutes =
        buildApiRoutes(
            appLabel,
            securityService,
            bearerSecurity,
            bearerAdminSecurity,
            messageService,
            contactService,
            analytics,
            deviceTokenRepository,
            notificationService,
        )
    val uiRoutes =
        buildUiRoutes(
            appLabel,
            excludedRoutes,
            messageService,
            pageFactory,
            jteRenderer,
            contactService,
            securityService,
            config,
            analytics,
            notificationService,
            userRepository,
            plugin,
        )
    val adminRoutes =
        buildAdminRoutes(
            appLabel,
            outboxRepository,
            cache,
            pageFactory,
            jteRenderer,
            config,
            securityService,
        )
    val componentRoutes = buildComponentRoutes(appLabel, pageFactory, jteRenderer)
    val baseApp =
        buildBaseApp(
            excludedRoutes,
            adminRoutes,
            apiRoutes,
            uiRoutes,
            componentRoutes,
            userRepository,
        )
    return buildFilterChain(
        config,
        userRepository,
        analytics,
        jwtService,
        plugin,
        activityUpdater,
        pageFactory,
        jteRenderer,
    )
        .then(baseApp)
}

private fun buildBearerSecurityPair(
    securityService: SecurityService
): Pair<org.http4k.security.Security, org.http4k.security.Security> {
    val bearerAuthFilter = Filter { next ->
        {
                req ->
            val token = req.header("Authorization")?.removePrefix("Bearer ")
            if (token == null) {
                Response(Status.UNAUTHORIZED).body("API token required")
            } else {
                when (val result = securityService.lookupSession(token)) {
                    is io.github.rygel.outerstellar.platform.security.SessionLookup.Active ->
                        next(req.with(SecurityRules.USER_KEY of result.user))
                    is io.github.rygel.outerstellar.platform.security.SessionLookup.Expired ->
                        Response(Status.UNAUTHORIZED)
                            .header("X-Session-Expired", "true")
                            .body("Session expired")
                    is io.github.rygel.outerstellar.platform.security.SessionLookup.NotFound -> {
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
    return bearerSecurity to bearerAdminSecurity
}

@Suppress("LongParameterList")
private fun buildApiRoutes(
    appLabel: String,
    securityService: SecurityService,
    bearerSecurity: org.http4k.security.Security,
    bearerAdminSecurity: org.http4k.security.Security,
    messageService: MessageService,
    contactService: io.github.rygel.outerstellar.platform.service.ContactService,
    analytics: io.github.rygel.outerstellar.platform.analytics.AnalyticsService,
    deviceTokenRepository: io.github.rygel.outerstellar.platform.security.DeviceTokenRepository?,
    notificationService: io.github.rygel.outerstellar.platform.service.NotificationService?,
): List<org.http4k.routing.RoutingHttpHandler> {
    val apiRoutes = contract {
        renderer = OpenApi3(ApiInfo("$appLabel Sync API", "v1.0"), Jackson)
        descriptionPath = "/api/openapi.json"
        routes += AuthApi(securityService).routes
        routes += emptyList<org.http4k.contract.ContractRoute>()
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

    val bearerAdminApiContract = contract {
        renderer = OpenApi3(ApiInfo("$appLabel Admin API", "v1.0"), Jackson)
        descriptionPath = "/api/v1/admin/api-openapi.json"
        security = bearerAdminSecurity
        routes += UserAdminApi(securityService).routes
    }

    return listOf(bearerAdminApiContract, apiRoutes, syncContract)
}

@Suppress("LongParameterList")
private fun buildUiRoutes(
    appLabel: String,
    excludedRoutes: Set<String>,
    messageService: MessageService,
    pageFactory: WebPageFactory,
    jteRenderer: org.http4k.template.TemplateRenderer,
    contactService: io.github.rygel.outerstellar.platform.service.ContactService,
    securityService: SecurityService,
    config: AppConfig,
    analytics: io.github.rygel.outerstellar.platform.analytics.AnalyticsService,
    notificationService: io.github.rygel.outerstellar.platform.service.NotificationService?,
    userRepository: UserRepository,
    plugin: PlatformPlugin?,
): org.http4k.routing.RoutingHttpHandler = contract {
    renderer = OpenApi3(ApiInfo("$appLabel UI", "v1.0"), Jackson)
    descriptionPath = "/ui/openapi.json"
    routes += HomeRoutes(messageService, pageFactory, jteRenderer).routes
    if ("/contacts" !in excludedRoutes) {
        routes += ContactsRoutes(pageFactory, jteRenderer, contactService).routes
    }
    routes +=
        AuthRoutes(pageFactory, jteRenderer, securityService, config.sessionCookieSecure, analytics)
            .routes
    routes +=
        OAuthRoutes(
            providers = mapOf("apple" to AppleOAuthProvider()),
            securityService = securityService,
            sessionCookieSecure = config.sessionCookieSecure,
        )
            .routes
    routes += ErrorRoutes(pageFactory, jteRenderer).routes
    routes += SearchRoutes(pageFactory, jteRenderer, emptyList()).routes
    routes += SettingsRoutes(pageFactory, jteRenderer).routes
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
                pageFactory,
            )
        routes += plugin.routes(pluginContext)
    }
    routes +=
        ("/logout" bindContract POST).to { request: org.http4k.core.Request ->
            Response(Status.FOUND)
                .header("location", request.webContext.url("/"))
                .header(
                    "Set-Cookie",
                    io.github.rygel.outerstellar.platform.web.SessionCookie.clear(
                        config.sessionCookieSecure
                    ),
                )
        }
}

private fun buildComponentRoutes(
    appLabel: String,
    pageFactory: WebPageFactory,
    jteRenderer: TemplateRenderer,
): RoutingHttpHandler = contract {
    renderer = OpenApi3(ApiInfo("$appLabel Components", "v1.0"), Jackson)
    descriptionPath = "/components/openapi.json"
    routes += ComponentRoutes(pageFactory, jteRenderer).routes
}

@Suppress("LongParameterList")
private fun buildAdminRoutes(
    appLabel: String,
    outboxRepository: io.github.rygel.outerstellar.platform.persistence.OutboxRepository,
    cache: io.github.rygel.outerstellar.platform.persistence.MessageCache,
    pageFactory: WebPageFactory,
    jteRenderer: org.http4k.template.TemplateRenderer,
    config: AppConfig,
    securityService: SecurityService,
): org.http4k.routing.RoutingHttpHandler = contract {
    renderer = OpenApi3(ApiInfo("$appLabel Admin", "v1.0"), Jackson)
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

@Suppress("LongParameterList")
private fun buildBaseApp(
    excludedRoutes: Set<String>,
    adminContract: org.http4k.routing.RoutingHttpHandler,
    apiRoutes: List<org.http4k.routing.RoutingHttpHandler>,
    uiRoutes: org.http4k.routing.RoutingHttpHandler,
    componentRoutes: org.http4k.routing.RoutingHttpHandler,
    userRepository: UserRepository,
): HttpHandler {
    val filteredAdminHandler =
        Filter { next -> SecurityRules.authenticated(SecurityRules.hasRole(UserRole.ADMIN, next)) }
            .then(adminContract)

    val metricsHandler =
        Filter { next -> SecurityRules.authenticated(SecurityRules.hasRole(UserRole.ADMIN, next)) }
            .then {
                Response(Status.OK)
                    .body(io.github.rygel.outerstellar.platform.web.Metrics.registry.scrape())
            }

    val coreRoutes =
        mutableListOf(static(ResourceLoader.Classpath("static")), uiRoutes, componentRoutes)
    coreRoutes.addAll(apiRoutes)
    if ("/" !in excludedRoutes) {
        coreRoutes += "/" bind filteredAdminHandler
    }
    coreRoutes += "/health" bind GET to { buildHealthResponse(userRepository) }
    coreRoutes += "/metrics" bind GET to metricsHandler

    return routes(coreRoutes)
}

@Suppress("TooGenericExceptionCaught", "SwallowedException")
private fun buildHealthResponse(userRepository: UserRepository): Response {
    val checks = mutableMapOf<String, Any>("status" to "UP")
    try {
        val userCount = userRepository.findAll().size
        checks["database"] = mapOf("status" to "UP", "users" to userCount)
    } catch (e: Exception) {
        checks["status"] = "DOWN"
        checks["database"] = mapOf("status" to "DOWN", "error" to (e.message ?: "unknown"))
    }
    checks["timestamp"] = java.time.Instant.now().toString()
    val status = if (checks["status"] == "UP") Status.OK else Status.SERVICE_UNAVAILABLE
    return Response(status)
        .header("content-type", "application/json; charset=utf-8")
        .body(Jackson.asJsonObject(checks).toString())
}

@Suppress("LongParameterList")
private fun buildFilterChain(
    config: AppConfig,
    userRepository: UserRepository,
    analytics: io.github.rygel.outerstellar.platform.analytics.AnalyticsService,
    jwtService: io.github.rygel.outerstellar.platform.security.JwtService?,
    plugin: PlatformPlugin?,
    activityUpdater: io.github.rygel.outerstellar.platform.security.AsyncActivityUpdater?,
    pageFactory: WebPageFactory,
    jteRenderer: org.http4k.template.TemplateRenderer,
): Filter =
    Filters.correlationId
        .then(Filters.cors(config.corsOrigins))
        .then(Filters.etagCaching)
        .then(Filters.staticCacheControl)
        .then(Filters.securityHeaders(config.cspPolicy))
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
                activityUpdater,
            )
        )
        .then(Filters.securityFilter)
        .then(Filters.requestLogging)
        .then(Filters.serverMetrics)
        .then(Filters.globalErrorHandler(pageFactory, jteRenderer))
