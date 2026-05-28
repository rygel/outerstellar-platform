package io.github.rygel.outerstellar.platform.web

typealias PluginNavItem = io.github.rygel.outerstellar.platform.plugin.PluginNavItem

typealias AdminNavItem = io.github.rygel.outerstellar.platform.plugin.AdminNavItem

typealias HostedAppManifest = io.github.rygel.outerstellar.platform.plugin.HostedAppManifest

typealias HostedAppOwnership = io.github.rygel.outerstellar.platform.plugin.HostedAppOwnership

typealias PluginRouteRegistration = io.github.rygel.outerstellar.platform.plugin.PluginRouteRegistration

typealias PluginAssets = io.github.rygel.outerstellar.platform.plugin.PluginAssets

typealias PluginLayoutRenderer = io.github.rygel.outerstellar.platform.plugin.PluginLayoutRenderer

typealias PluginOptions = io.github.rygel.outerstellar.platform.plugin.PluginOptions

typealias HostedAppContext = io.github.rygel.outerstellar.platform.plugin.HostedAppContext

typealias PluginContext = io.github.rygel.outerstellar.platform.plugin.PluginContext

typealias HostedApp = io.github.rygel.outerstellar.platform.plugin.HostedApp

/** Compatibility name for existing integrations. New hosted apps should implement HostedApp. */
interface PlatformPlugin : io.github.rygel.outerstellar.platform.plugin.HostedApp
