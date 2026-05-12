package io.github.rygel.outerstellar.platform

import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.analytics.NoOpAnalyticsService
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.persistence.MessageCache
import io.github.rygel.outerstellar.platform.persistence.OutboxRepository
import io.github.rygel.outerstellar.platform.security.ApiKeyRealm
import io.github.rygel.outerstellar.platform.security.AppleOAuthProvider
import io.github.rygel.outerstellar.platform.security.AuthRealm
import io.github.rygel.outerstellar.platform.security.AuthResult
import io.github.rygel.outerstellar.platform.security.SecurityRules
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.SessionRealm
import io.github.rygel.outerstellar.platform.security.UserRepository
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.web.AdminNavItem
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
import io.github.rygel.outerstellar.platform.web.Page
import io.github.rygel.outerstellar.platform.web.PlatformPlugin
import io.github.rygel.outerstellar.platform.web.PluginAdminDashboardPage
import io.github.rygel.outerstellar.platform.web.PluginContext
import io.github.rygel.outerstellar.platform.web.PluginOptions
import io.github.rygel.outerstellar.platform.web.SearchRoutes
import io.github.rygel.outerstellar.platform.web.SettingsRoutes
import io.github.rygel.outerstellar.platform.web.SyncApi
import io.github.rygel.outerstellar.platform.web.SyncWebSocket
import io.github.rygel.outerstellar.platform.web.UserAdminApi
import io.github.rygel.outerstellar.platform.web.UserAdminRoutes
import io.github.rygel.outerstellar.platform.web.WebPageFactory
import io.github.rygel.outerstellar.platform.web.analyticsPageViewFilter
import io.github.rygel.outerstellar.platform.web.etagCachingFilter
import io.github.rygel.outerstellar.platform.web.rateLimitFilter
import io.github.rygel.outerstellar.platform.web.staticCacheControlFilter
import io.github.rygel.outerstellar.platform.web.webContext
import org.http4k.contract.bindContract
import org.http4k.contract.contract
import org.http4k.contract.openapi.ApiInfo
import org.http4k.contract.openapi.v3.OpenApi3
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.PolyHandler
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.cookie.cookie
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.format.KotlinxSerialization
import org.http4k.routing.ResourceLoader
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.routing.websocket.bind as wsBind
import org.http4k.routing.websockets
import org.http4k.template.TemplateRenderer
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("io.github.rygel.outerstellar.platform.App")

private class OptionalServices(
    val messageService: MessageService?,
    val contactService: io.github.rygel.outerstellar.platform.service.ContactService?,
    val outboxRepository: OutboxRepository?,
    val cache: MessageCache?,
    val notificationService: io.github.rygel.outerstellar.platform.service.NotificationService?,
    val jwtService: io.github.rygel.outerstellar.platform.security.JwtService?,
    val deviceTokenRepository: io.github.rygel.outerstellar.platform.security.DeviceTokenRepository?,
    val syncWebSocket: SyncWebSocket?,
    val plugin: PlatformPlugin?,
)

private class AppContext(
    val config: AppConfig,
    val securityService: SecurityService,
    val userRepository: UserRepository,
    val jteRenderer: TemplateRenderer,
    val pageFactory: WebPageFactory,
    val analytics: AnalyticsService,
    val services: OptionalServices,
) {
    val messageService
        get() = services.messageService

    val contactService
        get() = services.contactService

    val outboxRepository
        get() = services.outboxRepository

    val cache
        get() = services.cache

    val notificationService
        get() = services.notificationService

    val jwtService
        get() = services.jwtService

    val deviceTokenRepository
        get() = services.deviceTokenRepository

    val syncWebSocket
        get() = services.syncWebSocket

    val plugin
        get() = services.plugin

    val appLabel: String
        get() = plugin?.appLabel ?: "Outerstellar"

    val excludedRoutes: Set<String>
        get() = plugin?.excludeDefaultRoutes ?: emptySet()

    fun pluginContext(): PluginContext =
        PluginContext(jteRenderer, config, securityService, userRepository, analytics, notificationService, pageFactory)
}

@Suppress("LongParameterList", "DEPRECATION")
fun app(
    messageService: MessageService? = null,
    contactService: io.github.rygel.outerstellar.platform.service.ContactService? = null,
    outboxRepository: OutboxRepository? = null,
    cache: MessageCache? = null,
    jteRenderer: TemplateRenderer,
    pageFactory: WebPageFactory,
    config: AppConfig,
    securityService: SecurityService,
    userRepository: UserRepository,
    deviceTokenRepository: io.github.rygel.outerstellar.platform.security.DeviceTokenRepository? = null,
    analytics: AnalyticsService = NoOpAnalyticsService(),
    notificationService: io.github.rygel.outerstellar.platform.service.NotificationService? = null,
    jwtService: io.github.rygel.outerstellar.platform.security.JwtService? = null,
    plugin: PlatformPlugin? = null,
    @Suppress("UNUSED_PARAMETER")
    activityUpdater: io.github.rygel.outerstellar.platform.security.AsyncActivityUpdater? = null,
    syncWebSocket: SyncWebSocket? = null,
): PolyHandler {
    logger.info("Initializing Outerstellar application")
    val ctx =
        AppContext(
            config = config,
            securityService = securityService,
            userRepository = userRepository,
            jteRenderer = jteRenderer,
            pageFactory = pageFactory,
            analytics = analytics,
            services =
                OptionalServices(
                    messageService = messageService,
                    contactService = contactService,
                    outboxRepository = outboxRepository,
                    cache = cache,
                    notificationService = notificationService,
                    jwtService = jwtService,
                    deviceTokenRepository = deviceTokenRepository,
                    syncWebSocket = syncWebSocket,
                    plugin = plugin,
                ),
        )
    val httpHandler = assembleHttpHandler(ctx)
    val wsHandler = ctx.syncWebSocket?.let { websockets("/ws/sync" wsBind it.handler) }
    return PolyHandler(httpHandler, wsHandler)
}

private fun assembleHttpHandler(ctx: AppContext): HttpHandler {
    val realms: List<AuthRealm> = listOf(SessionRealm(ctx.securityService), ApiKeyRealm(ctx.securityService))
    val (bearerSecurity, bearerAdminSecurity) = buildBearerSecurityPair(realms)
    val apiRoutes = buildApiRoutes(ctx, bearerSecurity, bearerAdminSecurity)
    val uiRoutes = buildUiRoutes(ctx)
    val adminRoutes = buildAdminRoutes(ctx)
    val componentRoutes = buildComponentRoutes(ctx)
    val baseApp = buildBaseApp(ctx, adminRoutes, apiRoutes, uiRoutes, componentRoutes)
    return buildFilterChain(ctx).then(baseApp)
}

private fun buildBearerSecurityPair(
    realms: List<AuthRealm>
): Pair<org.http4k.security.Security, org.http4k.security.Security> {
    val bearerAuthFilter = Filter { next ->
        { req ->
            val token = req.header("Authorization")?.removePrefix("Bearer ")
            if (token == null) {
                Response(Status.UNAUTHORIZED).body("API token required")
            } else {
                var finalResult: AuthResult = AuthResult.Skipped
                for (realm in realms) {
                    val result = realm.authenticate(token)
                    if (result !is AuthResult.Skipped) {
                        finalResult = result
                        break
                    }
                }
                when (finalResult) {
                    is AuthResult.Authenticated -> next(req.with(SecurityRules.USER_KEY of finalResult.user))
                    is AuthResult.Expired ->
                        Response(Status.UNAUTHORIZED).header("X-Session-Expired", "true").body("Session expired")
                    is AuthResult.Skipped -> Response(Status.UNAUTHORIZED).body("API token required")
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
                bearerAuthFilter.then(Filter { inner -> SecurityRules.hasRole(UserRole.ADMIN, inner) })(next)
            }
        }
    return bearerSecurity to bearerAdminSecurity
}

private fun buildApiRoutes(
    ctx: AppContext,
    bearerSecurity: org.http4k.security.Security,
    bearerAdminSecurity: org.http4k.security.Security,
): List<org.http4k.routing.RoutingHttpHandler> {
    val securityService = ctx.securityService
    val messageService = ctx.messageService
    val contactService = ctx.contactService
    val analytics = ctx.analytics
    val deviceTokenRepository = ctx.deviceTokenRepository
    val notificationService = ctx.notificationService
    val appLabel = ctx.appLabel

    val apiRoutes = contract {
        renderer = OpenApi3(ApiInfo("$appLabel API", "v1.0"), KotlinxSerialization)
        descriptionPath = "/api/openapi.json"
        routes += AuthApi(securityService).routes
    }

    val syncContract = contract {
        renderer = OpenApi3(ApiInfo("Sync", "v1.0"), KotlinxSerialization)
        descriptionPath = "/api/v1/sync/openapi.json"
        security = bearerSecurity
        if (messageService != null || contactService != null) {
            routes += SyncApi(messageService, contactService, analytics).routes
        }
        routes += AuthApi(securityService).bearerRoutes
        if (deviceTokenRepository != null) {
            routes += DeviceRegistrationApi(deviceTokenRepository).routes
        }
        if (notificationService != null) {
            routes += NotificationApi(notificationService).routes
        }
    }

    val bearerAdminApiContract = contract {
        renderer = OpenApi3(ApiInfo("$appLabel Admin API", "v1.0"), KotlinxSerialization)
        descriptionPath = "/api/v1/admin/api-openapi.json"
        security = bearerAdminSecurity
        routes += UserAdminApi(securityService).routes
    }

    return listOf(bearerAdminApiContract, apiRoutes, syncContract)
}

private fun buildUiRoutes(ctx: AppContext): org.http4k.routing.RoutingHttpHandler {
    val messageService = ctx.messageService
    val contactService = ctx.contactService
    val notificationService = ctx.notificationService
    val plugin = ctx.plugin
    val excludedRoutes = ctx.excludedRoutes
    val securityService = ctx.securityService
    val sessionCookieSecure = ctx.config.sessionCookieSecure
    val analytics = ctx.analytics
    val pageFactory = ctx.pageFactory
    val jteRenderer = ctx.jteRenderer
    val appLabel = ctx.appLabel

    return contract {
        renderer = OpenApi3(ApiInfo("$appLabel UI", "v1.0"), KotlinxSerialization)
        descriptionPath = "/ui/openapi.json"
        if (messageService != null && "/" !in excludedRoutes) {
            routes += HomeRoutes(messageService, pageFactory, jteRenderer).routes
        }
        if (contactService != null && "/contacts" !in excludedRoutes) {
            routes += ContactsRoutes(pageFactory, jteRenderer, contactService).routes
        }
        routes += AuthRoutes(pageFactory, jteRenderer, securityService, sessionCookieSecure, analytics).routes
        routes +=
            OAuthRoutes(
                    providers = mapOf("apple" to AppleOAuthProvider()),
                    securityService = securityService,
                    sessionCookieSecure = sessionCookieSecure,
                )
                .routes
        routes += ErrorRoutes(pageFactory, jteRenderer).routes
        routes += SearchRoutes(pageFactory, jteRenderer, emptyList()).routes
        routes += SettingsRoutes(pageFactory, jteRenderer).routes
        if (notificationService != null) {
            routes += NotificationRoutes(pageFactory, jteRenderer, notificationService).routes
        }
        if (plugin != null) {
            routes += plugin.routes(ctx.pluginContext())
        }
        routes +=
            ("/logout" bindContract POST).to { request: org.http4k.core.Request ->
                val rawToken =
                    request.cookie(io.github.rygel.outerstellar.platform.web.WebContext.SESSION_COOKIE)?.value
                if (rawToken != null) {
                    securityService.deleteSession(rawToken)
                }
                Response(Status.FOUND)
                    .header("location", request.webContext.url("/"))
                    .header(
                        "Set-Cookie",
                        io.github.rygel.outerstellar.platform.web.SessionCookie.clear(sessionCookieSecure),
                    )
            }
    }
}

private fun buildComponentRoutes(ctx: AppContext): RoutingHttpHandler {
    val pageFactory = ctx.pageFactory
    val jteRenderer = ctx.jteRenderer
    val appLabel = ctx.appLabel
    return contract {
        renderer = OpenApi3(ApiInfo("$appLabel Components", "v1.0"), KotlinxSerialization)
        descriptionPath = "/components/openapi.json"
        routes += ComponentRoutes(pageFactory, jteRenderer).routes
    }
}

private fun buildAdminRoutes(ctx: AppContext): org.http4k.routing.RoutingHttpHandler {
    val outboxRepository = ctx.outboxRepository
    val cache = ctx.cache
    val pageFactory = ctx.pageFactory
    val jteRenderer = ctx.jteRenderer
    val devDashboardEnabled = ctx.config.devDashboardEnabled
    val securityService = ctx.securityService
    val appLabel = ctx.appLabel
    val adminSecurity =
        object : org.http4k.security.Security {
            override val filter = Filter { next ->
                SecurityRules.authenticated(SecurityRules.hasRole(UserRole.ADMIN, next))
            }
        }
    val plugin = ctx.plugin
    val pluginSections =
        if (plugin != null) {
            plugin.adminSections(ctx.pluginContext())
        } else {
            emptyList()
        }
    val adminContract = contract {
        renderer = OpenApi3(ApiInfo("$appLabel Admin", "v1.0"), KotlinxSerialization)
        descriptionPath = "/admin/openapi.json"
        security = adminSecurity
        if (outboxRepository != null && cache != null) {
            routes += DevDashboardRoutes(outboxRepository, cache, pageFactory, jteRenderer, devDashboardEnabled).routes
        }
        routes += UserAdminRoutes(pageFactory, jteRenderer, securityService).routes
        pluginSections.forEach { section -> routes += section.route }
    }
    return if (pluginSections.isNotEmpty()) {
        val adminAuthFilter = Filter { next ->
            SecurityRules.authenticated(SecurityRules.hasRole(UserRole.ADMIN, next))
        }
        val pluginDashboardRoute =
            adminAuthFilter.then(
                "/admin/plugins" bind
                    GET to
                    { req ->
                        val webCtx =
                            io.github.rygel.outerstellar.platform.web.WebContext(
                                request = req,
                                pluginOptions =
                                    PluginOptions(
                                        adminNavItems =
                                            pluginSections.map {
                                                AdminNavItem(it.navLabel, it.summaryCard.linkUrl, it.navIcon)
                                            }
                                    ),
                            )
                        val shell = webCtx.shell("Plugin Dashboard", "/admin/plugins")
                        val page = Page(shell, PluginAdminDashboardPage(pluginSections.map { it.summaryCard }))
                        Response(Status.OK).body(ctx.jteRenderer(page) ?: "")
                    }
            )
        routes(adminContract, pluginDashboardRoute)
    } else {
        adminContract
    }
}

private val localhostOnly = Filter { next ->
    { request ->
        val source = request.source
        if (source == null) {
            next(request)
        } else {
            val host = source.toString().substringBefore(":").substringAfter("/")
            if (
                host == "127.0.0.1" ||
                    host == "::1" ||
                    host == "localhost" ||
                    host.startsWith("10.") ||
                    host.startsWith("172.16.") ||
                    host.startsWith("172.17.") ||
                    host.startsWith("172.18.") ||
                    host.startsWith("172.19.") ||
                    host.startsWith("172.20.") ||
                    host.startsWith("172.21.") ||
                    host.startsWith("172.22.") ||
                    host.startsWith("172.23.") ||
                    host.startsWith("172.24.") ||
                    host.startsWith("172.25.") ||
                    host.startsWith("172.26.") ||
                    host.startsWith("172.27.") ||
                    host.startsWith("172.28.") ||
                    host.startsWith("172.29.") ||
                    host.startsWith("172.30.") ||
                    host.startsWith("172.31.") ||
                    host.startsWith("192.168.")
            ) {
                next(request)
            } else {
                Response(Status.FORBIDDEN)
            }
        }
    }
}

private fun buildBaseApp(
    ctx: AppContext,
    adminContract: org.http4k.routing.RoutingHttpHandler,
    apiRoutes: List<org.http4k.routing.RoutingHttpHandler>,
    uiRoutes: org.http4k.routing.RoutingHttpHandler,
    componentRoutes: org.http4k.routing.RoutingHttpHandler,
): HttpHandler {
    val filteredAdminHandler =
        Filter { next -> SecurityRules.authenticated(SecurityRules.hasRole(UserRole.ADMIN, next)) }.then(adminContract)

    val metricsHandler =
        Filter { next -> SecurityRules.authenticated(SecurityRules.hasRole(UserRole.ADMIN, next)) }
            .then { Response(Status.OK).body(io.github.rygel.outerstellar.platform.web.Metrics.registry.scrape()) }

    val coreRoutes = mutableListOf(static(ResourceLoader.Classpath("static")), uiRoutes, componentRoutes)
    coreRoutes.addAll(apiRoutes)
    if ("/" !in ctx.excludedRoutes) {
        coreRoutes += "/" bind filteredAdminHandler
    }
    coreRoutes += "/health" bind GET to localhostOnly.then { buildHealthResponse(ctx.userRepository) }
    coreRoutes += "/metrics" bind GET to metricsHandler
    coreRoutes += "/robots.txt" bind GET to { buildRobotsTxtResponse() }

    return routes(coreRoutes)
}

private fun buildRobotsTxtResponse(): Response =
    Response(Status.OK)
        .header("content-type", "text/plain; charset=utf-8")
        .body(
            """
            User-agent: *
            Allow: /
            Allow: /contacts
            Allow: /search
            Disallow: /api/
            Disallow: /admin/
            Disallow: /ws/
            Disallow: /auth/
            Disallow: /errors/
            Disallow: /components/
            Disallow: /messages/
            Disallow: /notifications/
            Disallow: /settings/

            Sitemap: /sitemap.xml
            """
                .trimIndent() + "\n"
        )

@Suppress("TooGenericExceptionCaught", "SwallowedException")
private fun buildHealthResponse(userRepository: UserRepository): Response {
    val checks = mutableMapOf<String, Any>("status" to "UP")
    try {
        userRepository.countAll()
        checks["database"] = mapOf("status" to "UP")
    } catch (e: Exception) {
        checks["status"] = "DOWN"
        checks["database"] = mapOf("status" to "DOWN", "error" to "Database connection failed")
    }
    checks["timestamp"] = java.time.Instant.now().toString()
    val status = if (checks["status"] == "UP") Status.OK else Status.SERVICE_UNAVAILABLE
    return Response(status)
        .header("content-type", "application/json; charset=utf-8")
        .body(KotlinxSerialization.asJsonObject(checks).toString())
}

private fun buildFilterChain(ctx: AppContext): Filter {
    val config = ctx.config
    val userRepository = ctx.userRepository
    val securityService = ctx.securityService
    val jwtService = ctx.jwtService
    val plugin = ctx.plugin
    val pageFactory = ctx.pageFactory
    val jteRenderer = ctx.jteRenderer
    val analytics = ctx.analytics
    val adminNavItems =
        if (plugin != null) {
            plugin.adminSections(ctx.pluginContext()).map {
                AdminNavItem(it.navLabel, it.summaryCard.linkUrl, it.navIcon)
            }
        } else {
            emptyList()
        }
    var chain =
        Filters.correlationId
            .then(Filters.cors(config.corsOrigins))
            .then(etagCachingFilter)
            .then(staticCacheControlFilter)
            .then(Filters.securityHeaders(config.cspPolicy))
            .then(Filters.telemetry)
            .then(rateLimitFilter())
            .then(Filters.csrfProtection(config.sessionCookieSecure, config.csrfEnabled))
            .then(Filters.devAutoLogin(config.devMode, userRepository, securityService, config.sessionCookieSecure))
            .then(
                Filters.stateFilter(
                    config.devDashboardEnabled,
                    userRepository,
                    config.version,
                    jwtService,
                    securityService,
                    PluginOptions(
                        navItems = plugin?.navItems ?: emptyList(),
                        textResolver = plugin?.textResolver,
                        adminNavItems = adminNavItems,
                    ),
                    cookieSecure = config.sessionCookieSecure,
                    appBaseUrl = config.appBaseUrl,
                )
            )

    if (plugin != null) {
        for (filter in plugin.filters(ctx.pluginContext())) {
            chain = chain.then(filter)
        }
    }

    return chain
        .then(analyticsPageViewFilter(analytics))
        .then(Filters.sessionTimeout(config.sessionCookieSecure))
        .then(Filters.securityFilter)
        .then(Filters.requestLogging)
        .then(Filters.serverMetrics)
        .then(Filters.globalErrorHandler(pageFactory, jteRenderer))
}
