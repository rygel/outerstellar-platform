package io.github.rygel.outerstellar.platform.extension

import gg.jte.Content
import io.github.rygel.outerstellar.platform.ExtensionMigrations
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
 * Extension-facing SPI for one extension mounted inside outerstellar-platform.
 *
 * This package is the primary import surface for extension authors and now lives in the standalone
 * `outerstellar-platform-extension-api` module.
 */

/**
 * Nav item contributed by an extension to the shell navigation bar. [activeSection] defaults to [url] and is compared
 * against the current path to highlight the active link.
 */
data class ExtensionNavItem(val label: String, val url: String, val icon: String, val activeSection: String = url)

data class AdminNavItem(val label: String, val url: String, val icon: String)

data class ExtensionManifest(
    val id: String,
    val appLabel: String = "Outerstellar",
    val version: String = "dev",
    val requiredPlatformVersion: String? = null,
    val ownership: ExtensionOwnership = ExtensionOwnership.forExtension(id),
)

data class ExtensionOwnership(
    val uiPrefixes: List<String>,
    val apiPrefixes: List<String>,
    val adminPrefixes: List<String>,
    val assetPrefixes: List<String>,
) {
    companion object {
        fun forExtension(id: String): ExtensionOwnership =
            ExtensionOwnership(
                uiPrefixes = listOf("/$id", "/extension/$id"),
                apiPrefixes = listOf("/api/$id", "/api/extension/$id", "/api/v1/$id", "/api/v1/extension/$id"),
                adminPrefixes = listOf("/admin/$id"),
                assetPrefixes = listOf("/extensions/$id/assets"),
            )
    }
}

data class ExtensionRouteRegistration(
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
        ): ExtensionRouteRegistration =
            ExtensionRouteRegistration(
                route = null,
                group = RouteGroup.Static,
                description = description,
                pathPattern = pathPattern,
                method = method,
                staticRoute = route,
            )
    }
}

data class ExtensionAssets(val stylesheets: List<String> = emptyList(), val scripts: List<String> = emptyList())

fun interface ExtensionLayoutRenderer {
    fun render(shell: ShellView, content: Content): Content
}

data class ExtensionOptions(
    val navItems: List<ExtensionNavItem> = emptyList(),
    val textResolver: TextResolver? = null,
    val adminNavItems: List<AdminNavItem> = emptyList(),
    val layoutRenderer: ExtensionLayoutRenderer? = null,
    val assets: ExtensionAssets = ExtensionAssets(),
)

data class HostAppInfo(
    val version: String,
    val appBaseUrl: String,
    val devMode: Boolean,
    val registrationEnabled: Boolean,
)

typealias HostNotification = NotificationSummary

interface HostUsers {
    fun currentUser(request: Request): User?

    fun findById(id: UUID): User?

    fun findByUsername(username: String): User?

    fun findByEmail(email: String): User?
}

interface HostAnalytics {
    fun identify(userId: String, traits: Map<String, Any> = emptyMap())

    fun track(userId: String, event: String, properties: Map<String, Any> = emptyMap())

    fun page(userId: String, path: String)
}

interface HostNotifications {
    fun create(userId: UUID, title: String, body: String, type: String = "info")

    fun listForUser(userId: UUID, limit: Int = 50): List<HostNotification>

    fun countUnread(userId: UUID): Int

    fun markRead(id: UUID, userId: UUID)

    fun markAllRead(userId: UUID)

    fun delete(id: UUID, userId: UUID)
}

interface HostRendering {
    val renderer: TemplateRenderer

    fun renderShell(shell: ShellView, bodyHtml: String): String
}

interface HostApiKeys {
    fun createApiKey(userId: UUID, name: String): CreateApiKeyResponse

    fun listApiKeys(userId: UUID): List<ApiKeySummary>

    fun deleteApiKey(userId: UUID, keyId: Long)
}

interface HostOAuth {
    fun findOrCreateOAuthUser(providerName: String, oauthSubject: String, email: String?): User
}

data class HostSecurity(val apiKeys: HostApiKeys, val oauth: HostOAuth)

/**
 * Host services provided to the extension when it builds its routes. The extension should depend only on this context
 * rather than injecting host services directly.
 */
class ExtensionHostContext(
    val app: HostAppInfo,
    val users: HostUsers,
    val analytics: HostAnalytics,
    val notifications: HostNotifications?,
    val rendering: HostRendering,
    val security: HostSecurity,
) {
    @Deprecated("Use rendering.renderer", ReplaceWith("rendering.renderer"))
    val renderer: TemplateRenderer
        get() = rendering.renderer

    @Deprecated("Use app", ReplaceWith("app"))
    val config: HostAppInfo
        get() = app

    @Deprecated("Use security.apiKeys", ReplaceWith("security.apiKeys"))
    val apiKeyService: HostApiKeys
        get() = security.apiKeys

    @Deprecated("Use security.oauth", ReplaceWith("security.oauth"))
    val oauthService: HostOAuth
        get() = security.oauth

    @Deprecated("Use users", ReplaceWith("users"))
    val userRepository: HostUsers
        get() = users

    @Deprecated("Use notifications", ReplaceWith("notifications"))
    val notificationService: HostNotifications?
        get() = notifications

    /** Returns the authenticated user for this request, or null if no user is logged in. */
    fun currentUser(request: Request): User? = users.currentUser(request)

    /**
     * Renders [bodyHtml] wrapped inside the platform's standardized layout shell defined by [shell].
     *
     * The [bodyHtml] is rendered as-is with no escaping; extensions are trusted to produce safe HTML.
     */
    fun renderShell(shell: ShellView, bodyHtml: String): String = rendering.renderShell(shell, bodyHtml)

    companion object {
        fun forTesting(
            rendering: HostRendering,
            users: HostUsers,
            security: HostSecurity,
            app: HostAppInfo =
                HostAppInfo(version = "dev", appBaseUrl = "", devMode = false, registrationEnabled = true),
            analytics: HostAnalytics = NoOpHostAnalytics,
            notifications: HostNotifications? = null,
        ): ExtensionHostContext =
            ExtensionHostContext(
                app = app,
                users = users,
                analytics = analytics,
                notifications = notifications,
                rendering = rendering,
                security = security,
            )
    }
}

typealias ExtensionContext = ExtensionHostContext

private object NoOpHostAnalytics : HostAnalytics {
    override fun identify(userId: String, traits: Map<String, Any>) = Unit

    override fun track(userId: String, event: String, properties: Map<String, Any>) = Unit

    override fun page(userId: String, path: String) = Unit
}

/**
 * Single extension contract. One outerstellar-platform host accepts exactly one extension adapter.
 *
 * The host will automatically:
 * - Include your routes in the web app when they stay inside declared ownership prefixes (in ExtensionHost mode, the
 *   host also grants `/` as a default UI ownership prefix)
 * - Run your Flyway migrations in a dedicated history table
 */
interface PlatformExtension {
    val id: String
    val appLabel: String
        get() = "Outerstellar"

    val manifest: ExtensionManifest
        get() = ExtensionManifest(id = id, appLabel = appLabel)

    val mode: PlatformMode
        get() = PlatformMode.FullPlatform

    val textResolver: TextResolver?
        get() = null

    /**
     * Flyway migrations contributed by this extension. Return null when the extension does not own schema changes.
     *
     * Older extensions can keep overriding the deprecated migrationLocation/migrationHistoryTable/migrationNames
     * compatibility properties for now; the default getter adapts them into this value.
     *
     * Use [ExtensionMigrations.location] for the classpath location, [ExtensionMigrations.historyTable] for a dedicated
     * Flyway history table, and [ExtensionMigrations.migrationNames] when migration resources need to be enumerated
     * explicitly for packaging.
     */
    @Suppress("DEPRECATION")
    val migrations: ExtensionMigrations?
        get() {
            val location = migrationLocation ?: return null
            return ExtensionMigrations(
                location = location,
                historyTable = migrationHistoryTable,
                migrationNames = migrationNames,
            )
        }

    @Deprecated("Override migrations with ExtensionMigrations instead.", ReplaceWith("migrations"))
    val migrationLocation: String?
        get() = null

    @Deprecated("Override migrations with ExtensionMigrations instead.", ReplaceWith("migrations"))
    val migrationHistoryTable: String
        get() = io.github.rygel.outerstellar.platform.DEFAULT_EXTENSION_MIGRATION_HISTORY_TABLE

    @Deprecated("Override migrations with ExtensionMigrations instead.", ReplaceWith("migrations"))
    val migrationNames: List<String>
        get() = emptyList()

    fun templateOverrides(): Set<String> = emptySet()

    fun contribute(context: ExtensionContributionContext) {}

    fun routeRegistrations(context: ExtensionHostContext): List<ExtensionRouteRegistration> = emptyList()

    fun includePlatformPages(): Set<PlatformPageSets> = emptySet()

    fun layoutRenderer(context: ExtensionHostContext): ExtensionLayoutRenderer? = null

    fun filters(context: ExtensionHostContext): List<Filter> = emptyList()

    fun adminSections(context: ExtensionHostContext): List<AdminSection> = emptyList()

    fun bannerProviders(context: ExtensionHostContext): List<BannerProvider> = emptyList()
}
