package io.github.rygel.outerstellar.platform.extension

import io.github.rygel.outerstellar.platform.banner.BannerProvider
import io.github.rygel.outerstellar.platform.composition.RouteGroup
import io.github.rygel.outerstellar.platform.web.AdminMetric
import io.github.rygel.outerstellar.platform.web.AdminSection
import io.github.rygel.outerstellar.platform.web.AdminSummaryCard
import io.github.rygel.outerstellar.platform.web.composition.PlatformPageSets
import org.http4k.contract.ContractRoute
import org.http4k.core.Filter
import org.http4k.routing.ResourceLoader
import org.http4k.routing.bind
import org.http4k.routing.static

class ExtensionContributionContext
internal constructor(
    val host: ExtensionHostContext,
    internal val routeRegistry: ExtensionRouteContributionRegistry = ExtensionRouteContributionRegistry(),
    internal val platformPageRegistry: PlatformPageContributionRegistry = PlatformPageContributionRegistry(),
    internal val filterRegistry: ExtensionFilterContributionRegistry = ExtensionFilterContributionRegistry(),
    internal val adminRegistry: ExtensionAdminContributionRegistry = ExtensionAdminContributionRegistry(),
    internal val bannerRegistry: ExtensionBannerContributionRegistry = ExtensionBannerContributionRegistry(),
    internal val navigationRegistry: ExtensionNavigationContributionRegistry =
        ExtensionNavigationContributionRegistry(),
    internal val layoutRegistry: ExtensionLayoutContributionRegistry = ExtensionLayoutContributionRegistry(),
    internal val assetRegistry: ExtensionAssetContributionRegistry = ExtensionAssetContributionRegistry(),
) {
    val routes: ExtensionRouteContributionRegistry = routeRegistry
    val platformPages: PlatformPageContributionRegistry = platformPageRegistry
    val filters: ExtensionFilterContributionRegistry = filterRegistry
    val admin: ExtensionAdminContributionRegistry = adminRegistry
    val banners: ExtensionBannerContributionRegistry = bannerRegistry
    val navigation: ExtensionNavigationContributionRegistry = navigationRegistry
    val layout: ExtensionLayoutContributionRegistry = layoutRegistry
    val assets: ExtensionAssetContributionRegistry = assetRegistry
}

class ExtensionRouteContributionRegistry internal constructor() {
    private val registrations = mutableListOf<ExtensionRouteRegistration>()

    fun register(
        route: ContractRoute,
        group: RouteGroup,
        description: String,
        pathPattern: String = description,
        method: String = "*",
    ) {
        registrations += ExtensionRouteRegistration(route, group, description, pathPattern, method)
    }

    fun register(registration: ExtensionRouteRegistration) {
        registrations += registration
    }

    fun publicUi(route: ContractRoute, description: String, pathPattern: String = description) {
        register(route, RouteGroup.PublicUi, description, pathPattern)
    }

    fun protectedUi(route: ContractRoute, description: String, pathPattern: String = description) {
        register(route, RouteGroup.ProtectedUi, description, pathPattern)
    }

    fun api(route: ContractRoute, description: String, pathPattern: String = description) {
        register(route, RouteGroup.Api, description, pathPattern)
    }

    fun admin(route: ContractRoute, description: String, pathPattern: String = description) {
        register(route, RouteGroup.Admin, description, pathPattern)
    }

    fun staticAssets(pathPrefix: String, loader: ResourceLoader, description: String = "Extension static assets") {
        registrations +=
            ExtensionRouteRegistration.staticAssets(
                route = pathPrefix bind static(loader),
                description = description,
                pathPattern = "$pathPrefix/*",
            )
    }

    internal fun snapshot(): List<ExtensionRouteRegistration> = registrations.toList()
}

class PlatformPageContributionRegistry internal constructor() {
    private val included = linkedSetOf<PlatformPageSets>()

    fun include(pageSet: PlatformPageSets) {
        included += pageSet
    }

    fun include(vararg pageSets: PlatformPageSets) {
        included += pageSets
    }

    fun includeAll(pageSets: Iterable<PlatformPageSets>) {
        included += pageSets
    }

    internal fun snapshot(): Set<PlatformPageSets> = included.toSet()
}

class ExtensionFilterContributionRegistry internal constructor() {
    private val filters = mutableListOf<Filter>()

    fun add(filter: Filter) {
        filters += filter
    }

    fun addAll(filters: Iterable<Filter>) {
        this.filters += filters
    }

    internal fun snapshot(): List<Filter> = filters.toList()
}

class ExtensionAdminContributionRegistry internal constructor() {
    private val sections = mutableListOf<AdminSection>()

    fun section(section: AdminSection) {
        sections += section
    }

    @Suppress("LongParameterList")
    fun section(
        id: String,
        navLabel: String,
        navIcon: String,
        route: ContractRoute,
        linkUrl: String,
        title: String = navLabel,
        metrics: List<AdminMetric> = emptyList(),
        linkLabel: String = "View details",
    ) {
        sections +=
            AdminSection(
                id = id,
                navLabel = navLabel,
                navIcon = navIcon,
                summaryCard =
                    AdminSummaryCard(title = title, metrics = metrics, linkUrl = linkUrl, linkLabel = linkLabel),
                route = route,
            )
    }

    fun sections(sections: Iterable<AdminSection>) {
        this.sections += sections
    }

    internal fun snapshot(): List<AdminSection> = sections.toList()
}

class ExtensionBannerContributionRegistry internal constructor() {
    private val providers = mutableListOf<BannerProvider>()

    fun provider(provider: BannerProvider) {
        providers += provider
    }

    fun providers(providers: Iterable<BannerProvider>) {
        this.providers += providers
    }

    internal fun snapshot(): List<BannerProvider> = providers.toList()
}

class ExtensionNavigationContributionRegistry internal constructor() {
    private val navItems = mutableListOf<ExtensionNavItem>()

    fun item(item: ExtensionNavItem) {
        navItems += item
    }

    fun item(label: String, url: String, icon: String, activeSection: String = url) {
        item(ExtensionNavItem(label, url, icon, activeSection))
    }

    fun items(items: Iterable<ExtensionNavItem>) {
        navItems += items
    }

    internal fun snapshot(): List<ExtensionNavItem> = navItems.toList()
}

class ExtensionLayoutContributionRegistry internal constructor() {
    private var renderer: ExtensionLayoutRenderer? = null

    fun replaceWith(renderer: ExtensionLayoutRenderer) {
        this.renderer = renderer
    }

    internal fun snapshot(): ExtensionLayoutRenderer? = renderer
}

class ExtensionAssetContributionRegistry internal constructor() {
    private val stylesheets = mutableListOf<String>()
    private val scripts = mutableListOf<String>()

    fun stylesheet(url: String) {
        stylesheets += url
    }

    fun script(url: String) {
        scripts += url
    }

    internal fun snapshot(): ExtensionAssets =
        ExtensionAssets(stylesheets = stylesheets.toList(), scripts = scripts.toList())
}
