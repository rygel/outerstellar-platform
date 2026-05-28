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
import io.github.rygel.outerstellar.platform.model.ApiKeySummary
import io.github.rygel.outerstellar.platform.model.CreateApiKeyResponse
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.persistence.Notification
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.security.ApiKeyService
import io.github.rygel.outerstellar.platform.security.OAuthService
import io.github.rygel.outerstellar.platform.service.NotificationService
import io.github.rygel.outerstellar.platform.web.AdminSection
import io.github.rygel.outerstellar.platform.web.ShellView
import io.github.rygel.outerstellar.platform.web.composition.PlatformPageSets
import io.github.rygel.outerstellar.platform.web.requestContext
import java.util.UUID
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

data class PluginAppInfo(
    val version: String,
    val appBaseUrl: String,
    val devMode: Boolean,
    val registrationEnabled: Boolean,
) {
    companion object {
        fun from(config: AppConfig): PluginAppInfo =
            PluginAppInfo(
                version = config.version,
                appBaseUrl = config.appBaseUrl,
                devMode = config.devMode,
                registrationEnabled = config.registrationEnabled,
            )
    }
}

interface PluginUsers {
    fun currentUser(request: Request): User?

    fun findById(id: UUID): User?

    fun findByUsername(username: String): User?

    fun findByEmail(email: String): User?
}

interface PluginAnalytics {
    fun identify(userId: String, traits: Map<String, Any> = emptyMap())

    fun track(userId: String, event: String, properties: Map<String, Any> = emptyMap())

    fun page(userId: String, path: String)
}

interface PluginNotifications {
    fun create(userId: UUID, title: String, body: String, type: String = "info")

    fun listForUser(userId: UUID, limit: Int = 50): List<Notification>

    fun countUnread(userId: UUID): Int

    fun markRead(id: UUID, userId: UUID)

    fun markAllRead(userId: UUID)

    fun delete(id: UUID, userId: UUID)
}

interface PluginRendering {
    val renderer: TemplateRenderer

    fun renderShell(shell: ShellView, bodyHtml: String): String
}

interface PluginApiKeys {
    fun createApiKey(userId: UUID, name: String): CreateApiKeyResponse

    fun listApiKeys(userId: UUID): List<ApiKeySummary>

    fun deleteApiKey(userId: UUID, keyId: Long)
}

interface PluginOAuth {
    fun findOrCreateOAuthUser(providerName: String, oauthSubject: String, email: String?): User
}

data class PluginSecurity(val apiKeys: PluginApiKeys, val oauth: PluginOAuth)

/**
 * Host services provided to the plugin when it builds its routes. The plugin should depend only on this context rather
 * than injecting host services directly.
 */
class HostedAppContext(
    val app: PluginAppInfo,
    val users: PluginUsers,
    val analytics: PluginAnalytics,
    val notifications: PluginNotifications?,
    val rendering: PluginRendering,
    val security: PluginSecurity,
) {
    @Deprecated("Use rendering.renderer", ReplaceWith("rendering.renderer"))
    val renderer: TemplateRenderer
        get() = rendering.renderer

    @Deprecated("Use app", ReplaceWith("app"))
    val config: PluginAppInfo
        get() = app

    @Deprecated("Use security.apiKeys", ReplaceWith("security.apiKeys"))
    val apiKeyService: PluginApiKeys
        get() = security.apiKeys

    @Deprecated("Use security.oauth", ReplaceWith("security.oauth"))
    val oauthService: PluginOAuth
        get() = security.oauth

    @Deprecated("Use users", ReplaceWith("users"))
    val userRepository: PluginUsers
        get() = users

    @Deprecated("Use notifications", ReplaceWith("notifications"))
    val notificationService: PluginNotifications?
        get() = notifications

    /** Returns the authenticated user for this request, or null if no user is logged in. */
    fun currentUser(request: Request): User? = users.currentUser(request)

    /**
     * Renders [bodyHtml] wrapped inside the platform's standardized layout shell defined by [shell].
     *
     * The [bodyHtml] is rendered as-is with no escaping; hosted apps are trusted to produce safe HTML.
     */
    fun renderShell(shell: ShellView, bodyHtml: String): String = rendering.renderShell(shell, bodyHtml)

    companion object {
        internal fun fromHostServices(
            renderer: TemplateRenderer,
            config: AppConfig,
            apiKeyService: ApiKeyService,
            oauthService: OAuthService,
            userRepository: UserRepository,
            analytics: AnalyticsService,
            notificationService: NotificationService?,
        ): HostedAppContext =
            HostedAppContext(
                app = PluginAppInfo.from(config),
                users = DefaultPluginUsers(userRepository),
                analytics = DefaultPluginAnalytics(analytics),
                notifications = notificationService?.let(::DefaultPluginNotifications),
                rendering = DefaultPluginRendering(renderer),
                security = PluginSecurity(DefaultPluginApiKeys(apiKeyService), DefaultPluginOAuth(oauthService)),
            )

        /**
         * Creates a [HostedAppContext] with sensible defaults for testing. Only the services that cannot be stubbed
         * without a mocking library are required; use `.copy()` on the returned value for overrides.
         */
        fun forTesting(
            renderer: TemplateRenderer,
            apiKeyService: ApiKeyService,
            oauthService: OAuthService,
            userRepository: UserRepository,
            config: AppConfig = AppConfig(),
            analyticsService: AnalyticsService = NoOpAnalyticsService(),
            notificationService: NotificationService? = null,
        ): HostedAppContext =
            fromHostServices(
                renderer = renderer,
                config = config,
                apiKeyService = apiKeyService,
                oauthService = oauthService,
                userRepository = userRepository,
                analytics = analyticsService,
                notificationService = notificationService,
            )
    }
}

typealias PluginContext = HostedAppContext

private val hostedAppContextLogger = LoggerFactory.getLogger(HostedAppContext::class.java)

private class DefaultPluginUsers(private val userRepository: UserRepository) : PluginUsers {
    override fun currentUser(request: Request): User? =
        try {
            request.requestContext.user
        } catch (e: LensFailure) {
            hostedAppContextLogger.debug("Lens extraction failed for current user check: {}", e.message)
            null
        }

    override fun findById(id: UUID): User? = userRepository.findById(id)

    override fun findByUsername(username: String): User? = userRepository.findByUsername(username)

    override fun findByEmail(email: String): User? = userRepository.findByEmail(email)
}

private class DefaultPluginAnalytics(private val analyticsService: AnalyticsService) : PluginAnalytics {
    override fun identify(userId: String, traits: Map<String, Any>) = analyticsService.identify(userId, traits)

    override fun track(userId: String, event: String, properties: Map<String, Any>) =
        analyticsService.track(userId, event, properties)

    override fun page(userId: String, path: String) = analyticsService.page(userId, path)
}

private class DefaultPluginNotifications(private val notificationService: NotificationService) : PluginNotifications {
    override fun create(userId: UUID, title: String, body: String, type: String) =
        notificationService.create(userId, title, body, type)

    override fun listForUser(userId: UUID, limit: Int): List<Notification> =
        notificationService.listForUser(userId, limit)

    override fun countUnread(userId: UUID): Int = notificationService.countUnread(userId)

    override fun markRead(id: UUID, userId: UUID) = notificationService.markRead(id, userId)

    override fun markAllRead(userId: UUID) = notificationService.markAllRead(userId)

    override fun delete(id: UUID, userId: UUID) = notificationService.delete(id, userId)
}

private class DefaultPluginRendering(override val renderer: TemplateRenderer) : PluginRendering {
    override fun renderShell(shell: ShellView, bodyHtml: String): String {
        val output = StringOutput()
        val content = Content { it.writeUnsafeContent(bodyHtml) }
        JteLayoutRouterGenerated.render(OwaspHtmlTemplateOutput(output), null, shell, content)
        return output.toString()
    }
}

private class DefaultPluginApiKeys(private val apiKeyService: ApiKeyService) : PluginApiKeys {
    override fun createApiKey(userId: UUID, name: String): CreateApiKeyResponse =
        apiKeyService.createApiKey(userId, name)

    override fun listApiKeys(userId: UUID): List<ApiKeySummary> = apiKeyService.listApiKeys(userId)

    override fun deleteApiKey(userId: UUID, keyId: Long) = apiKeyService.deleteApiKey(userId, keyId)
}

private class DefaultPluginOAuth(private val oauthService: OAuthService) : PluginOAuth {
    override fun findOrCreateOAuthUser(providerName: String, oauthSubject: String, email: String?): User =
        oauthService.findOrCreateOAuthUser(providerName, oauthSubject, email)
}

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
