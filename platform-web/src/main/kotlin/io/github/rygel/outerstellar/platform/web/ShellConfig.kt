package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.banner.BannerProvider
import io.github.rygel.outerstellar.platform.composition.PlatformMode
import io.github.rygel.outerstellar.platform.composition.RouteGroup
import io.github.rygel.outerstellar.platform.plugin.HostedAppContribution
import io.github.rygel.outerstellar.platform.web.composition.PlatformPageSets

data class ShellConfig(
    val pluginOptions: PluginOptions = PluginOptions(),
    val appBaseUrl: String = "",
    val sidebarFactory: SidebarFactory = SidebarFactory(),
    val bannerProviders: List<BannerProvider> = emptyList(),
    val mode: PlatformMode = PlatformMode.FullPlatformApp,
    val appLabel: String = "Outerstellar",
    val appHomeUrl: String = "/",
    val includedPlatformPages: Set<PlatformPageSets> = emptySet(),
) {
    companion object {
        fun from(
            contribution: HostedAppContribution,
            appBaseUrl: String = "",
            sidebarFactory: SidebarFactory = SidebarFactory(),
        ): ShellConfig =
            ShellConfig(
                pluginOptions = contribution.options,
                appBaseUrl = appBaseUrl,
                sidebarFactory = sidebarFactory,
                bannerProviders = contribution.bannerProviders,
                mode = contribution.mode,
                appLabel = contribution.appLabel,
                appHomeUrl =
                    contribution.routeRegistrations
                        .firstOrNull { it.pathPattern == "/" && it.group in uiRouteGroups }
                        ?.pathPattern
                        ?: contribution.manifest?.ownership?.uiPrefixes?.firstOrNull()
                        ?: contribution.effectiveOwnership?.uiPrefixes?.firstOrNull()
                        ?: "/",
                includedPlatformPages = contribution.includedPlatformPages,
            )

        private val uiRouteGroups = setOf(RouteGroup.PublicUi, RouteGroup.ProtectedUi)
    }
}
