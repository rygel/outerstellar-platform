package dev.outerstellar.platform.web

import dev.outerstellar.platform.AppConfig
import dev.outerstellar.platform.PluginMigrationSource
import dev.outerstellar.platform.analytics.AnalyticsService
import dev.outerstellar.platform.security.SecurityService
import dev.outerstellar.platform.security.UserRepository
import dev.outerstellar.platform.service.NotificationService
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
)

/**
 * Single-plugin contract. One outerstellar-starter host accepts exactly one plugin.
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
interface StarterPlugin : PluginMigrationSource {
    /** Unique identifier for this plugin (used in logging). */
    val id: String

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
