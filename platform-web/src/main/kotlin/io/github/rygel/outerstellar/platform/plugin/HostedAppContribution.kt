package io.github.rygel.outerstellar.platform.plugin

import io.github.rygel.outerstellar.platform.banner.BannerProvider
import io.github.rygel.outerstellar.platform.composition.PlatformMode
import io.github.rygel.outerstellar.platform.composition.RouteGroup
import io.github.rygel.outerstellar.platform.web.AdminSection
import io.github.rygel.outerstellar.platform.web.composition.PlatformPageSets
import org.http4k.core.Filter

data class HostedAppContribution(
    val mode: PlatformMode,
    val appLabel: String,
    val manifest: HostedAppManifest? = null,
    val includedPlatformPages: Set<PlatformPageSets> = emptySet(),
    val routeRegistrations: List<PluginRouteRegistration> = emptyList(),
    val filters: List<Filter> = emptyList(),
    val adminSections: List<AdminSection> = emptyList(),
    val bannerProviders: List<BannerProvider> = emptyList(),
    val options: PluginOptions = PluginOptions(),
) {
    fun diagnostics(): HostedAppDiagnostics =
        HostedAppDiagnostics(
            hostedAppId = manifest?.id ?: "platform",
            appLabel = appLabel,
            version = manifest?.version ?: "dev",
            requiredPlatformVersion = manifest?.requiredPlatformVersion,
            mode = mode.name,
            capabilities =
                listOf(
                    HostedAppCapability("routes", "Routes", routeRegistrations.isNotEmpty(), routeRegistrations.size),
                    HostedAppCapability("admin", "Admin", adminSections.isNotEmpty(), adminSections.size),
                    HostedAppCapability(
                        "layout",
                        "Layout",
                        options.layoutRenderer != null,
                        booleanCount(options.layoutRenderer != null),
                    ),
                    HostedAppCapability("assets", "Assets", options.assets.hasAssets(), options.assets.count()),
                    HostedAppCapability("filters", "Filters", filters.isNotEmpty(), filters.size),
                    HostedAppCapability("banners", "Banners", bannerProviders.isNotEmpty(), bannerProviders.size),
                    HostedAppCapability(
                        "navigation",
                        "Navigation",
                        options.navItems.isNotEmpty(),
                        options.navItems.size,
                    ),
                    HostedAppCapability(
                        "platform-pages",
                        "Platform pages",
                        includedPlatformPages.isNotEmpty(),
                        includedPlatformPages.size,
                    ),
                    HostedAppCapability(
                        "text",
                        "Text resolver",
                        options.textResolver != null,
                        booleanCount(options.textResolver != null),
                    ),
                ),
            routes =
                routeRegistrations.map { registration ->
                    HostedAppRouteDiagnostic(
                        method = registration.method,
                        pathPattern = registration.pathPattern,
                        group = registration.group.name,
                        description = registration.description,
                    )
                },
            includedPlatformPages = includedPlatformPages.map { it.pageSet.id }.sorted(),
            stylesheets = options.assets.stylesheets,
            scripts = options.assets.scripts,
            ownership = manifest?.ownership?.toDiagnostics(),
        )

    companion object {
        fun from(plugin: HostedApp?, fallbackMode: PlatformMode, context: HostedAppContext?): HostedAppContribution {
            if (plugin == null || context == null) {
                return HostedAppContribution(mode = fallbackMode, appLabel = "Outerstellar")
            }

            val contributionContext = HostedAppContributionContext(host = context)
            contributionContext.platformPages.includeAll(plugin.includePlatformPages())
            plugin.routeRegistrations(context).forEach(contributionContext.routes::register)
            contributionContext.filters.addAll(plugin.filters(context))
            contributionContext.admin.sections(plugin.adminSections(context))
            contributionContext.banners.providers(plugin.bannerProviders(context))
            plugin.layoutRenderer(context)?.let(contributionContext.layout::replaceWith)

            plugin.contribute(contributionContext)

            val adminSections = contributionContext.adminRegistry.snapshot()
            val manifest = plugin.manifest
            val routeRegistrations = contributionContext.routeRegistry.snapshot()
            val assets = contributionContext.assetRegistry.snapshot()
            validateHostedAppContribution(manifest, routeRegistrations, assets)

            return HostedAppContribution(
                mode = plugin.mode,
                appLabel = manifest.appLabel,
                manifest = manifest,
                includedPlatformPages = contributionContext.platformPageRegistry.snapshot(),
                routeRegistrations = routeRegistrations,
                filters = contributionContext.filterRegistry.snapshot(),
                adminSections = adminSections,
                bannerProviders = contributionContext.bannerRegistry.snapshot(),
                options =
                    PluginOptions(
                        navItems = contributionContext.navigationRegistry.snapshot(),
                        textResolver = plugin.textResolver,
                        adminNavItems =
                            adminSections.map { section ->
                                AdminNavItem(section.navLabel, section.summaryCard.linkUrl, section.navIcon)
                            },
                        layoutRenderer = contributionContext.layoutRegistry.snapshot(),
                        assets = assets,
                    ),
            )
        }

        private fun validateHostedAppContribution(
            manifest: HostedAppManifest,
            routeRegistrations: List<PluginRouteRegistration>,
            assets: PluginAssets,
        ) {
            routeRegistrations.forEach { registration ->
                val prefixes =
                    when (registration.group) {
                        RouteGroup.Api -> manifest.ownership.apiPrefixes
                        RouteGroup.Admin -> manifest.ownership.adminPrefixes
                        RouteGroup.Static -> manifest.ownership.assetPrefixes
                        else -> manifest.ownership.uiPrefixes
                    }
                requirePathOwnedByManifest(manifest, registration.pathPattern, prefixes) {
                    "Route ${registration.method} ${registration.pathPattern} (${registration.description})"
                }
            }

            (assets.stylesheets + assets.scripts).forEach { asset ->
                requirePathOwnedByManifest(manifest, asset, manifest.ownership.assetPrefixes) { "Asset $asset" }
            }
        }

        private fun requirePathOwnedByManifest(
            manifest: HostedAppManifest,
            path: String,
            prefixes: List<String>,
            subject: () -> String,
        ) {
            require(path.startsWith("/")) {
                "${subject()} must provide a pathPattern starting with '/' so hosted app ownership can be validated."
            }
            require(
                prefixes.any { prefix -> path == prefix || path.startsWith("$prefix/") || path.startsWith("$prefix/*") }
            ) {
                "${subject()} is outside hosted app '${manifest.id}' ownership. Allowed prefixes: ${prefixes.joinToString()}"
            }
        }
    }
}

typealias PluginContribution = HostedAppContribution

data class HostedAppDiagnostics(
    val hostedAppId: String,
    val appLabel: String,
    val version: String,
    val requiredPlatformVersion: String?,
    val mode: String,
    val capabilities: List<HostedAppCapability>,
    val routes: List<HostedAppRouteDiagnostic>,
    val includedPlatformPages: List<String>,
    val stylesheets: List<String>,
    val scripts: List<String>,
    val ownership: HostedAppOwnershipDiagnostics?,
)

data class HostedAppCapability(val id: String, val label: String, val enabled: Boolean, val count: Int)

data class HostedAppRouteDiagnostic(
    val method: String,
    val pathPattern: String,
    val group: String,
    val description: String,
)

data class HostedAppOwnershipDiagnostics(
    val uiPrefixes: List<String>,
    val apiPrefixes: List<String>,
    val adminPrefixes: List<String>,
    val assetPrefixes: List<String>,
)

private fun HostedAppOwnership.toDiagnostics(): HostedAppOwnershipDiagnostics =
    HostedAppOwnershipDiagnostics(
        uiPrefixes = uiPrefixes,
        apiPrefixes = apiPrefixes,
        adminPrefixes = adminPrefixes,
        assetPrefixes = assetPrefixes,
    )

private fun PluginAssets.hasAssets(): Boolean = stylesheets.isNotEmpty() || scripts.isNotEmpty()

private fun PluginAssets.count(): Int = stylesheets.size + scripts.size

private fun booleanCount(enabled: Boolean): Int = if (enabled) 1 else 0
