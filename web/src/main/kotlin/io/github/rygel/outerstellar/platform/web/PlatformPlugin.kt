package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.PluginMigrationSource
import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.UserRepository
import io.github.rygel.outerstellar.platform.service.NotificationService
import org.http4k.contract.ContractRoute
import org.http4k.template.TemplateRenderer
import org.koin.core.module.Module

/**
 * Nav item contributed by a plugin to the shell navigation bar. [activeSection] defaults to [url]
 * and is compared against the current path to highlight the active link (the same convention used
 * by the host's built-in nav links).
 */
data class PluginNavItem(
    val label: String,
    val url: String,
    val icon: String,
    val activeSection: String = url,
)

/**
 * Host services provided to the plugin when it builds its routes. The plugin should depend only on
 * this context rather than injecting host services directly.
 */
data class PluginContext(
    val renderer: TemplateRenderer,
    val config: AppConfig,
    val securityService: SecurityService,
    val userRepository: UserRepository,
    val analytics: AnalyticsService,
    val notificationService: NotificationService?,
    val pageFactory: WebPageFactory,
)

/**
 * Single-plugin contract. One outerstellar-platform host accepts exactly one plugin.
 *
 * Implement this interface and register it as a Koin `single` in your plugin's Koin module. The
 * host will automatically:
 * - Include your routes in the web app
 * - Inject your nav items into the shell nav bar (replacing the defaults when non-empty)
 * - Run your Flyway migrations in a dedicated history table
 *
 * Register your Koin module by passing it to `startKoin` **before** the host modules are resolved:
 * ```kotlin
 * startKoin { modules(myPluginModule, persistenceModule, coreModule, webModule, securityModule) }
 * ```
 */
interface PlatformPlugin : PluginMigrationSource {
    /** Unique identifier for this plugin (used in logging). */
    val id: String

    /** Label used in OpenAPI info and branding. Defaults to "Outerstellar". */
    val appLabel: String
        get() = "Outerstellar"

    /**
     * Default route paths to exclude from the host app (e.g. `setOf("/contacts", "/")`). Routes
     * whose path is in this set will not be registered by the host.
     */
    val excludeDefaultRoutes: Set<String>
        get() = emptySet()

    /**
     * Navigation items shown in the shell sidebar. When non-empty these **replace** the host's
     * default nav links. When empty the host's default nav links are shown unchanged.
     */
    val navItems: List<PluginNavItem>
        get() = emptyList()

    /** HTTP routes contributed to the application. */
    fun routes(context: PluginContext): List<ContractRoute> = emptyList()

    /** Koin modules this plugin needs. Register in `startKoin` rather than relying on this. */
    fun koinModules(): List<Module> = emptyList()
}
