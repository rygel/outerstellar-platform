package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.banner.BannerProvider

data class ShellConfig(
    val pluginOptions: PluginOptions = PluginOptions(),
    val appBaseUrl: String = "",
    val sidebarFactory: SidebarFactory = SidebarFactory(),
    val bannerProviders: List<BannerProvider> = emptyList(),
)
