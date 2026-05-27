package io.github.rygel.outerstellar.platform

import io.github.rygel.outerstellar.platform.di.CoreComponents
import io.github.rygel.outerstellar.platform.di.PersistenceComponents
import io.github.rygel.outerstellar.platform.di.WebComponents
import io.github.rygel.outerstellar.platform.export.ContactExportProvider
import io.github.rygel.outerstellar.platform.export.MessageExportProvider
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.search.ContactSearchProvider
import io.github.rygel.outerstellar.platform.search.MessageSearchProvider
import io.github.rygel.outerstellar.platform.security.ApiKeyRealm
import io.github.rygel.outerstellar.platform.security.AppleOAuthProvider
import io.github.rygel.outerstellar.platform.security.AuthRealm
import io.github.rygel.outerstellar.platform.security.AuthResult
import io.github.rygel.outerstellar.platform.security.OAuthProvider
import io.github.rygel.outerstellar.platform.security.SecurityComponents
import io.github.rygel.outerstellar.platform.security.SecurityRules
import io.github.rygel.outerstellar.platform.security.SessionRealm
import io.github.rygel.outerstellar.platform.web.AdminNavItem
import io.github.rygel.outerstellar.platform.web.ApiKeyRoutes
import io.github.rygel.outerstellar.platform.web.AuthApi
import io.github.rygel.outerstellar.platform.web.AuthRoutes
import io.github.rygel.outerstellar.platform.web.ComponentRoutes
import io.github.rygel.outerstellar.platform.web.ContactsRoutes
import io.github.rygel.outerstellar.platform.web.DevDashboardRoutes
import io.github.rygel.outerstellar.platform.web.DeviceRegistrationApi
import io.github.rygel.outerstellar.platform.web.ErrorRoutes
import io.github.rygel.outerstellar.platform.web.ExportRoutes
import io.github.rygel.outerstellar.platform.web.Filters
import io.github.rygel.outerstellar.platform.web.HomeRoutes
import io.github.rygel.outerstellar.platform.web.Metrics
import io.github.rygel.outerstellar.platform.web.NotificationApi
import io.github.rygel.outerstellar.platform.web.NotificationRoutes
import io.github.rygel.outerstellar.platform.web.OAuthRoutes
import io.github.rygel.outerstellar.platform.web.Page
import io.github.rygel.outerstellar.platform.web.PasswordRoutes
import io.github.rygel.outerstellar.platform.web.PlatformPlugin
import io.github.rygel.outerstellar.platform.web.PluginAdminDashboardPage
import io.github.rygel.outerstellar.platform.web.PluginContext
import io.github.rygel.outerstellar.platform.web.PluginOptions
import io.github.rygel.outerstellar.platform.web.PollApi
import io.github.rygel.outerstellar.platform.web.ProfileRoutes
import io.github.rygel.outerstellar.platform.web.RequestContext
import io.github.rygel.outerstellar.platform.web.SearchRoutes
import io.github.rygel.outerstellar.platform.web.SessionCookie
import io.github.rygel.outerstellar.platform.web.SettingsRoutes
import io.github.rygel.outerstellar.platform.web.ShellConfig
import io.github.rygel.outerstellar.platform.web.ShellRenderer
import io.github.rygel.outerstellar.platform.web.SyncApi
import io.github.rygel.outerstellar.platform.web.TOTPApiRoutes
import io.github.rygel.outerstellar.platform.web.TOTPRoutes
import io.github.rygel.outerstellar.platform.web.UserAdminApi
import io.github.rygel.outerstellar.platform.web.UserAdminRoutes
import io.github.rygel.outerstellar.platform.web.VoteApi
import io.github.rygel.outerstellar.platform.web.analyticsPageViewFilter
import io.github.rygel.outerstellar.platform.web.etagCachingFilter
import io.github.rygel.outerstellar.platform.web.rateLimitFilter
import io.github.rygel.outerstellar.platform.web.shellRenderer
import io.github.rygel.outerstellar.platform.web.staticCacheControlFilter
import java.time.Instant
import java.time.LocalDate
import org.http4k.contract.bindContract
import org.http4k.contract.contract
import org.http4k.contract.openapi.ApiInfo
import org.http4k.contract.openapi.v3.OpenApi3
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.PolyHandler
import org.http4k.core.Request
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
import org.http4k.security.Security
import org.http4k.template.TemplateRenderer
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("io.github.rygel.outerstellar.platform.App")

fun app(
    config: AppConfig,
    persistence: PersistenceComponents,
    security: SecurityComponents,
    core: CoreComponents,
    web: WebComponents,
    plugin: PlatformPlugin? = null,
): PolyHandler {
    logger.info("Initializing Outerstellar application")
    val httpHandler = assembleHttpHandler(config, persistence, security, core, web, plugin)
    val wsHandler = web.syncWebSocket.let { websockets("/ws/sync" wsBind it.handler) }
    return PolyHandler(httpHandler, wsHandler)
}

private fun assembleHttpHandler(
    config: AppConfig,
    persistence: PersistenceComponents,
    security: SecurityComponents,
    core: CoreComponents,
    web: WebComponents,
    plugin: PlatformPlugin?,
): HttpHandler {
    val sec = security
    val realms = listOf(SessionRealm(sec.sessionService), ApiKeyRealm(sec.apiKeyService))
    val (bearerSecurity, bearerAdminSecurity) = buildBearerSecurityPair(realms)
    val apiRoutes =
        buildApiRoutes(config, persistence, security, core, web, plugin, bearerSecurity, bearerAdminSecurity)
    val uiRouteSet = buildUiRoutes(config, persistence, security, core, web, plugin)
    val componentRouteSet = buildComponentRoutes(web, plugin)
    val adminRoutes = buildAdminRoutes(config, persistence, security, web, plugin)
    val baseApp =
        buildBaseApp(config, persistence, security, web, plugin, adminRoutes, apiRoutes, uiRouteSet, componentRouteSet)
    return buildFilterChain(config, persistence, security, web, plugin).then(baseApp)
}

private fun buildBearerSecurityPair(realms: List<AuthRealm>): Pair<Security, Security> {
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
                    is AuthResult.Skipped,
                    is AuthResult.TotpRequired -> Response(Status.UNAUTHORIZED).body("API token required")
                }
            }
        }
    }
    val bearerSecurity =
        object : Security {
            override val filter = bearerAuthFilter
        }
    val bearerAdminSecurity =
        object : Security {
            override val filter = Filter { next ->
                bearerAuthFilter.then(Filter { inner -> SecurityRules.hasRole(UserRole.ADMIN, inner) })(next)
            }
        }
    return bearerSecurity to bearerAdminSecurity
}

private fun buildApiRoutes(
    config: AppConfig,
    persistence: PersistenceComponents,
    security: SecurityComponents,
    core: CoreComponents,
    web: WebComponents,
    plugin: PlatformPlugin?,
    bearerSecurity: Security,
    bearerAdminSecurity: Security,
): List<RoutingHttpHandler> {
    val sec = security
    val appLabel = plugin?.appLabel ?: "Outerstellar"

    val apiRoutes = contract {
        renderer = OpenApi3(ApiInfo("$appLabel API", "v1.0"), KotlinxSerialization)
        descriptionPath = "/api/openapi.json"
        routes +=
            AuthApi(
                    sec.apiKeyService,
                    sec.passwordResetService,
                    sec.authService,
                    sec.accountService,
                    sec.sessionService,
                    config,
                )
                .routes
        routes += VoteApi(web.voteService).routes
        routes += PollApi(web.pollService).routes
    }

    val syncContract = contract {
        renderer = OpenApi3(ApiInfo("Sync", "v1.0"), KotlinxSerialization)
        descriptionPath = "/api/v1/sync/openapi.json"
        this.security = bearerSecurity
        routes += SyncApi(core.messageService, core.contactService, web.analyticsService).routes
        routes +=
            AuthApi(
                    sec.apiKeyService,
                    sec.passwordResetService,
                    sec.authService,
                    sec.accountService,
                    sec.sessionService,
                    config,
                )
                .bearerRoutes
        routes += DeviceRegistrationApi(persistence.deviceTokenRepository).routes
        routes += NotificationApi(web.notificationService).routes
    }

    val bearerAdminApiContract = contract {
        renderer = OpenApi3(ApiInfo("$appLabel Admin API", "v1.0"), KotlinxSerialization)
        descriptionPath = "/api/v1/admin/api-openapi.json"
        this.security = bearerAdminSecurity
        routes += UserAdminApi(sec.userAdminService).routes
        val exportProviders =
            listOfNotNull(MessageExportProvider(core.messageService), ContactExportProvider(core.contactService))
        if (exportProviders.isNotEmpty()) {
            routes += ExportRoutes(exportProviders).routes
        }
    }

    return listOf(bearerAdminApiContract, apiRoutes, syncContract)
}

private data class UiRouteSet(val publicRoutes: RoutingHttpHandler, val protectedRoutes: RoutingHttpHandler)

private data class ComponentRouteSet(val publicRoutes: RoutingHttpHandler, val protectedRoutes: RoutingHttpHandler)

@Suppress("LongMethod")
private fun buildUiRoutes(
    config: AppConfig,
    persistence: PersistenceComponents,
    security: SecurityComponents,
    core: CoreComponents,
    web: WebComponents,
    plugin: PlatformPlugin?,
): UiRouteSet {
    val sec = security
    val sessionCookieSecure = config.sessionCookieSecure
    val pageFactory = web.pageFactory
    val jteRenderer = web.templateRenderer
    val appLabel = plugin?.appLabel ?: "Outerstellar"
    val pluginCtx = plugin?.let { buildPluginContext(jteRenderer, config, persistence, security, web) }
    val passwordRoutes = PasswordRoutes(pageFactory, jteRenderer, sec.accountService, sec.passwordResetService)
    val homeRoutes = HomeRoutes(core.messageService, pageFactory, jteRenderer)
    val contactsRoutes = ContactsRoutes(pageFactory, jteRenderer, core.contactService)
    val notificationRoutes = NotificationRoutes(pageFactory, jteRenderer, web.notificationService)

    val publicContract = contract {
        renderer = OpenApi3(ApiInfo("$appLabel UI", "v1.0"), KotlinxSerialization)
        descriptionPath = "/ui/openapi.json"
        routes +=
            AuthRoutes(
                    pageFactory,
                    jteRenderer,
                    sec.authService,
                    sec.sessionService,
                    sec.passwordResetService,
                    web.analyticsService,
                    config,
                )
                .routes
        routes += passwordRoutes.publicRoutes
        val oauthProviders = mutableMapOf<String, OAuthProvider>()
        val appleConfig = config.appleOAuth
        if (appleConfig.enabled && appleConfig.clientId.isNotBlank()) {
            oauthProviders["apple"] =
                AppleOAuthProvider(
                    teamId = appleConfig.teamId,
                    clientId = appleConfig.clientId,
                    keyId = appleConfig.keyId,
                    privateKeyPem = appleConfig.privateKeyPem,
                )
        }
        routes +=
            OAuthRoutes(
                    providers = oauthProviders,
                    oauthService = sec.oauthService,
                    sessionService = sec.sessionService,
                    sessionCookieSecure = sessionCookieSecure,
                    appBaseUrl = config.appBaseUrl,
                )
                .routes
        routes += ErrorRoutes(pageFactory, jteRenderer).routes
        val searchProviders =
            listOfNotNull(MessageSearchProvider(core.messageService), ContactSearchProvider(core.contactService))
        routes += SearchRoutes(pageFactory, jteRenderer, searchProviders).routes
        routes += homeRoutes.publicRoutes
        routes += notificationRoutes.publicRoutes
    }

    val protectedContract = contract {
        renderer = OpenApi3(ApiInfo("$appLabel Protected UI", "v1.0"), KotlinxSerialization)
        descriptionPath = "/ui-protected/openapi.json"
        routes += homeRoutes.protectedRoutes
        routes += contactsRoutes.routes
        routes += passwordRoutes.protectedRoutes
        routes += ProfileRoutes(pageFactory, jteRenderer, sec.accountService, sessionCookieSecure).routes
        routes += ApiKeyRoutes(pageFactory, jteRenderer, sec.apiKeyService).routes
        routes += SettingsRoutes(pageFactory, jteRenderer).routes
        routes += notificationRoutes.protectedRoutes
        if (plugin != null && pluginCtx != null) {
            routes += plugin.routeRegistrations(pluginCtx).map { it.route }
        }
        routes +=
            ("/logout" bindContract POST).to { request: Request ->
                val rawToken = request.cookie(RequestContext.SESSION_COOKIE)?.value
                if (rawToken != null) {
                    sec.sessionService.deleteSession(rawToken)
                }
                Response(Status.FOUND)
                    .header("location", request.shellRenderer.url("/"))
                    .header("Set-Cookie", SessionCookie.clear(sessionCookieSecure))
            }
    }

    return UiRouteSet(publicRoutes = publicContract, protectedRoutes = protectedContract)
}

private fun buildComponentRoutes(web: WebComponents, plugin: PlatformPlugin?): ComponentRouteSet {
    val appLabel = plugin?.appLabel ?: "Outerstellar"
    val componentRoutes = ComponentRoutes(web.pageFactory, web.templateRenderer, web.voteService, web.pollService)
    val publicContract = contract {
        renderer = OpenApi3(ApiInfo("$appLabel Components", "v1.0"), KotlinxSerialization)
        descriptionPath = "/components/openapi.json"
        routes += componentRoutes.publicRoutes
    }
    val protectedContract = contract {
        renderer = OpenApi3(ApiInfo("$appLabel Protected Components", "v1.0"), KotlinxSerialization)
        descriptionPath = "/components-protected/openapi.json"
        routes += componentRoutes.protectedRoutes
    }
    return ComponentRouteSet(publicRoutes = publicContract, protectedRoutes = protectedContract)
}

private fun buildAdminRoutes(
    config: AppConfig,
    persistence: PersistenceComponents,
    security: SecurityComponents,
    web: WebComponents,
    plugin: PlatformPlugin?,
): RoutingHttpHandler {
    val sec = security
    val pageFactory = web.pageFactory
    val jteRenderer = web.templateRenderer
    val devDashboardEnabled = config.devDashboardEnabled
    val appLabel = plugin?.appLabel ?: "Outerstellar"
    val adminSecurity =
        object : Security {
            override val filter = Filter { next ->
                SecurityRules.authenticated(SecurityRules.hasRole(UserRole.ADMIN, next))
            }
        }
    val pluginCtx = plugin?.let { buildPluginContext(jteRenderer, config, persistence, security, web) }
    val pluginSections =
        if (plugin != null && pluginCtx != null) {
            plugin.adminSections(pluginCtx)
        } else {
            emptyList()
        }
    val adminContract = contract {
        renderer = OpenApi3(ApiInfo("$appLabel Admin", "v1.0"), KotlinxSerialization)
        descriptionPath = "/admin/openapi.json"
        this.security = adminSecurity
        routes +=
            DevDashboardRoutes(
                    persistence.outboxRepository,
                    web.messageCache,
                    pageFactory,
                    jteRenderer,
                    devDashboardEnabled,
                )
                .routes
        routes += UserAdminRoutes(pageFactory, jteRenderer, sec.userAdminService).routes
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
                        val pluginRequestContext = RequestContext(req, sessionService = sec.sessionService)
                        val pluginShellRenderer =
                            ShellRenderer(
                                pluginRequestContext,
                                shellConfig =
                                    ShellConfig(
                                        pluginOptions =
                                            PluginOptions(
                                                adminNavItems =
                                                    pluginSections.map {
                                                        AdminNavItem(it.navLabel, it.summaryCard.linkUrl, it.navIcon)
                                                    }
                                            )
                                    ),
                            )
                        val shell = pluginShellRenderer.shell("Plugin Dashboard", "/admin/plugins")
                        val page = Page(shell, PluginAdminDashboardPage(pluginSections.map { it.summaryCard }))
                        Response(Status.OK).body(jteRenderer(page) ?: "")
                    }
            )
        routes(adminContract, pluginDashboardRoute)
    } else {
        adminContract
    }
}

private fun buildPluginContext(
    jteRenderer: TemplateRenderer,
    config: AppConfig,
    persistence: PersistenceComponents,
    security: SecurityComponents,
    web: WebComponents,
): PluginContext =
    PluginContext(
        jteRenderer,
        config,
        security.apiKeyService,
        security.oauthService,
        persistence.userRepository,
        web.analyticsService,
        web.notificationService,
        web.pageFactory,
    )

private val localhostOnly = Filter { next ->
    { request ->
        val host = request.header("Host")
        if (host == null || host.isLocalhostHost()) {
            next(request)
        } else {
            Response(Status.FORBIDDEN)
        }
    }
}

private fun String.isLocalhostHost(): Boolean =
    startsWith("localhost") || startsWith("127.0.0.1") || startsWith("[::1]")

private fun buildBaseApp(
    config: AppConfig,
    persistence: PersistenceComponents,
    security: SecurityComponents,
    web: WebComponents,
    plugin: PlatformPlugin?,
    adminContract: RoutingHttpHandler,
    apiRoutes: List<RoutingHttpHandler>,
    uiRouteSet: UiRouteSet,
    componentRouteSet: ComponentRouteSet,
): HttpHandler {
    val sec = security
    val userRepository = persistence.userRepository

    val authenticatedFilter = Filter { next -> SecurityRules.authenticated(next) }

    val filteredAdminHandler =
        Filter { next -> SecurityRules.authenticated(SecurityRules.hasRole(UserRole.ADMIN, next)) }.then(adminContract)

    val metricsHandler =
        Filter { next -> SecurityRules.authenticated(SecurityRules.hasRole(UserRole.ADMIN, next)) }
            .then { Response(Status.OK).body(Metrics.registry.scrape()) }

    val unfiltered =
        mutableListOf(
            static(ResourceLoader.Classpath("static")),
            "/health" bind GET to localhostOnly.then { buildHealthResponse(userRepository) },
            "/metrics" bind GET to metricsHandler,
            "/robots.txt" bind GET to { buildRobotsTxtResponse() },
            "/sitemap.xml" bind GET to { buildSitemapResponse(config.appBaseUrl) },
        )

    val appRoutes = mutableListOf<RoutingHttpHandler>()
    appRoutes += uiRouteSet.publicRoutes
    appRoutes += componentRouteSet.publicRoutes
    appRoutes += authenticatedFilter.then(uiRouteSet.protectedRoutes)
    appRoutes += authenticatedFilter.then(componentRouteSet.protectedRoutes)
    appRoutes +=
        TOTPRoutes(
                sec.authService,
                web.templateRenderer,
                config.sessionCookieSecure,
                sec.totpService,
                sec.sessionService,
            )
            .routes
    appRoutes += TOTPApiRoutes(sec.authService, sec.totpService, sec.sessionService).routes
    appRoutes.addAll(apiRoutes)
    appRoutes += "/" bind filteredAdminHandler

    return routes(unfiltered + appRoutes)
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

private fun buildSitemapResponse(appBaseUrl: String): Response {
    val base = appBaseUrl.ifBlank { "http://localhost:8080" }
    val today = LocalDate.now().toString()
    return Response(Status.OK)
        .header("content-type", "application/xml; charset=utf-8")
        .body(
            """<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
    <url>
        <loc>${base}/</loc>
        <lastmod>$today</lastmod>
        <changefreq>weekly</changefreq>
        <priority>1.0</priority>
    </url>
    <url>
        <loc>${base}/auth</loc>
        <lastmod>$today</lastmod>
        <changefreq>monthly</changefreq>
        <priority>0.5</priority>
    </url>
    <url>
        <loc>${base}/search</loc>
        <lastmod>$today</lastmod>
        <changefreq>weekly</changefreq>
        <priority>0.8</priority>
    </url>
</urlset>"""
                .trimIndent() + "\n"
        )
}

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
    checks["timestamp"] = Instant.now().toString()
    val status = if (checks["status"] == "UP") Status.OK else Status.SERVICE_UNAVAILABLE
    return Response(status)
        .header("content-type", "application/json; charset=utf-8")
        .body(KotlinxSerialization.asJsonObject(checks).toString())
}

private fun buildFilterChain(
    config: AppConfig,
    persistence: PersistenceComponents,
    security: SecurityComponents,
    web: WebComponents,
    plugin: PlatformPlugin?,
): Filter {
    val sec = security
    val userRepository = persistence.userRepository
    val jwtService = sec.jwtService
    val pageFactory = web.pageFactory
    val jteRenderer = web.templateRenderer
    val analytics = web.analyticsService
    val pluginCtx = plugin?.let { buildPluginContext(jteRenderer, config, persistence, security, web) }
    val adminNavItems =
        if (plugin != null && pluginCtx != null) {
            plugin.adminSections(pluginCtx).map { AdminNavItem(it.navLabel, it.summaryCard.linkUrl, it.navIcon) }
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
            .then(
                rateLimitFilter(
                    trustedProxies = config.trustedProxies.split(",").map { it.trim() }.filter { it.isNotBlank() }
                )
            )
            .then(Filters.csrfProtection(config.sessionCookieSecure, config.csrfEnabled))
            .then(Filters.devAutoLogin(config.devMode, userRepository, sec.sessionService, config.sessionCookieSecure))
            .then(
                Filters.stateFilter(
                    config.devDashboardEnabled,
                    userRepository,
                    config.version,
                    jwtService,
                    PluginOptions(
                        navItems = emptyList(),
                        textResolver = plugin?.textResolver,
                        adminNavItems = adminNavItems,
                    ),
                    cookieSecure = config.sessionCookieSecure,
                    appBaseUrl = config.appBaseUrl,
                    bannerProviders =
                        if (plugin != null && pluginCtx != null) plugin.bannerProviders(pluginCtx) else emptyList(),
                    sessionService = sec.sessionService,
                )
            )

    if (plugin != null && pluginCtx != null) {
        for (filter in plugin.filters(pluginCtx)) {
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
