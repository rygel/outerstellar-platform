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
import io.github.rygel.outerstellar.platform.web.AdminPageFactory
import io.github.rygel.outerstellar.platform.web.ApiKeyRoutes
import io.github.rygel.outerstellar.platform.web.AuthRoutes
import io.github.rygel.outerstellar.platform.web.ContactsPageFactory
import io.github.rygel.outerstellar.platform.web.ContactsRoutes
import io.github.rygel.outerstellar.platform.web.ErrorRoutes
import io.github.rygel.outerstellar.platform.web.HomePageFactory
import io.github.rygel.outerstellar.platform.web.HomeRoutes
import io.github.rygel.outerstellar.platform.web.InfraPageFactory
import io.github.rygel.outerstellar.platform.web.NotificationRoutes
import io.github.rygel.outerstellar.platform.web.OAuthRoutes
import io.github.rygel.outerstellar.platform.web.PasswordRoutes
import io.github.rygel.outerstellar.platform.web.ProfileRoutes
import io.github.rygel.outerstellar.platform.web.RequestContext
import io.github.rygel.outerstellar.platform.web.SearchPageFactory
import io.github.rygel.outerstellar.platform.web.SearchRoutes
import io.github.rygel.outerstellar.platform.web.SessionCookie
import io.github.rygel.outerstellar.platform.web.SettingsPageFactory
import io.github.rygel.outerstellar.platform.web.SettingsRoutes
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
        registerSearchPages(registry, publicRoutes, web.pages.searchPageFactory, web.runtime.templateRenderer)
        registerHomePages(
            registry,
            publicRoutes,
            protectedRoutes,
            web.pages.homePageFactory,
            web.pages.infraPageFactory,
            web.runtime.templateRenderer,
        )
        registerNotificationPages(
            registry,
            publicRoutes,
            protectedRoutes,
            web.pages.adminPageFactory,
            web.runtime.templateRenderer,
        )
        registerContactsPages(registry, protectedRoutes, web.pages.contactsPageFactory, web.runtime.templateRenderer)
        registerProfilePages(registry, protectedRoutes, web.pages.adminPageFactory, web.runtime.templateRenderer)
        registerApiKeyPages(registry, protectedRoutes, web.pages.adminPageFactory, web.runtime.templateRenderer)
        registerSettingsPages(registry, protectedRoutes, web.pages.settingsPageFactory, web.runtime.templateRenderer)
    }

    private fun registerAuthRoutes(registry: RouteRegistry, publicRoutes: MutableList<ContractRoute>) {
        val authRoutes =
            AuthRoutes(
                web.pages.authPageFactory,
                web.runtime.templateRenderer,
                security.authService,
                security.sessionService,
                security.passwordResetService,
                web.runtime.analyticsService,
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
                web.pages.authPageFactory,
                web.runtime.templateRenderer,
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
        val errorRoutes = ErrorRoutes(web.pages.errorPageFactory, web.runtime.templateRenderer)
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
                registerHomePages(
                    registry,
                    publicRoutes,
                    protectedRoutes,
                    web.pages.homePageFactory,
                    web.pages.infraPageFactory,
                    web.runtime.templateRenderer,
                )
            PlatformPageSets.CONTACTS ->
                registerContactsPages(
                    registry,
                    protectedRoutes,
                    web.pages.contactsPageFactory,
                    web.runtime.templateRenderer,
                )
            PlatformPageSets.SETTINGS ->
                registerSettingsPages(
                    registry,
                    protectedRoutes,
                    web.pages.settingsPageFactory,
                    web.runtime.templateRenderer,
                )
            PlatformPageSets.SEARCH ->
                registerSearchPages(registry, publicRoutes, web.pages.searchPageFactory, web.runtime.templateRenderer)
            PlatformPageSets.NOTIFICATIONS ->
                registerNotificationPages(
                    registry,
                    publicRoutes,
                    protectedRoutes,
                    web.pages.adminPageFactory,
                    web.runtime.templateRenderer,
                )
            PlatformPageSets.PROFILE ->
                registerProfilePages(
                    registry,
                    protectedRoutes,
                    web.pages.adminPageFactory,
                    web.runtime.templateRenderer,
                )
            PlatformPageSets.ADMIN -> {}
            PlatformPageSets.DEV_DASHBOARD -> {}
        }
    }

    private fun registerHomePages(
        registry: RouteRegistry,
        publicRoutes: MutableList<ContractRoute>,
        protectedRoutes: MutableList<ContractRoute>,
        homePageFactory: HomePageFactory,
        infraPageFactory: InfraPageFactory,
        jteRenderer: TemplateRenderer,
    ) {
        val homeRoutes = HomeRoutes(core.messageService, homePageFactory, infraPageFactory, jteRenderer)
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
        contactsPageFactory: ContactsPageFactory,
        jteRenderer: TemplateRenderer,
    ) {
        val contactsRoutes = ContactsRoutes(contactsPageFactory, jteRenderer, core.contactService)
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
        adminPageFactory: AdminPageFactory,
        jteRenderer: TemplateRenderer,
    ) {
        val apiKeyRoutes = ApiKeyRoutes(adminPageFactory, jteRenderer, security.apiKeyService)
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
        settingsPageFactory: SettingsPageFactory,
        jteRenderer: TemplateRenderer,
    ) {
        val settingsRoutes = SettingsRoutes(settingsPageFactory, jteRenderer)
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
        searchPageFactory: SearchPageFactory,
        jteRenderer: TemplateRenderer,
    ) {
        val searchProviders =
            listOfNotNull(MessageSearchProvider(core.messageService), ContactSearchProvider(core.contactService))
        val searchRoutes = SearchRoutes(searchPageFactory, jteRenderer, searchProviders)
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
        adminPageFactory: AdminPageFactory,
        jteRenderer: TemplateRenderer,
    ) {
        val notificationRoutes = NotificationRoutes(adminPageFactory, jteRenderer, web.notificationService)
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
        adminPageFactory: AdminPageFactory,
        jteRenderer: TemplateRenderer,
    ) {
        val profileRoutes =
            ProfileRoutes(adminPageFactory, jteRenderer, security.accountService, config.sessionCookieSecure)
        profileRoutes.routes.forEach { route ->
            registry.register(
                RegisteredRoute(route, RouteOwner.PlatformUi, RouteGroup.ProtectedUi, "/profile", "*", "Profile")
            )
        }
        protectedRoutes += profileRoutes.routes
    }
}
