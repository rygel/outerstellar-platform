package io.github.rygel.outerstellar.platform.web.assembly

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.composition.PlatformMode
import io.github.rygel.outerstellar.platform.composition.RegisteredRoute
import io.github.rygel.outerstellar.platform.composition.RouteGroup
import io.github.rygel.outerstellar.platform.composition.RouteOwner
import io.github.rygel.outerstellar.platform.composition.RouteRegistry
import io.github.rygel.outerstellar.platform.di.CoreComponents
import io.github.rygel.outerstellar.platform.di.WebComponents
import io.github.rygel.outerstellar.platform.plugin.HostedAppContribution
import io.github.rygel.outerstellar.platform.search.ContactSearchProvider
import io.github.rygel.outerstellar.platform.search.MessageSearchProvider
import io.github.rygel.outerstellar.platform.security.AppleOAuthProvider
import io.github.rygel.outerstellar.platform.security.OAuthProvider
import io.github.rygel.outerstellar.platform.security.SecurityComponents
import io.github.rygel.outerstellar.platform.web.ApiKeyRoutes
import io.github.rygel.outerstellar.platform.web.AuthRoutes
import io.github.rygel.outerstellar.platform.web.ContactsRoutes
import io.github.rygel.outerstellar.platform.web.ErrorRoutes
import io.github.rygel.outerstellar.platform.web.HomeRoutes
import io.github.rygel.outerstellar.platform.web.NotificationRoutes
import io.github.rygel.outerstellar.platform.web.OAuthRoutes
import io.github.rygel.outerstellar.platform.web.PasswordRoutes
import io.github.rygel.outerstellar.platform.web.ProfileRoutes
import io.github.rygel.outerstellar.platform.web.RequestContext
import io.github.rygel.outerstellar.platform.web.SearchRoutes
import io.github.rygel.outerstellar.platform.web.SessionCookie
import io.github.rygel.outerstellar.platform.web.SettingsRoutes
import io.github.rygel.outerstellar.platform.web.WebPageFactory
import io.github.rygel.outerstellar.platform.web.composition.PlatformPageSets
import io.github.rygel.outerstellar.platform.web.shellRenderer
import org.http4k.contract.ContractRoute
import org.http4k.contract.bindContract
import org.http4k.contract.contract
import org.http4k.contract.openapi.ApiInfo
import org.http4k.contract.openapi.v3.OpenApi3
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.cookie.cookie
import org.http4k.format.KotlinxSerialization
import org.http4k.template.TemplateRenderer

@Suppress("LongParameterList")
internal class UiRouteRegistrar(
    private val config: AppConfig,
    private val security: SecurityComponents,
    private val core: CoreComponents,
    private val web: WebComponents,
    private val pluginContribution: HostedAppContribution,
) {
    fun register(registry: RouteRegistry) {
        val publicContractRoutes = mutableListOf<ContractRoute>()
        val protectedContractRoutes = mutableListOf<ContractRoute>()

        registerUiKernelRoutes(registry, publicContractRoutes, protectedContractRoutes)

        when (pluginContribution.mode) {
            PlatformMode.FullPlatformApp ->
                registerFullPlatformUiRoutes(registry, publicContractRoutes, protectedContractRoutes)
            PlatformMode.PluginHostedApp -> {
                val included = pluginContribution.includedPlatformPages
                included.forEach { registerPageSet(it, registry, publicContractRoutes, protectedContractRoutes) }
                registerExcludedPageSets(registry, included)
            }

            PlatformMode.HeadlessKernel -> registerExcludedPageSets(registry, emptySet())
        }

        registerLogoutRoute(protectedContractRoutes, registry)
        assembleUiContracts(registry, publicContractRoutes, protectedContractRoutes)
    }

    private fun registerUiKernelRoutes(
        registry: RouteRegistry,
        publicRoutes: MutableList<ContractRoute>,
        protectedRoutes: MutableList<ContractRoute>,
    ) {
        registerAuthRoutes(registry, publicRoutes)
        registerPasswordRoutes(registry, publicRoutes, protectedRoutes)
        registerOAuthRoutes(registry, publicRoutes)
        registerErrorRoutes(registry, publicRoutes)
    }

    private fun registerFullPlatformUiRoutes(
        registry: RouteRegistry,
        publicRoutes: MutableList<ContractRoute>,
        protectedRoutes: MutableList<ContractRoute>,
    ) {
        registerSearchPages(registry, publicRoutes, web.pageFactory, web.templateRenderer)
        registerHomePages(registry, publicRoutes, protectedRoutes, web.pageFactory, web.templateRenderer)
        registerNotificationPages(registry, publicRoutes, protectedRoutes, web.pageFactory, web.templateRenderer)
        registerContactsPages(registry, protectedRoutes, web.pageFactory, web.templateRenderer)
        registerProfilePages(registry, protectedRoutes, web.pageFactory, web.templateRenderer)
        registerApiKeyPages(registry, protectedRoutes, web.pageFactory, web.templateRenderer)
        registerSettingsPages(registry, protectedRoutes, web.pageFactory, web.templateRenderer)
    }

    private fun registerAuthRoutes(registry: RouteRegistry, publicRoutes: MutableList<ContractRoute>) {
        val authRoutes =
            AuthRoutes(
                web.pageFactory,
                web.templateRenderer,
                security.authService,
                security.sessionService,
                security.passwordResetService,
                web.analyticsService,
                config,
            )
        authRoutes.routes.forEach { route ->
            registry.register(
                RegisteredRoute(route, RouteOwner.PlatformKernel, RouteGroup.PublicUi, "/auth", "*", "Auth")
            )
        }
        publicRoutes += authRoutes.routes
    }

    private fun registerPasswordRoutes(
        registry: RouteRegistry,
        publicRoutes: MutableList<ContractRoute>,
        protectedRoutes: MutableList<ContractRoute>,
    ) {
        val passwordRoutes =
            PasswordRoutes(
                web.pageFactory,
                web.templateRenderer,
                security.accountService,
                security.passwordResetService,
            )
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
    }

    private fun registerOAuthRoutes(registry: RouteRegistry, publicRoutes: MutableList<ContractRoute>) {
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
                oauthService = security.oauthService,
                sessionService = security.sessionService,
                sessionCookieSecure = config.sessionCookieSecure,
                appBaseUrl = config.appBaseUrl,
            )
        oauthRoutes.routes.forEach { route ->
            registry.register(
                RegisteredRoute(route, RouteOwner.PlatformKernel, RouteGroup.PublicUi, "/auth/oauth", "*", "OAuth")
            )
        }
        publicRoutes += oauthRoutes.routes
    }

    private fun registerErrorRoutes(registry: RouteRegistry, publicRoutes: MutableList<ContractRoute>) {
        val errorRoutes = ErrorRoutes(web.pageFactory, web.templateRenderer)
        errorRoutes.routes.forEach { route ->
            registry.register(
                RegisteredRoute(route, RouteOwner.PlatformKernel, RouteGroup.PublicUi, "/errors", "GET", "Error pages")
            )
        }
        publicRoutes += errorRoutes.routes
    }

    private fun registerExcludedPageSets(registry: RouteRegistry, included: Set<PlatformPageSets>) {
        PlatformPageSets.entries.filter { it !in included }.forEach { registry.registerExcludedPageSet(it.pageSet.id) }
    }

    private fun registerLogoutRoute(protectedRoutes: MutableList<ContractRoute>, registry: RouteRegistry) {
        protectedRoutes +=
            ("/logout" bindContract POST).to { request: Request ->
                val rawToken = request.cookie(RequestContext.SESSION_COOKIE)?.value
                if (rawToken != null) {
                    security.sessionService.deleteSession(rawToken)
                }
                Response(Status.FOUND)
                    .header("location", request.shellRenderer.url("/"))
                    .header("Set-Cookie", SessionCookie.clear(config.sessionCookieSecure))
            }
        registry.register(
            RegisteredRoute(null, RouteOwner.PlatformKernel, RouteGroup.ProtectedUi, "/logout", "POST", "Logout")
        )
    }

    private fun assembleUiContracts(
        registry: RouteRegistry,
        publicRoutes: MutableList<ContractRoute>,
        protectedRoutes: MutableList<ContractRoute>,
    ) {
        val appLabel = pluginContribution.appLabel
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

    private fun registerPageSet(
        pageSet: PlatformPageSets,
        registry: RouteRegistry,
        publicRoutes: MutableList<ContractRoute>,
        protectedRoutes: MutableList<ContractRoute>,
    ) {
        when (pageSet) {
            PlatformPageSets.HOME ->
                registerHomePages(registry, publicRoutes, protectedRoutes, web.pageFactory, web.templateRenderer)
            PlatformPageSets.CONTACTS ->
                registerContactsPages(registry, protectedRoutes, web.pageFactory, web.templateRenderer)
            PlatformPageSets.SETTINGS ->
                registerSettingsPages(registry, protectedRoutes, web.pageFactory, web.templateRenderer)
            PlatformPageSets.SEARCH ->
                registerSearchPages(registry, publicRoutes, web.pageFactory, web.templateRenderer)
            PlatformPageSets.NOTIFICATIONS ->
                registerNotificationPages(
                    registry,
                    publicRoutes,
                    protectedRoutes,
                    web.pageFactory,
                    web.templateRenderer,
                )
            PlatformPageSets.PROFILE ->
                registerProfilePages(registry, protectedRoutes, web.pageFactory, web.templateRenderer)
            PlatformPageSets.ADMIN -> {}
            PlatformPageSets.DEV_DASHBOARD -> {}
        }
    }

    private fun registerHomePages(
        registry: RouteRegistry,
        publicRoutes: MutableList<ContractRoute>,
        protectedRoutes: MutableList<ContractRoute>,
        pageFactory: WebPageFactory,
        jteRenderer: TemplateRenderer,
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
        protectedRoutes: MutableList<ContractRoute>,
        pageFactory: WebPageFactory,
        jteRenderer: TemplateRenderer,
    ) {
        val contactsRoutes = ContactsRoutes(pageFactory, jteRenderer, core.contactService)
        contactsRoutes.routes.forEach { route ->
            registry.register(
                RegisteredRoute(route, RouteOwner.PlatformUi, RouteGroup.ProtectedUi, "/contacts", "*", "Contacts")
            )
        }
        protectedRoutes += contactsRoutes.routes
    }

    private fun registerApiKeyPages(
        registry: RouteRegistry,
        protectedRoutes: MutableList<ContractRoute>,
        pageFactory: WebPageFactory,
        jteRenderer: TemplateRenderer,
    ) {
        val apiKeyRoutes = ApiKeyRoutes(pageFactory, jteRenderer, security.apiKeyService)
        apiKeyRoutes.routes.forEach { route ->
            registry.register(
                RegisteredRoute(
                    route,
                    RouteOwner.PlatformUi,
                    RouteGroup.ProtectedUi,
                    "/settings/api-keys",
                    "*",
                    "API keys",
                )
            )
        }
        protectedRoutes += apiKeyRoutes.routes
    }

    private fun registerSettingsPages(
        registry: RouteRegistry,
        protectedRoutes: MutableList<ContractRoute>,
        pageFactory: WebPageFactory,
        jteRenderer: TemplateRenderer,
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
        publicRoutes: MutableList<ContractRoute>,
        pageFactory: WebPageFactory,
        jteRenderer: TemplateRenderer,
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
        publicRoutes: MutableList<ContractRoute>,
        protectedRoutes: MutableList<ContractRoute>,
        pageFactory: WebPageFactory,
        jteRenderer: TemplateRenderer,
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
        protectedRoutes: MutableList<ContractRoute>,
        pageFactory: WebPageFactory,
        jteRenderer: TemplateRenderer,
    ) {
        val profileRoutes = ProfileRoutes(pageFactory, jteRenderer, security.accountService, config.sessionCookieSecure)
        profileRoutes.routes.forEach { route ->
            registry.register(
                RegisteredRoute(route, RouteOwner.PlatformUi, RouteGroup.ProtectedUi, "/profile", "*", "Profile")
            )
        }
        protectedRoutes += profileRoutes.routes
    }
}
