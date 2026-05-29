package io.github.rygel.outerstellar.platform

import io.github.rygel.outerstellar.platform.composition.PlatformMode
import io.github.rygel.outerstellar.platform.composition.RegisteredRoute
import io.github.rygel.outerstellar.platform.composition.RouteGroup
import io.github.rygel.outerstellar.platform.composition.RouteOwner
import io.github.rygel.outerstellar.platform.composition.RouteRegistry
import io.github.rygel.outerstellar.platform.di.CoreComponents
import io.github.rygel.outerstellar.platform.di.PersistenceComponents
import io.github.rygel.outerstellar.platform.di.WebComponents
import io.github.rygel.outerstellar.platform.export.ContactExportProvider
import io.github.rygel.outerstellar.platform.export.MessageExportProvider
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.plugin.HostedApp
import io.github.rygel.outerstellar.platform.plugin.HostedAppContext
import io.github.rygel.outerstellar.platform.plugin.HostedAppContribution
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
import io.github.rygel.outerstellar.platform.web.PluginAdminDashboardPage
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
import io.github.rygel.outerstellar.platform.web.composition.PlatformPageSets
import io.github.rygel.outerstellar.platform.web.etagCachingFilter
import io.github.rygel.outerstellar.platform.web.hostedAppContextFromHostServices
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
    plugin: HostedApp? = null,
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
    plugin: HostedApp?,
): HttpHandler {
    val registry = RouteRegistry()
    val pluginContext = plugin?.let { buildPluginContext(web.templateRenderer, config, persistence, security, web) }
    val pluginContribution = HostedAppContribution.from(plugin, config.platformMode, pluginContext)
    val sec = security
    val realms = listOf(SessionRealm(sec.sessionService), ApiKeyRealm(sec.apiKeyService))
    val (bearerSecurity, bearerAdminSecurity) = buildBearerSecurityPair(realms)
    registerApiRoutes(
        registry,
        config,
        persistence,
        security,
        core,
        web,
        pluginContribution,
        bearerSecurity,
        bearerAdminSecurity,
    )
    registerUiRoutes(registry, config, security, core, web, pluginContribution)
    registerComponentRoutes(registry, web, pluginContribution)
    registerAdminRoutes(registry, config, persistence, security, web, pluginContribution)
    registerKernelRoutes(registry)
    registerTotpRoutes(registry)
    registerPluginRoutes(registry, pluginContribution)
    registry.requireNoConflicts()
    logger.info(registry.formatTable())
    return buildFromRegistry(registry, config, persistence, security, web, pluginContribution)
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

private fun registerApiRoutes(
    registry: RouteRegistry,
    config: AppConfig,
    persistence: PersistenceComponents,
    security: SecurityComponents,
    core: CoreComponents,
    web: WebComponents,
    pluginContribution: HostedAppContribution,
    bearerSecurity: Security,
    bearerAdminSecurity: Security,
) {
    val sec = security
    val appLabel = pluginContribution.appLabel
    val apiContract = contract {
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
    registry.register(
        RegisteredRoute(
            apiContract,
            RouteOwner.PlatformKernel,
            RouteGroup.Api,
            "/api/openapi.json",
            "GET",
            "API routes",
        )
    )

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
    registry.register(
        RegisteredRoute(
            syncContract,
            RouteOwner.PlatformKernel,
            RouteGroup.Api,
            "/api/v1/sync/openapi.json",
            "GET",
            "Sync API",
        )
    )

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
    registry.register(
        RegisteredRoute(
            bearerAdminApiContract,
            RouteOwner.PlatformKernel,
            RouteGroup.Api,
            "/api/v1/admin/api-openapi.json",
            "GET",
            "Admin API",
        )
    )
}

private fun registerUiRoutes(
    registry: RouteRegistry,
    config: AppConfig,
    security: SecurityComponents,
    core: CoreComponents,
    web: WebComponents,
    pluginContribution: HostedAppContribution,
) {
    val sec = security
    val sessionCookieSecure = config.sessionCookieSecure
    val pageFactory = web.pageFactory
    val jteRenderer = web.templateRenderer
    val appLabel = pluginContribution.appLabel

    val publicContractRoutes = mutableListOf<org.http4k.contract.ContractRoute>()
    val protectedContractRoutes = mutableListOf<org.http4k.contract.ContractRoute>()

    registerUiKernelRoutes(
        registry,
        publicContractRoutes,
        protectedContractRoutes,
        pageFactory,
        jteRenderer,
        sec,
        web,
        config,
        sessionCookieSecure,
    )

    when (pluginContribution.mode) {
        PlatformMode.FullPlatformApp -> {
            registerFullPlatformUiRoutes(
                registry,
                publicContractRoutes,
                protectedContractRoutes,
                pageFactory,
                jteRenderer,
                core,
                web,
                sec,
                sessionCookieSecure,
            )
        }

        PlatformMode.PluginHostedApp -> {
            val included = pluginContribution.includedPlatformPages
            for (pageSet in included) {
                registerPageSet(
                    pageSet,
                    registry,
                    publicContractRoutes,
                    protectedContractRoutes,
                    core,
                    web,
                    sessionCookieSecure,
                    sec,
                )
            }
            registerExcludedPageSets(registry, included)
        }

        PlatformMode.HeadlessKernel -> registerExcludedPageSets(registry, emptySet())
    }

    registerLogoutRoute(protectedContractRoutes, registry, sec, sessionCookieSecure)
    assembleUiContracts(registry, publicContractRoutes, protectedContractRoutes, appLabel)
}

private fun registerExcludedPageSets(registry: RouteRegistry, included: Set<PlatformPageSets>) {
    PlatformPageSets.entries.filter { it !in included }.forEach { registry.registerExcludedPageSet(it.pageSet.id) }
}

private fun registerLogoutRoute(
    protectedRoutes: MutableList<org.http4k.contract.ContractRoute>,
    registry: RouteRegistry,
    sec: SecurityComponents,
    sessionCookieSecure: Boolean,
) {
    protectedRoutes +=
        ("/logout" bindContract POST).to { request: Request ->
            val rawToken = request.cookie(RequestContext.SESSION_COOKIE)?.value
            if (rawToken != null) {
                sec.sessionService.deleteSession(rawToken)
            }
            Response(Status.FOUND)
                .header("location", request.shellRenderer.url("/"))
                .header("Set-Cookie", SessionCookie.clear(sessionCookieSecure))
        }
    registry.register(
        RegisteredRoute(null, RouteOwner.PlatformKernel, RouteGroup.ProtectedUi, "/logout", "POST", "Logout")
    )
}

private fun assembleUiContracts(
    registry: RouteRegistry,
    publicRoutes: MutableList<org.http4k.contract.ContractRoute>,
    protectedRoutes: MutableList<org.http4k.contract.ContractRoute>,
    appLabel: String,
) {
    val publicContract = contract {
        renderer = OpenApi3(ApiInfo("$appLabel UI", "v1.0"), KotlinxSerialization)
        descriptionPath = "/ui/openapi.json"
        routes += publicRoutes
    }
    registry.register(
        RegisteredRoute(
            publicContract,
            RouteOwner.PlatformKernel,
            RouteGroup.PublicUi,
            "/ui/openapi.json",
            "GET",
            "Public UI",
        )
    )

    val protectedContract = contract {
        renderer = OpenApi3(ApiInfo("$appLabel Protected UI", "v1.0"), KotlinxSerialization)
        descriptionPath = "/ui-protected/openapi.json"
        routes += protectedRoutes
    }
    registry.register(
        RegisteredRoute(
            protectedContract,
            RouteOwner.PlatformKernel,
            RouteGroup.ProtectedUi,
            "/ui-protected/openapi.json",
            "GET",
            "Protected UI",
        )
    )
}

private fun registerUiKernelRoutes(
    registry: RouteRegistry,
    publicRoutes: MutableList<org.http4k.contract.ContractRoute>,
    protectedRoutes: MutableList<org.http4k.contract.ContractRoute>,
    pageFactory: io.github.rygel.outerstellar.platform.web.WebPageFactory,
    jteRenderer: org.http4k.template.TemplateRenderer,
    sec: SecurityComponents,
    web: WebComponents,
    config: AppConfig,
    sessionCookieSecure: Boolean,
) {
    val authRoutes =
        AuthRoutes(
            pageFactory,
            jteRenderer,
            sec.authService,
            sec.sessionService,
            sec.passwordResetService,
            web.analyticsService,
            config,
        )
    authRoutes.routes.forEach { route ->
        registry.register(RegisteredRoute(route, RouteOwner.PlatformKernel, RouteGroup.PublicUi, "/auth", "*", "Auth"))
    }
    publicRoutes += authRoutes.routes

    val passwordRoutes = PasswordRoutes(pageFactory, jteRenderer, sec.accountService, sec.passwordResetService)
    passwordRoutes.publicRoutes.forEach { route ->
        registry.register(
            RegisteredRoute(
                route,
                RouteOwner.PlatformKernel,
                RouteGroup.PublicUi,
                "/auth/reset",
                "GET",
                "Password reset",
            )
        )
    }
    publicRoutes += passwordRoutes.publicRoutes
    passwordRoutes.protectedRoutes.forEach { route ->
        registry.register(
            RegisteredRoute(
                route,
                RouteOwner.PlatformKernel,
                RouteGroup.ProtectedUi,
                "/password",
                "*",
                "Password change",
            )
        )
    }
    protectedRoutes += passwordRoutes.protectedRoutes

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
    val oauthRoutes =
        OAuthRoutes(
            providers = oauthProviders,
            oauthService = sec.oauthService,
            sessionService = sec.sessionService,
            sessionCookieSecure = sessionCookieSecure,
            appBaseUrl = config.appBaseUrl,
        )
    oauthRoutes.routes.forEach { route ->
        registry.register(
            RegisteredRoute(route, RouteOwner.PlatformKernel, RouteGroup.PublicUi, "/auth/oauth", "*", "OAuth")
        )
    }
    publicRoutes += oauthRoutes.routes

    ErrorRoutes(pageFactory, jteRenderer).routes.forEach { route ->
        registry.register(
            RegisteredRoute(route, RouteOwner.PlatformKernel, RouteGroup.PublicUi, "/errors", "GET", "Error pages")
        )
    }
    publicRoutes += ErrorRoutes(pageFactory, jteRenderer).routes
}

private fun registerFullPlatformUiRoutes(
    registry: RouteRegistry,
    publicRoutes: MutableList<org.http4k.contract.ContractRoute>,
    protectedRoutes: MutableList<org.http4k.contract.ContractRoute>,
    pageFactory: io.github.rygel.outerstellar.platform.web.WebPageFactory,
    jteRenderer: org.http4k.template.TemplateRenderer,
    core: CoreComponents,
    web: WebComponents,
    sec: SecurityComponents,
    sessionCookieSecure: Boolean,
) {
    val searchProviders =
        listOfNotNull(MessageSearchProvider(core.messageService), ContactSearchProvider(core.contactService))
    SearchRoutes(pageFactory, jteRenderer, searchProviders).routes.forEach { route ->
        registry.register(
            RegisteredRoute(route, RouteOwner.PlatformUi, RouteGroup.PublicUi, "/search", "GET", "Search")
        )
    }
    publicRoutes += SearchRoutes(pageFactory, jteRenderer, searchProviders).routes

    val homeRoutes = HomeRoutes(core.messageService, pageFactory, jteRenderer)
    homeRoutes.publicRoutes.forEach { route ->
        registry.register(
            RegisteredRoute(route, RouteOwner.PlatformUi, RouteGroup.PublicUi, "/", "GET", "Home (public)")
        )
    }
    publicRoutes += homeRoutes.publicRoutes
    homeRoutes.protectedRoutes.forEach { route ->
        registry.register(
            RegisteredRoute(route, RouteOwner.PlatformUi, RouteGroup.ProtectedUi, "/", "GET", "Home (protected)")
        )
    }
    protectedRoutes += homeRoutes.protectedRoutes

    val notificationRoutes = NotificationRoutes(pageFactory, jteRenderer, web.notificationService)
    notificationRoutes.publicRoutes.forEach { route ->
        registry.register(
            RegisteredRoute(
                route,
                RouteOwner.PlatformUi,
                RouteGroup.PublicUi,
                "/components/notification-bell",
                "GET",
                "Notification bell",
            )
        )
    }
    publicRoutes += notificationRoutes.publicRoutes
    notificationRoutes.protectedRoutes.forEach { route ->
        registry.register(
            RegisteredRoute(
                route,
                RouteOwner.PlatformUi,
                RouteGroup.ProtectedUi,
                "/notifications",
                "*",
                "Notifications",
            )
        )
    }
    protectedRoutes += notificationRoutes.protectedRoutes

    ContactsRoutes(pageFactory, jteRenderer, core.contactService).routes.forEach { route ->
        registry.register(
            RegisteredRoute(route, RouteOwner.PlatformUi, RouteGroup.ProtectedUi, "/contacts", "*", "Contacts")
        )
    }
    protectedRoutes += ContactsRoutes(pageFactory, jteRenderer, core.contactService).routes

    ProfileRoutes(pageFactory, jteRenderer, sec.accountService, sessionCookieSecure).routes.forEach { route ->
        registry.register(
            RegisteredRoute(route, RouteOwner.PlatformUi, RouteGroup.ProtectedUi, "/profile", "*", "Profile")
        )
    }
    protectedRoutes += ProfileRoutes(pageFactory, jteRenderer, sec.accountService, sessionCookieSecure).routes

    ApiKeyRoutes(pageFactory, jteRenderer, sec.apiKeyService).routes.forEach { route ->
        registry.register(
            RegisteredRoute(route, RouteOwner.PlatformUi, RouteGroup.ProtectedUi, "/settings/api-keys", "*", "API keys")
        )
    }
    protectedRoutes += ApiKeyRoutes(pageFactory, jteRenderer, sec.apiKeyService).routes

    SettingsRoutes(pageFactory, jteRenderer).routes.forEach { route ->
        registry.register(
            RegisteredRoute(route, RouteOwner.PlatformUi, RouteGroup.ProtectedUi, "/settings", "*", "Settings")
        )
    }
    protectedRoutes += SettingsRoutes(pageFactory, jteRenderer).routes
}

private fun registerPageSet(
    pageSet: PlatformPageSets,
    registry: RouteRegistry,
    publicRoutes: MutableList<org.http4k.contract.ContractRoute>,
    protectedRoutes: MutableList<org.http4k.contract.ContractRoute>,
    core: CoreComponents,
    web: WebComponents,
    sessionCookieSecure: Boolean,
    sec: SecurityComponents,
) {
    val pageFactory = web.pageFactory
    val jteRenderer = web.templateRenderer
    when (pageSet) {
        PlatformPageSets.HOME ->
            registerHomePages(registry, publicRoutes, protectedRoutes, pageFactory, jteRenderer, core)
        PlatformPageSets.CONTACTS -> registerContactsPages(registry, protectedRoutes, pageFactory, jteRenderer, core)
        PlatformPageSets.SETTINGS -> registerSettingsPages(registry, protectedRoutes, pageFactory, jteRenderer)
        PlatformPageSets.SEARCH -> registerSearchPages(registry, publicRoutes, pageFactory, jteRenderer, core)
        PlatformPageSets.NOTIFICATIONS ->
            registerNotificationPages(registry, publicRoutes, protectedRoutes, pageFactory, jteRenderer, web)
        PlatformPageSets.PROFILE ->
            registerProfilePages(registry, protectedRoutes, pageFactory, jteRenderer, sec, sessionCookieSecure)
        PlatformPageSets.ADMIN -> {}
        PlatformPageSets.DEV_DASHBOARD -> {}
    }
}

private fun registerHomePages(
    registry: RouteRegistry,
    publicRoutes: MutableList<org.http4k.contract.ContractRoute>,
    protectedRoutes: MutableList<org.http4k.contract.ContractRoute>,
    pageFactory: io.github.rygel.outerstellar.platform.web.WebPageFactory,
    jteRenderer: org.http4k.template.TemplateRenderer,
    core: CoreComponents,
) {
    val homeRoutes = HomeRoutes(core.messageService, pageFactory, jteRenderer)
    homeRoutes.publicRoutes.forEach { route ->
        registry.register(
            RegisteredRoute(route, RouteOwner.PlatformUi, RouteGroup.PublicUi, "/", "GET", "Home (public)")
        )
    }
    publicRoutes += homeRoutes.publicRoutes
    homeRoutes.protectedRoutes.forEach { route ->
        registry.register(
            RegisteredRoute(route, RouteOwner.PlatformUi, RouteGroup.ProtectedUi, "/", "GET", "Home (protected)")
        )
    }
    protectedRoutes += homeRoutes.protectedRoutes
}

private fun registerContactsPages(
    registry: RouteRegistry,
    protectedRoutes: MutableList<org.http4k.contract.ContractRoute>,
    pageFactory: io.github.rygel.outerstellar.platform.web.WebPageFactory,
    jteRenderer: org.http4k.template.TemplateRenderer,
    core: CoreComponents,
) {
    val contactsRoutes = ContactsRoutes(pageFactory, jteRenderer, core.contactService)
    contactsRoutes.routes.forEach { route ->
        registry.register(
            RegisteredRoute(route, RouteOwner.PlatformUi, RouteGroup.ProtectedUi, "/contacts", "*", "Contacts")
        )
    }
    protectedRoutes += contactsRoutes.routes
}

private fun registerSettingsPages(
    registry: RouteRegistry,
    protectedRoutes: MutableList<org.http4k.contract.ContractRoute>,
    pageFactory: io.github.rygel.outerstellar.platform.web.WebPageFactory,
    jteRenderer: org.http4k.template.TemplateRenderer,
) {
    val settingsRoutes = SettingsRoutes(pageFactory, jteRenderer)
    settingsRoutes.routes.forEach { route ->
        registry.register(
            RegisteredRoute(route, RouteOwner.PlatformUi, RouteGroup.ProtectedUi, "/settings", "*", "Settings")
        )
    }
    protectedRoutes += settingsRoutes.routes
}

private fun registerSearchPages(
    registry: RouteRegistry,
    publicRoutes: MutableList<org.http4k.contract.ContractRoute>,
    pageFactory: io.github.rygel.outerstellar.platform.web.WebPageFactory,
    jteRenderer: org.http4k.template.TemplateRenderer,
    core: CoreComponents,
) {
    val searchProviders =
        listOfNotNull(MessageSearchProvider(core.messageService), ContactSearchProvider(core.contactService))
    val searchRoutes = SearchRoutes(pageFactory, jteRenderer, searchProviders)
    searchRoutes.routes.forEach { route ->
        registry.register(
            RegisteredRoute(route, RouteOwner.PlatformUi, RouteGroup.PublicUi, "/search", "GET", "Search")
        )
    }
    publicRoutes += searchRoutes.routes
}

private fun registerNotificationPages(
    registry: RouteRegistry,
    publicRoutes: MutableList<org.http4k.contract.ContractRoute>,
    protectedRoutes: MutableList<org.http4k.contract.ContractRoute>,
    pageFactory: io.github.rygel.outerstellar.platform.web.WebPageFactory,
    jteRenderer: org.http4k.template.TemplateRenderer,
    web: WebComponents,
) {
    val notificationRoutes = NotificationRoutes(pageFactory, jteRenderer, web.notificationService)
    notificationRoutes.publicRoutes.forEach { route ->
        registry.register(
            RegisteredRoute(
                route,
                RouteOwner.PlatformUi,
                RouteGroup.PublicUi,
                "/components/notification-bell",
                "GET",
                "Notification bell",
            )
        )
    }
    publicRoutes += notificationRoutes.publicRoutes
    notificationRoutes.protectedRoutes.forEach { route ->
        registry.register(
            RegisteredRoute(
                route,
                RouteOwner.PlatformUi,
                RouteGroup.ProtectedUi,
                "/notifications",
                "*",
                "Notifications",
            )
        )
    }
    protectedRoutes += notificationRoutes.protectedRoutes
}

private fun registerProfilePages(
    registry: RouteRegistry,
    protectedRoutes: MutableList<org.http4k.contract.ContractRoute>,
    pageFactory: io.github.rygel.outerstellar.platform.web.WebPageFactory,
    jteRenderer: org.http4k.template.TemplateRenderer,
    sec: SecurityComponents,
    sessionCookieSecure: Boolean,
) {
    val profileRoutes = ProfileRoutes(pageFactory, jteRenderer, sec.accountService, sessionCookieSecure)
    profileRoutes.routes.forEach { route ->
        registry.register(
            RegisteredRoute(route, RouteOwner.PlatformUi, RouteGroup.ProtectedUi, "/profile", "*", "Profile")
        )
    }
    protectedRoutes += profileRoutes.routes
}

private fun registerComponentRoutes(
    registry: RouteRegistry,
    web: WebComponents,
    pluginContribution: HostedAppContribution,
) {
    val appLabel = pluginContribution.appLabel
    val componentRoutes = ComponentRoutes(web.pageFactory, web.templateRenderer, web.voteService, web.pollService)
    val publicContract = contract {
        renderer = OpenApi3(ApiInfo("$appLabel Components", "v1.0"), KotlinxSerialization)
        descriptionPath = "/components/openapi.json"
        routes += componentRoutes.publicRoutes
    }
    registry.register(
        RegisteredRoute(
            publicContract,
            RouteOwner.PlatformKernel,
            RouteGroup.PublicUi,
            "/components/openapi.json",
            "GET",
            "Public components",
        )
    )
    val protectedContract = contract {
        renderer = OpenApi3(ApiInfo("$appLabel Protected Components", "v1.0"), KotlinxSerialization)
        descriptionPath = "/components-protected/openapi.json"
        routes += componentRoutes.protectedRoutes
    }
    registry.register(
        RegisteredRoute(
            protectedContract,
            RouteOwner.PlatformKernel,
            RouteGroup.ProtectedUi,
            "/components-protected/openapi.json",
            "GET",
            "Protected components",
        )
    )
}

@Suppress("LongParameterList")
private fun registerAdminRoutes(
    registry: RouteRegistry,
    config: AppConfig,
    persistence: PersistenceComponents,
    security: SecurityComponents,
    web: WebComponents,
    pluginContribution: HostedAppContribution,
) {
    val sec = security
    val pageFactory = web.pageFactory
    val jteRenderer = web.templateRenderer
    val devDashboardEnabled = config.devDashboardEnabled
    val appLabel = pluginContribution.appLabel
    val adminSecurity =
        object : Security {
            override val filter = Filter { next ->
                SecurityRules.authenticated(SecurityRules.hasRole(UserRole.ADMIN, next))
            }
        }
    val pluginSections = pluginContribution.adminSections
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
    val adminHandler: RoutingHttpHandler =
        if (pluginSections.isNotEmpty()) {
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
                                    shellConfig = ShellConfig(pluginOptions = pluginContribution.options.toWebOptions()),
                                )
                            val shell = pluginShellRenderer.shell("Plugin Dashboard", "/admin/plugins")
                            val page =
                                Page(
                                    shell,
                                    PluginAdminDashboardPage(
                                        cards = pluginSections.map { it.summaryCard },
                                        diagnostics = pluginContribution.diagnostics(),
                                    ),
                                )
                            Response(Status.OK).body(jteRenderer(page))
                        }
                )
            routes(adminContract, pluginDashboardRoute)
        } else {
            adminContract
        }
    registry.register(
        RegisteredRoute(adminHandler, RouteOwner.PlatformKernel, RouteGroup.Admin, "/admin", "GET", "Admin dashboard")
    )
}

private fun registerKernelRoutes(registry: RouteRegistry) {
    registry.register(
        RegisteredRoute(null, RouteOwner.PlatformKernel, RouteGroup.Static, "/static", "GET", "Static assets")
    )
    registry.register(
        RegisteredRoute(null, RouteOwner.PlatformKernel, RouteGroup.Health, "/health", "GET", "Health check")
    )
    registry.register(RegisteredRoute(null, RouteOwner.PlatformKernel, RouteGroup.Health, "/metrics", "GET", "Metrics"))
    registry.register(
        RegisteredRoute(null, RouteOwner.PlatformKernel, RouteGroup.Static, "/robots.txt", "GET", "Robots.txt")
    )
    registry.register(
        RegisteredRoute(null, RouteOwner.PlatformKernel, RouteGroup.Static, "/sitemap.xml", "GET", "Sitemap")
    )
}

private fun registerTotpRoutes(registry: RouteRegistry) {
    registry.register(RegisteredRoute(null, RouteOwner.PlatformKernel, RouteGroup.ProtectedUi, "/totp", "*", "TOTP UI"))
    registry.register(RegisteredRoute(null, RouteOwner.PlatformKernel, RouteGroup.Api, "/api/totp", "*", "TOTP API"))
}

private fun registerPluginRoutes(registry: RouteRegistry, pluginContribution: HostedAppContribution) {
    pluginContribution.routeRegistrations.forEach { registration ->
        registry.register(
            RegisteredRoute(
                registration.httpRoute,
                RouteOwner.Plugin,
                registration.group,
                registration.pathPattern,
                registration.method,
                registration.description,
            )
        )
    }
}

private fun buildFromRegistry(
    registry: RouteRegistry,
    config: AppConfig,
    persistence: PersistenceComponents,
    security: SecurityComponents,
    web: WebComponents,
    pluginContribution: HostedAppContribution,
): HttpHandler {
    val sec = security
    val userRepository = persistence.userRepository
    val authenticatedFilter = Filter { next -> SecurityRules.authenticated(next) }

    val publicUiRoutes = registry.byGroup(RouteGroup.PublicUi)
    val protectedUiRoutes = registry.byGroup(RouteGroup.ProtectedUi)
    val apiRoutes = registry.byGroup(RouteGroup.Api)
    val adminRoutes = registry.byGroup(RouteGroup.Admin)
    val staticRoutes = registry.byGroup(RouteGroup.Static)

    val publicUiHandlers = publicUiRoutes.mapNotNull { it.httpRoute as? RoutingHttpHandler }
    val protectedUiHandlers = protectedUiRoutes.mapNotNull { it.httpRoute as? RoutingHttpHandler }
    val apiHandlers = apiRoutes.mapNotNull { it.httpRoute as? RoutingHttpHandler }
    val adminHandlers = adminRoutes.mapNotNull { it.httpRoute as? RoutingHttpHandler }
    val staticHandlers = staticRoutes.mapNotNull { it.httpRoute as? RoutingHttpHandler }

    val filteredAdminHandler =
        Filter { next -> SecurityRules.authenticated(SecurityRules.hasRole(UserRole.ADMIN, next)) }
            .then(routes(adminHandlers))

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
    unfiltered += staticHandlers

    val appRoutes = mutableListOf<RoutingHttpHandler>()
    appRoutes += routes(publicUiHandlers)
    appRoutes += authenticatedFilter.then(routes(protectedUiHandlers))
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
    appRoutes += apiHandlers
    appRoutes += "/" bind filteredAdminHandler

    val baseApp = routes(unfiltered + appRoutes)
    return buildFilterChain(config, persistence, security, web, pluginContribution).then(baseApp)
}

private fun buildPluginContext(
    jteRenderer: TemplateRenderer,
    config: AppConfig,
    persistence: PersistenceComponents,
    security: SecurityComponents,
    web: WebComponents,
): HostedAppContext =
    hostedAppContextFromHostServices(
        renderer = jteRenderer,
        config = config,
        apiKeyService = security.apiKeyService,
        oauthService = security.oauthService,
        userRepository = persistence.userRepository,
        analytics = web.analyticsService,
        notificationService = web.notificationService,
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
    pluginContribution: HostedAppContribution,
): Filter {
    val sec = security
    val userRepository = persistence.userRepository
    val jwtService = sec.jwtService
    val pageFactory = web.pageFactory
    val jteRenderer = web.templateRenderer
    val analytics = web.analyticsService
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
                    pluginContribution.options.toWebOptions(),
                    cookieSecure = config.sessionCookieSecure,
                    appBaseUrl = config.appBaseUrl,
                    bannerProviders = pluginContribution.bannerProviders,
                    sessionService = sec.sessionService,
                )
            )

    for (filter in pluginContribution.filters) {
        chain = chain.then(filter)
    }

    return chain
        .then(analyticsPageViewFilter(analytics))
        .then(Filters.sessionTimeout(config.sessionCookieSecure))
        .then(Filters.securityFilter)
        .then(Filters.requestLogging)
        .then(Filters.serverMetrics)
        .then(Filters.globalErrorHandler(pageFactory, jteRenderer))
}

private fun io.github.rygel.outerstellar.platform.plugin.PluginOptions.toWebOptions():
    io.github.rygel.outerstellar.platform.web.PluginOptions =
    io.github.rygel.outerstellar.platform.web.PluginOptions(
        navItems =
            navItems.map {
                io.github.rygel.outerstellar.platform.web.PluginNavItem(it.label, it.url, it.icon, it.activeSection)
            },
        textResolver = textResolver,
        adminNavItems =
            adminNavItems.map { io.github.rygel.outerstellar.platform.web.AdminNavItem(it.label, it.url, it.icon) },
        layoutRenderer = layoutRenderer,
        assets = assets,
    )
