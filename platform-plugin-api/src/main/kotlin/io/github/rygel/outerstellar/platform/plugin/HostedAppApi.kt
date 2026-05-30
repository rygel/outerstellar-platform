package io.github.rygel.outerstellar.platform.plugin

import gg.jte.Content
import io.github.rygel.outerstellar.platform.PluginMigrations
import io.github.rygel.outerstellar.platform.TextResolver
import io.github.rygel.outerstellar.platform.banner.BannerProvider
import io.github.rygel.outerstellar.platform.composition.PlatformMode
import io.github.rygel.outerstellar.platform.composition.RouteGroup
import io.github.rygel.outerstellar.platform.model.ApiKeySummary
import io.github.rygel.outerstellar.platform.model.CreateApiKeyResponse
import io.github.rygel.outerstellar.platform.model.NotificationSummary
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.web.AdminSection
import io.github.rygel.outerstellar.platform.web.ShellView
import io.github.rygel.outerstellar.platform.web.composition.PlatformPageSets
import java.util.UUID
import org.http4k.contract.ContractRoute
import org.http4k.core.Filter
import org.http4k.core.Request
import org.http4k.routing.RoutingHttpHandler
import org.http4k.template.TemplateRenderer

/**
 * Plugin-facing SPI for one hosted application mounted inside outerstellar-platform.
 *
 * This package is the primary import surface for hosted app authors and now lives in the standalone
 * `outerstellar-platform-plugin-api` module.
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
    val httpRoute: Any?
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
)

typealias PluginNotification = NotificationSummary

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

    fun listForUser(userId: UUID, limit: Int = 50): List<PluginNotification>

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
        fun forTesting(
            rendering: PluginRendering,
            users: PluginUsers,
            security: PluginSecurity,
            app: PluginAppInfo =
                PluginAppInfo(version = "dev", appBaseUrl = "", devMode = false, registrationEnabled = true),
            analytics: PluginAnalytics = NoOpPluginAnalytics,
            notifications: PluginNotifications? = null,
        ): HostedAppContext =
            HostedAppContext(
                app = app,
                users = users,
                analytics = analytics,
                notifications = notifications,
                rendering = rendering,
                security = security,
            )
    }
}

typealias PluginContext = HostedAppContext

private object NoOpPluginAnalytics : PluginAnalytics {
    override fun identify(userId: String, traits: Map<String, Any>) = Unit

    override fun track(userId: String, event: String, properties: Map<String, Any>) = Unit

    override fun page(userId: String, path: String) = Unit
}

/**
 * Single hosted-app contract. One outerstellar-platform host accepts exactly one hosted app adapter.
 *
 * The host will automatically:
 * - Include your routes in the web app when they stay inside declared ownership prefixes (in PluginHostedApp mode, the
 *   host also grants `/` as a default UI ownership prefix)
 * - Run your Flyway migrations in a dedicated history table
 */
interface HostedApp {
    val id: String
    val appLabel: String
        get() = "Outerstellar"

    val manifest: HostedAppManifest
        get() = HostedAppManifest(id = id, appLabel = appLabel)

    val mode: PlatformMode
        get() = PlatformMode.FullPlatformApp

    val textResolver: TextResolver?
        get() = null

    /**
     * Flyway migrations contributed by this hosted app. Return null when the hosted app does not own schema changes.
     *
     * Older plugins can keep overriding the deprecated migrationLocation/migrationHistoryTable/migrationNames
     * compatibility properties for now; the default getter adapts them into this value.
     *
     * Use [PluginMigrations.location] for the classpath location, [PluginMigrations.historyTable] for a dedicated
     * Flyway history table, and [PluginMigrations.migrationNames] when migration resources need to be enumerated
     * explicitly for packaging.
     */
    @Suppress("DEPRECATION")
    val migrations: PluginMigrations?
        get() {
            val location = migrationLocation ?: return null
            return PluginMigrations(
                location = location,
                historyTable = migrationHistoryTable,
                migrationNames = migrationNames,
            )
        }

    @Deprecated("Override migrations with PluginMigrations instead.", ReplaceWith("migrations"))
    val migrationLocation: String?
        get() = null

    @Deprecated("Override migrations with PluginMigrations instead.", ReplaceWith("migrations"))
    val migrationHistoryTable: String
        get() = io.github.rygel.outerstellar.platform.DEFAULT_PLUGIN_MIGRATION_HISTORY_TABLE

    @Deprecated("Override migrations with PluginMigrations instead.", ReplaceWith("migrations"))
    val migrationNames: List<String>
        get() = emptyList()

    fun templateOverrides(): Set<String> = emptySet()

    fun contribute(context: HostedAppContributionContext) {}

    fun routeRegistrations(context: HostedAppContext): List<PluginRouteRegistration> = emptyList()

    fun includePlatformPages(): Set<PlatformPageSets> = emptySet()

    fun layoutRenderer(context: HostedAppContext): PluginLayoutRenderer? = null

    fun filters(context: HostedAppContext): List<Filter> = emptyList()

    fun adminSections(context: HostedAppContext): List<AdminSection> = emptyList()

    fun bannerProviders(context: HostedAppContext): List<BannerProvider> = emptyList()
}
