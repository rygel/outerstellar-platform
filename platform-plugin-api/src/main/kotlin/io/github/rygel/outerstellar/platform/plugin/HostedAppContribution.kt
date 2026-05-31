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
    val effectiveOwnership: HostedAppOwnership? = null,
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
            ownership = effectiveOwnership?.toDiagnostics(),
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
            val effectiveOwnership = manifest.ownership.withMode(plugin.mode)
            val routeRegistrations = contributionContext.routeRegistry.snapshot()
            val assets = contributionContext.assetRegistry.snapshot()
            validateHostedAppContribution(manifest, effectiveOwnership, routeRegistrations, assets)

            return HostedAppContribution(
                mode = plugin.mode,
                appLabel = manifest.appLabel,
                manifest = manifest,
                effectiveOwnership = effectiveOwnership,
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
            effectiveOwnership: HostedAppOwnership,
            routeRegistrations: List<PluginRouteRegistration>,
            assets: PluginAssets,
        ) {
            routeRegistrations.forEach { registration ->
                val prefixes =
                    when (registration.group) {
                        RouteGroup.Api -> effectiveOwnership.apiPrefixes
                        RouteGroup.Admin -> effectiveOwnership.adminPrefixes
                        RouteGroup.Static -> effectiveOwnership.assetPrefixes
                        else -> effectiveOwnership.uiPrefixes
                    }
                requirePathOwnedByManifest(manifest, registration.pathPattern, prefixes, registration.group) {
                    "Route ${registration.method} ${registration.pathPattern} (${registration.description})"
                }
            }

            (assets.stylesheets + assets.scripts).forEach { asset ->
                requirePathOwnedByManifest(manifest, asset, effectiveOwnership.assetPrefixes, RouteGroup.Static) {
                    "Asset $asset"
                }
            }
        }

        private fun requirePathOwnedByManifest(
            manifest: HostedAppManifest,
            path: String,
            prefixes: List<String>,
            group: RouteGroup,
            subject: () -> String,
        ) {
            require(path.startsWith("/")) {
                "${subject()} must provide an absolute pathPattern starting with '/' so hosted app ownership can be " +
                    "validated. Example: context.routes.publicUi(route, \"Page\", \"/${manifest.id}/page\")."
            }
            require(
                prefixes.any { prefix ->
                    prefix == "/" || path == prefix || path.startsWith("$prefix/") || path.startsWith("$prefix/*")
                }
            ) {
                val rootHint =
                    if (group == RouteGroup.PublicUi || group == RouteGroup.ProtectedUi) {
                        " In PluginHostedApp mode, UI routes also get '/' ownership automatically."
                    } else {
                        " Root '/' ownership is only granted to UI routes in PluginHostedApp mode; API, admin, and " +
                            "static asset routes must use their explicit manifest prefixes."
                    }
                "${subject()} is outside hosted app '${manifest.id}' ownership for ${group.name}. " +
                    "Allowed prefixes: ${prefixes.joinToString()}. Fix the pathPattern, update " +
                    "HostedAppManifest.ownership, or register the route in the matching route group.$rootHint"
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

private fun HostedAppOwnership.withMode(mode: PlatformMode): HostedAppOwnership =
    when (mode) {
        PlatformMode.PluginHostedApp -> copy(uiPrefixes = listOf("/") + uiPrefixes.filterNot { it == "/" })
        else -> this
    }

private fun PluginAssets.hasAssets(): Boolean = stylesheets.isNotEmpty() || scripts.isNotEmpty()

private fun PluginAssets.count(): Int = stylesheets.size + scripts.size

private fun booleanCount(enabled: Boolean): Int = if (enabled) 1 else 0
