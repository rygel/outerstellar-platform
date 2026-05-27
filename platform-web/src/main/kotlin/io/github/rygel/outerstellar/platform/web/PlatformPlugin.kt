package io.github.rygel.outerstellar.platform.web

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
import io.github.rygel.outerstellar.platform.web.composition.PlatformPageSets
import org.http4k.contract.ContractRoute
import org.http4k.core.Filter
import org.http4k.core.Request
import org.http4k.lens.LensFailure
import org.http4k.template.TemplateRenderer
import org.slf4j.LoggerFactory

/**
 * Nav item contributed by a plugin to the shell navigation bar. [activeSection] defaults to [url] and is compared
 * against the current path to highlight the active link (the same convention used by the host's built-in nav links).
 */
data class PluginNavItem(val label: String, val url: String, val icon: String, val activeSection: String = url)

data class AdminNavItem(val label: String, val url: String, val icon: String)

data class PluginRouteRegistration(val route: ContractRoute, val group: RouteGroup, val description: String)

data class PluginOptions(
    val navItems: List<PluginNavItem> = emptyList(),
    val textResolver: TextResolver? = null,
    val adminNavItems: List<AdminNavItem> = emptyList(),
)

/**
 * Host services provided to the plugin when it builds its routes. The plugin should depend only on this context rather
 * than injecting host services directly.
 */
data class PluginContext(
    val renderer: TemplateRenderer,
    val config: AppConfig,
    val apiKeyService: ApiKeyService,
    val oauthService: OAuthService,
    val userRepository: UserRepository,
    val analytics: AnalyticsService,
    val notificationService: NotificationService?,
    val pageFactory: WebPageFactory,
) {
    /**
     * Returns the authenticated user for this request, or null if no user is logged in. This is a convenience wrapper
     * around [SecurityRules.USER_KEY] that handles the lens failure gracefully.
     */
    fun currentUser(request: Request): User? =
        try {
            request.requestContext.user
        } catch (e: LensFailure) {
            logger.debug("Lens extraction failed for current user check: {}", e.message)
            null
        }

    /**
     * Renders [bodyHtml] wrapped inside the platform's standardized layout shell defined by [shell]. The [ShellView] is
     * obtained from [ShellRenderer.shell()].
     *
     * Usage:
     * ```kotlin
     * val shell = request.shellRenderer.shell("Page Title", "/section")
     * val html = context.renderShell(shell, "<div>...</div>")
     * ```
     *
     * The [bodyHtml] is rendered as-is with no escaping — plugins are trusted to produce safe HTML.
     */
    fun renderShell(shell: ShellView, bodyHtml: String): String {
        val output = StringOutput()
        val content = Content { it.writeUnsafeContent(bodyHtml) }
        JteLayoutRouterGenerated.render(OwaspHtmlTemplateOutput(output), null, shell, content)
        return output.toString()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PluginContext::class.java)

        /**
         * Creates a [PluginContext] with sensible defaults for testing. Only the three services that cannot be stubbed
         * without a mocking library are required — use `mockk(relaxed = true)` for them.
         *
         * To override other defaults, use `.copy()` on the returned instance:
         * ```kotlin
         * val ctx = PluginContext.forTesting(renderer, mockk(relaxed = true), mockk(relaxed = true))
         *     .copy(config = AppConfig(devMode = true))
         * ```
         */
        fun forTesting(
            renderer: TemplateRenderer,
            apiKeyService: ApiKeyService,
            oauthService: OAuthService,
            userRepository: UserRepository,
        ): PluginContext =
            PluginContext(
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

/**
 * Single-plugin contract. One outerstellar-platform host accepts exactly one plugin.
 *
 * Implement this interface and pass your plugin instance to [createServerComponents]:
 * ```kotlin
 * val components = createServerComponents(plugin = MyPlugin())
 * ```
 *
 * The host will automatically:
 * - Include your routes in the web app
 * - Run your Flyway migrations in a dedicated history table
 */
interface PlatformPlugin : PluginMigrationSource {
    val id: String
    val appLabel: String
        get() = "Outerstellar"

    val mode: PlatformMode
        get() = PlatformMode.FullPlatformApp

    val textResolver: TextResolver?
        get() = null

    fun templateOverrides(): Set<String> = emptySet()

    fun routeRegistrations(context: PluginContext): List<PluginRouteRegistration> = emptyList()

    fun mountPlatformPages(): Set<PlatformPageSets> = emptySet()

    fun filters(context: PluginContext): List<Filter> = emptyList()

    fun adminSections(context: PluginContext): List<AdminSection> = emptyList()

    fun bannerProviders(context: PluginContext): List<BannerProvider> = emptyList()
}
