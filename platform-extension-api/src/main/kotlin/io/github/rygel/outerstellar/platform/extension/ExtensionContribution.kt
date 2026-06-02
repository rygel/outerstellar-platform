package io.github.rygel.outerstellar.platform.extension

import io.github.rygel.outerstellar.platform.banner.BannerProvider
import io.github.rygel.outerstellar.platform.composition.PlatformMode
import io.github.rygel.outerstellar.platform.composition.RouteGroup
import io.github.rygel.outerstellar.platform.web.AdminSection
import io.github.rygel.outerstellar.platform.web.composition.PlatformPageSets
import org.http4k.core.Filter

data class ExtensionContribution(
    val mode: PlatformMode,
    val appLabel: String,
    val manifest: ExtensionManifest? = null,
    val effectiveOwnership: ExtensionOwnership? = null,
    val includedPlatformPages: Set<PlatformPageSets> = emptySet(),
    val routeRegistrations: List<ExtensionRouteRegistration> = emptyList(),
    val filters: List<Filter> = emptyList(),
    val adminSections: List<AdminSection> = emptyList(),
    val bannerProviders: List<BannerProvider> = emptyList(),
    val templateOverrides: Set<String> = emptySet(),
    val options: ExtensionOptions = ExtensionOptions(),
) {
    fun diagnostics(): ExtensionDiagnostics =
        ExtensionDiagnostics(
            extensionId = manifest?.id ?: "platform",
            appLabel = appLabel,
            version = manifest?.version ?: "dev",
            requiredPlatformVersion = manifest?.requiredPlatformVersion,
            mode = mode.name,
            capabilities =
                listOf(
                    ExtensionCapability("routes", "Routes", routeRegistrations.isNotEmpty(), routeRegistrations.size),
                    ExtensionCapability("admin", "Admin", adminSections.isNotEmpty(), adminSections.size),
                    ExtensionCapability(
                        "layout",
                        "Layout",
                        options.layoutRenderer != null,
                        booleanCount(options.layoutRenderer != null),
                    ),
                    ExtensionCapability("assets", "Assets", options.assets.hasAssets(), options.assets.count()),
                    ExtensionCapability("filters", "Filters", filters.isNotEmpty(), filters.size),
                    ExtensionCapability("banners", "Banners", bannerProviders.isNotEmpty(), bannerProviders.size),
                    ExtensionCapability(
                        "navigation",
                        "Navigation",
                        options.navItems.isNotEmpty(),
                        options.navItems.size,
                    ),
                    ExtensionCapability(
                        "platform-pages",
                        "Platform pages",
                        includedPlatformPages.isNotEmpty(),
                        includedPlatformPages.size,
                    ),
                    ExtensionCapability(
                        "text",
                        "Text resolver",
                        options.textResolver != null,
                        booleanCount(options.textResolver != null),
                    ),
                ),
            routes =
                routeRegistrations.map { registration ->
                    ExtensionRouteDiagnostic(
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
        fun from(
            extension: PlatformExtension?,
            fallbackMode: PlatformMode,
            context: ExtensionHostContext?,
        ): ExtensionContribution {
            if (extension == null || context == null) {
                return ExtensionContribution(mode = fallbackMode, appLabel = "Outerstellar")
            }

            val contributionContext = ExtensionContributionContext(host = context)
            extension.contribute(contributionContext)

            val adminSections = contributionContext.adminRegistry.snapshot()
            val manifest = extension.manifest
            val effectiveOwnership = manifest.ownership.withMode(extension.mode)
            val routeRegistrations = contributionContext.routeRegistry.snapshot()
            val assets = contributionContext.assetRegistry.snapshot()
            validateExtensionContribution(manifest, effectiveOwnership, routeRegistrations, assets)

            return ExtensionContribution(
                mode = extension.mode,
                appLabel = manifest.appLabel,
                manifest = manifest,
                effectiveOwnership = effectiveOwnership,
                includedPlatformPages = contributionContext.platformPageRegistry.snapshot(),
                routeRegistrations = routeRegistrations,
                filters = contributionContext.filterRegistry.snapshot(),
                adminSections = adminSections,
                bannerProviders = contributionContext.bannerRegistry.snapshot(),
                templateOverrides = contributionContext.templateOverrideRegistry.snapshot(),
                options =
                    ExtensionOptions(
                        navItems = contributionContext.navigationRegistry.snapshot(),
                        textResolver = extension.textResolver,
                        adminNavItems =
                            adminSections.map { section ->
                                AdminNavItem(section.navLabel, section.summaryCard.linkUrl, section.navIcon)
                            },
                        layoutRenderer = contributionContext.layoutRegistry.snapshot(),
                        assets = assets,
                    ),
            )
        }

        private fun validateExtensionContribution(
            manifest: ExtensionManifest,
            effectiveOwnership: ExtensionOwnership,
            routeRegistrations: List<ExtensionRouteRegistration>,
            assets: ExtensionAssets,
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
            manifest: ExtensionManifest,
            path: String,
            prefixes: List<String>,
            group: RouteGroup,
            subject: () -> String,
        ) {
            require(path.startsWith("/")) {
                "${subject()} must provide an absolute pathPattern starting with '/' so extension ownership can be " +
                    "validated. Example: context.routes.publicUi(route, \"Page\", \"/${manifest.id}/page\")."
            }
            require(
                prefixes.any { prefix ->
                    prefix == "/" || path == prefix || path.startsWith("$prefix/") || path.startsWith("$prefix/*")
                }
            ) {
                val rootHint =
                    if (group == RouteGroup.PublicUi || group == RouteGroup.ProtectedUi) {
                        " In ExtensionHost mode, UI routes also get '/' ownership automatically."
                    } else {
                        " Root '/' ownership is only granted to UI routes in ExtensionHost mode; API, admin, and " +
                            "static asset routes must use their explicit manifest prefixes."
                    }
                "${subject()} is outside extension '${manifest.id}' ownership for ${group.name}. " +
                    "Allowed prefixes: ${prefixes.joinToString()}. Fix the pathPattern, update " +
                    "ExtensionManifest.ownership, or register the route in the matching route group.$rootHint"
            }
        }
    }
}

data class ExtensionDiagnostics(
    val extensionId: String,
    val appLabel: String,
    val version: String,
    val requiredPlatformVersion: String?,
    val mode: String,
    val capabilities: List<ExtensionCapability>,
    val routes: List<ExtensionRouteDiagnostic>,
    val includedPlatformPages: List<String>,
    val stylesheets: List<String>,
    val scripts: List<String>,
    val ownership: ExtensionOwnershipDiagnostics?,
)

data class ExtensionCapability(val id: String, val label: String, val enabled: Boolean, val count: Int)

data class ExtensionRouteDiagnostic(
    val method: String,
    val pathPattern: String,
    val group: String,
    val description: String,
)

data class ExtensionOwnershipDiagnostics(
    val uiPrefixes: List<String>,
    val apiPrefixes: List<String>,
    val adminPrefixes: List<String>,
    val assetPrefixes: List<String>,
)

private fun ExtensionOwnership.toDiagnostics(): ExtensionOwnershipDiagnostics =
    ExtensionOwnershipDiagnostics(
        uiPrefixes = uiPrefixes,
        apiPrefixes = apiPrefixes,
        adminPrefixes = adminPrefixes,
        assetPrefixes = assetPrefixes,
    )

private fun ExtensionOwnership.withMode(mode: PlatformMode): ExtensionOwnership =
    when (mode) {
        PlatformMode.ExtensionHost -> copy(uiPrefixes = listOf("/") + uiPrefixes.filterNot { it == "/" })
        else -> this
    }

private fun ExtensionAssets.hasAssets(): Boolean = stylesheets.isNotEmpty() || scripts.isNotEmpty()

private fun ExtensionAssets.count(): Int = stylesheets.size + scripts.size

private fun booleanCount(enabled: Boolean): Int = if (enabled) 1 else 0
