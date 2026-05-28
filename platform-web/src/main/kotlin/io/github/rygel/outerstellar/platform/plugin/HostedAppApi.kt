package io.github.rygel.outerstellar.platform.plugin

import gg.jte.Content
import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.JteLayoutRouterGenerated
import gg.jte.html.OwaspHtmlTemplateOutput
import gg.jte.output.StringOutput
import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.PluginMigrationSource
import io.github.rygel.outerstellar.platform.TextResolver
import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.analytics.NoOpAnalyticsService
import io.github.rygel.outerstellar.platform.banner.BannerProvider
import io.github.rygel.outerstellar.platform.composition.PlatformMode
import io.github.rygel.outerstellar.platform.composition.RouteGroup
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.security.ApiKeyService
import io.github.rygel.outerstellar.platform.security.OAuthService
import io.github.rygel.outerstellar.platform.service.NotificationService
import io.github.rygel.outerstellar.platform.web.AdminSection
import io.github.rygel.outerstellar.platform.web.ShellView
import io.github.rygel.outerstellar.platform.web.WebPageFactory
import io.github.rygel.outerstellar.platform.web.composition.PlatformPageSets
import io.github.rygel.outerstellar.platform.web.requestContext
import org.http4k.contract.ContractRoute
import org.http4k.core.Filter
import org.http4k.core.Request
import org.http4k.lens.LensFailure
import org.http4k.routing.RoutingHttpHandler
import org.http4k.template.TemplateRenderer
import org.slf4j.LoggerFactory

/**
 * Plugin-facing SPI for one hosted application mounted inside outerstellar-platform.
 *
 * This package is the primary import surface for hosted app authors. It still lives in platform-web for now, but it is
 * intentionally isolated so it can become a small standalone plugin API module later.
 */

/**
 * Nav item contributed by a plugin to the shell navigation bar. [activeSection] defaults to [url] and is compared
 * against the current path to highlight the active link.
 */
data class PluginNavItem(val label: String, val url: String, val icon: String, val activeSection: String = url)

data class AdminNavItem(val label: String, val url: String, val icon: String)

data class HostedAppManifest(
    val id: String,
    val appLabel: String = "Outerstellar",
    val version: String = "dev",
    val requiredPlatformVersion: String? = null,
    val ownership: HostedAppOwnership = HostedAppOwnership.forPlugin(id),
)

data class HostedAppOwnership(
    val uiPrefixes: List<String>,
    val apiPrefixes: List<String>,
    val adminPrefixes: List<String>,
    val assetPrefixes: List<String>,
) {
    companion object {
        fun forPlugin(id: String): HostedAppOwnership =
            HostedAppOwnership(
                uiPrefixes = listOf("/$id", "/plugin/$id"),
                apiPrefixes = listOf("/api/$id", "/api/plugin/$id", "/api/v1/$id", "/api/v1/plugin/$id"),
                adminPrefixes = listOf("/admin/$id"),
                assetPrefixes = listOf("/plugins/$id/assets"),
            )
    }
}

data class PluginRouteRegistration(
    val route: ContractRoute?,
    val group: RouteGroup,
    val description: String,
    val pathPattern: String = description,
    val method: String = "*",
    val staticRoute: RoutingHttpHandler? = null,
) {
    internal val httpRoute: Any?
        get() = route ?: staticRoute

    companion object {
        fun staticAssets(
            route: RoutingHttpHandler,
            description: String,
            pathPattern: String,
            method: String = "GET",
        ): PluginRouteRegistration =
            PluginRouteRegistration(
                route = null,
                group = RouteGroup.Static,
                description = description,
                pathPattern = pathPattern,
                method = method,
                staticRoute = route,
            )
    }
}

data class PluginAssets(val stylesheets: List<String> = emptyList(), val scripts: List<String> = emptyList())

fun interface PluginLayoutRenderer {
    fun render(shell: ShellView, content: Content): Content
}

data class PluginOptions(
    val navItems: List<PluginNavItem> = emptyList(),
    val textResolver: TextResolver? = null,
    val adminNavItems: List<AdminNavItem> = emptyList(),
    val layoutRenderer: PluginLayoutRenderer? = null,
    val assets: PluginAssets = PluginAssets(),
)

/**
 * Host services provided to the plugin when it builds its routes. The plugin should depend only on this context rather
 * than injecting host services directly.
 */
data class HostedAppContext(
    val renderer: TemplateRenderer,
    val config: AppConfig,
    val apiKeyService: ApiKeyService,
    val oauthService: OAuthService,
    val userRepository: UserRepository,
    val analytics: AnalyticsService,
    val notificationService: NotificationService?,
    val pageFactory: WebPageFactory,
) {
    /** Returns the authenticated user for this request, or null if no user is logged in. */
    fun currentUser(request: Request): User? =
        try {
            request.requestContext.user
        } catch (e: LensFailure) {
            logger.debug("Lens extraction failed for current user check: {}", e.message)
            null
        }

    /**
     * Renders [bodyHtml] wrapped inside the platform's standardized layout shell defined by [shell].
     *
     * The [bodyHtml] is rendered as-is with no escaping; hosted apps are trusted to produce safe HTML.
     */
    fun renderShell(shell: ShellView, bodyHtml: String): String {
        val output = StringOutput()
        val content = Content { it.writeUnsafeContent(bodyHtml) }
        JteLayoutRouterGenerated.render(OwaspHtmlTemplateOutput(output), null, shell, content)
        return output.toString()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HostedAppContext::class.java)

        /**
         * Creates a [HostedAppContext] with sensible defaults for testing. Only the services that cannot be stubbed
         * without a mocking library are required; use `.copy()` on the returned value for overrides.
         */
        fun forTesting(
            renderer: TemplateRenderer,
            apiKeyService: ApiKeyService,
            oauthService: OAuthService,
            userRepository: UserRepository,
        ): HostedAppContext =
            HostedAppContext(
                renderer = renderer,
                config = AppConfig(),
                apiKeyService = apiKeyService,
                oauthService = oauthService,
                userRepository = userRepository,
                analytics = NoOpAnalyticsService(),
                notificationService = null,
                pageFactory = WebPageFactory(),
            )
    }
}

typealias PluginContext = HostedAppContext

/**
 * Single hosted-app contract. One outerstellar-platform host accepts exactly one hosted app adapter.
 *
 * The host will automatically:
 * - Include your routes in the web app when they stay inside declared ownership prefixes
 * - Run your Flyway migrations in a dedicated history table
 */
interface HostedApp : PluginMigrationSource {
    val id: String
    val appLabel: String
        get() = "Outerstellar"

    val manifest: HostedAppManifest
        get() = HostedAppManifest(id = id, appLabel = appLabel)

    val mode: PlatformMode
        get() = PlatformMode.FullPlatformApp

    val textResolver: TextResolver?
        get() = null

    fun templateOverrides(): Set<String> = emptySet()

    fun contribute(context: HostedAppContributionContext) {}

    fun routeRegistrations(context: HostedAppContext): List<PluginRouteRegistration> = emptyList()

    fun includePlatformPages(): Set<PlatformPageSets> = emptySet()

    fun layoutRenderer(context: HostedAppContext): PluginLayoutRenderer? = null

    fun filters(context: HostedAppContext): List<Filter> = emptyList()

    fun adminSections(context: HostedAppContext): List<AdminSection> = emptyList()

    fun bannerProviders(context: HostedAppContext): List<BannerProvider> = emptyList()
}
