package io.github.rygel.outerstellar.platform.plugin

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

class HostedAppContributionContext
internal constructor(
    val host: HostedAppContext,
    internal val routeRegistry: PluginRouteContributionRegistry = PluginRouteContributionRegistry(),
    internal val platformPageRegistry: PlatformPageContributionRegistry = PlatformPageContributionRegistry(),
    internal val filterRegistry: PluginFilterContributionRegistry = PluginFilterContributionRegistry(),
    internal val adminRegistry: PluginAdminContributionRegistry = PluginAdminContributionRegistry(),
    internal val bannerRegistry: PluginBannerContributionRegistry = PluginBannerContributionRegistry(),
    internal val navigationRegistry: PluginNavigationContributionRegistry = PluginNavigationContributionRegistry(),
    internal val layoutRegistry: PluginLayoutContributionRegistry = PluginLayoutContributionRegistry(),
    internal val assetRegistry: PluginAssetContributionRegistry = PluginAssetContributionRegistry(),
) {
    val routes: PluginRouteContributionRegistry = routeRegistry
    val platformPages: PlatformPageContributionRegistry = platformPageRegistry
    val filters: PluginFilterContributionRegistry = filterRegistry
    val admin: PluginAdminContributionRegistry = adminRegistry
    val banners: PluginBannerContributionRegistry = bannerRegistry
    val navigation: PluginNavigationContributionRegistry = navigationRegistry
    val layout: PluginLayoutContributionRegistry = layoutRegistry
    val assets: PluginAssetContributionRegistry = assetRegistry
}

typealias PluginContributionContext = HostedAppContributionContext

class PluginRouteContributionRegistry internal constructor() {
    private val registrations = mutableListOf<PluginRouteRegistration>()

    fun register(
        route: ContractRoute,
        group: RouteGroup,
        description: String,
        pathPattern: String = description,
        method: String = "*",
    ) {
        registrations += PluginRouteRegistration(route, group, description, pathPattern, method)
    }

    fun register(registration: PluginRouteRegistration) {
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

    fun staticAssets(pathPrefix: String, loader: ResourceLoader, description: String = "Plugin static assets") {
        registrations +=
            PluginRouteRegistration.staticAssets(
                route = pathPrefix bind static(loader),
                description = description,
                pathPattern = "$pathPrefix/*",
            )
    }

    internal fun snapshot(): List<PluginRouteRegistration> = registrations.toList()
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

class PluginFilterContributionRegistry internal constructor() {
    private val filters = mutableListOf<Filter>()

    fun add(filter: Filter) {
        filters += filter
    }

    fun addAll(filters: Iterable<Filter>) {
        this.filters += filters
    }

    internal fun snapshot(): List<Filter> = filters.toList()
}

class PluginAdminContributionRegistry internal constructor() {
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

class PluginBannerContributionRegistry internal constructor() {
    private val providers = mutableListOf<BannerProvider>()

    fun provider(provider: BannerProvider) {
        providers += provider
    }

    fun providers(providers: Iterable<BannerProvider>) {
        this.providers += providers
    }

    internal fun snapshot(): List<BannerProvider> = providers.toList()
}

class PluginNavigationContributionRegistry internal constructor() {
    private val navItems = mutableListOf<PluginNavItem>()

    fun item(item: PluginNavItem) {
        navItems += item
    }

    fun item(label: String, url: String, icon: String, activeSection: String = url) {
        item(PluginNavItem(label, url, icon, activeSection))
    }

    fun items(items: Iterable<PluginNavItem>) {
        navItems += items
    }

    internal fun snapshot(): List<PluginNavItem> = navItems.toList()
}

class PluginLayoutContributionRegistry internal constructor() {
    private var renderer: PluginLayoutRenderer? = null

    fun replaceWith(renderer: PluginLayoutRenderer) {
        this.renderer = renderer
    }

    internal fun snapshot(): PluginLayoutRenderer? = renderer
}

class PluginAssetContributionRegistry internal constructor() {
    private val stylesheets = mutableListOf<String>()
    private val scripts = mutableListOf<String>()

    fun stylesheet(url: String) {
        stylesheets += url
    }

    fun script(url: String) {
        scripts += url
    }

    internal fun snapshot(): PluginAssets = PluginAssets(stylesheets = stylesheets.toList(), scripts = scripts.toList())
}
