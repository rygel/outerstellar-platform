package io.github.rygel.outerstellar.platform.web.assembly

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.composition.PlatformMode
import io.github.rygel.outerstellar.platform.composition.RegisteredRoute
import io.github.rygel.outerstellar.platform.composition.RouteGroup
import io.github.rygel.outerstellar.platform.composition.RouteOwner
import io.github.rygel.outerstellar.platform.composition.RouteRegistry
import io.github.rygel.outerstellar.platform.di.CoreComponents
import io.github.rygel.outerstellar.platform.di.PlatformPersistence
import io.github.rygel.outerstellar.platform.di.WebComponents
import io.github.rygel.outerstellar.platform.export.ContactExportProvider
import io.github.rygel.outerstellar.platform.export.MessageExportProvider
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.plugin.HostedAppContribution
import io.github.rygel.outerstellar.platform.security.ApiKeyRealm
import io.github.rygel.outerstellar.platform.security.SecurityComponents
import io.github.rygel.outerstellar.platform.security.SecurityRules
import io.github.rygel.outerstellar.platform.security.SessionRealm
import io.github.rygel.outerstellar.platform.web.AuthApi
import io.github.rygel.outerstellar.platform.web.ComponentRoutes
import io.github.rygel.outerstellar.platform.web.DevDashboardRoutes
import io.github.rygel.outerstellar.platform.web.DeviceRegistrationApi
import io.github.rygel.outerstellar.platform.web.ExportRoutes
import io.github.rygel.outerstellar.platform.web.NotificationApi
import io.github.rygel.outerstellar.platform.web.Page
import io.github.rygel.outerstellar.platform.web.PluginAdminDashboardPage
import io.github.rygel.outerstellar.platform.web.PollApi
import io.github.rygel.outerstellar.platform.web.RequestContext
import io.github.rygel.outerstellar.platform.web.ShellConfig
import io.github.rygel.outerstellar.platform.web.ShellRenderer
import io.github.rygel.outerstellar.platform.web.SyncApi
import io.github.rygel.outerstellar.platform.web.UserAdminApi
import io.github.rygel.outerstellar.platform.web.UserAdminRoutes
import io.github.rygel.outerstellar.platform.web.VoteApi
import io.github.rygel.outerstellar.platform.web.composition.PlatformPageSets
import org.http4k.contract.ContractRoute
import org.http4k.contract.contract
import org.http4k.contract.openapi.ApiInfo
import org.http4k.contract.openapi.v3.OpenApi3
import org.http4k.core.Filter
import org.http4k.core.Method.GET
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.format.KotlinxSerialization
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.security.Security

@Suppress("LongParameterList")
internal class RouteRegistrar(
    private val config: AppConfig,
    private val persistence: PlatformPersistence,
    private val security: SecurityComponents,
    private val core: CoreComponents,
    private val web: WebComponents,
    private val pluginContribution: HostedAppContribution,
) {
    fun buildRegistry(): RouteRegistry {
        val registry = RouteRegistry()
        val realms = listOf(SessionRealm(security.sessionService), ApiKeyRealm(security.apiKeyService))
        val (bearerSecurity, bearerAdminSecurity) = SecurityConfigurator(realms).bearerSecurityPair()
        registerAll(registry, bearerSecurity, bearerAdminSecurity)
        registry.requireNoConflicts()
        return registry
    }

    private fun registerAll(registry: RouteRegistry, bearerSecurity: Security, bearerAdminSecurity: Security) {
        registerApiRoutes(registry, bearerSecurity, bearerAdminSecurity)
        UiRouteRegistrar(config, security, core, web, pluginContribution).register(registry)
        registerComponentRoutes(registry)
        registerAdminRoutes(registry)
        registerKernelRoutes(registry)
        registerTotpRoutes(registry)
        registerPluginRoutes(registry)
    }

    private fun registerApiRoutes(registry: RouteRegistry, bearerSecurity: Security, bearerAdminSecurity: Security) {
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
            routes += SyncApi(core.messageService, core.contactService, web.runtime.analyticsService).routes
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

    private fun registerComponentRoutes(registry: RouteRegistry) {
        val appLabel = pluginContribution.appLabel
        val componentRoutes =
            ComponentRoutes(
                web.pages.sidebarFactory,
                web.pages.homePageFactory,
                web.runtime.templateRenderer,
                web.voteService,
                web.pollService,
            )
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

    private fun registerAdminRoutes(registry: RouteRegistry) {
        val sec = security
        val includedPages = pluginContribution.includedPlatformPages
        val mountPlatformAdmin =
            pluginContribution.mode != PlatformMode.PluginHostedApp || PlatformPageSets.ADMIN in includedPages
        val mountDevDashboard =
            pluginContribution.mode != PlatformMode.PluginHostedApp || PlatformPageSets.DEV_DASHBOARD in includedPages
        val adminSecurity =
            object : Security {
                override val filter = Filter { next ->
                    SecurityRules.authenticated(SecurityRules.hasRole(UserRole.ADMIN, next))
                }
            }
        val pluginSections = pluginContribution.adminSections
        val contractRoutes = mutableListOf<ContractRoute>()
        if (mountDevDashboard) {
            contractRoutes +=
                DevDashboardRoutes(
                        persistence.outboxRepository,
                        core.messageCache,
                        web.pages.devDashboardPageFactory,
                        web.runtime.templateRenderer,
                        config.devDashboardEnabled,
                    )
                    .routes
        }
        if (mountPlatformAdmin) {
            contractRoutes +=
                UserAdminRoutes(web.pages.adminPageFactory, web.runtime.templateRenderer, sec.userAdminService).routes
        }
        pluginSections.forEach { section -> contractRoutes += section.route }
        if (contractRoutes.isEmpty()) return
        val adminContract = contract {
            renderer = OpenApi3(ApiInfo("${pluginContribution.appLabel} Admin", "v1.0"), KotlinxSerialization)
            descriptionPath = "/admin/openapi.json"
            this.security = adminSecurity
            routes += contractRoutes
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
                                        appVersion = config.version,
                                        shellConfig =
                                            ShellConfig.from(
                                                pluginContribution,
                                                appBaseUrl = config.appBaseUrl,
                                                sidebarFactory = web.pages.sidebarFactory,
                                            ),
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
                                Response(Status.OK).body(web.runtime.templateRenderer(page))
                            }
                    )
                routes(adminContract, pluginDashboardRoute)
            } else {
                adminContract
            }
        registry.register(
            RegisteredRoute(
                adminHandler,
                RouteOwner.PlatformKernel,
                RouteGroup.Admin,
                "/admin",
                "GET",
                "Admin dashboard",
            )
        )
    }

    private fun registerKernelRoutes(registry: RouteRegistry) {
        registry.register(
            RegisteredRoute(null, RouteOwner.PlatformKernel, RouteGroup.Static, "/static", "GET", "Static assets")
        )
        registry.register(
            RegisteredRoute(null, RouteOwner.PlatformKernel, RouteGroup.Health, "/health", "GET", "Health check")
        )
        registry.register(
            RegisteredRoute(null, RouteOwner.PlatformKernel, RouteGroup.Health, "/metrics", "GET", "Metrics")
        )
        registry.register(
            RegisteredRoute(null, RouteOwner.PlatformKernel, RouteGroup.Static, "/robots.txt", "GET", "Robots.txt")
        )
        registry.register(
            RegisteredRoute(null, RouteOwner.PlatformKernel, RouteGroup.Static, "/sitemap.xml", "GET", "Sitemap")
        )
    }

    private fun registerTotpRoutes(registry: RouteRegistry) {
        registry.register(
            RegisteredRoute(null, RouteOwner.PlatformKernel, RouteGroup.ProtectedUi, "/totp", "*", "TOTP UI")
        )
        registry.register(
            RegisteredRoute(null, RouteOwner.PlatformKernel, RouteGroup.Api, "/api/totp", "*", "TOTP API")
        )
    }

    private fun registerPluginRoutes(registry: RouteRegistry) {
        pluginContribution.routeRegistrations.forEach { registration ->
            val httpRoute =
                when (val route = registration.httpRoute) {
                    is ContractRoute -> contract { routes += route }
                    else -> route
                }
            registry.register(
                RegisteredRoute(
                    httpRoute,
                    RouteOwner.Plugin,
                    registration.group,
                    registration.pathPattern,
                    registration.method,
                    registration.description,
                )
            )
        }
    }
}
