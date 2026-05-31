package io.github.rygel.outerstellar.platform.web

typealias PluginNavItem = io.github.rygel.outerstellar.platform.plugin.PluginNavItem

typealias AdminNavItem = io.github.rygel.outerstellar.platform.plugin.AdminNavItem

typealias HostedAppManifest = io.github.rygel.outerstellar.platform.plugin.HostedAppManifest

typealias HostedAppOwnership = io.github.rygel.outerstellar.platform.plugin.HostedAppOwnership

typealias PluginRouteRegistration = io.github.rygel.outerstellar.platform.plugin.PluginRouteRegistration

typealias PluginAssets = io.github.rygel.outerstellar.platform.plugin.PluginAssets

typealias PluginLayoutRenderer = io.github.rygel.outerstellar.platform.plugin.PluginLayoutRenderer

typealias PluginOptions = io.github.rygel.outerstellar.platform.plugin.PluginOptions

typealias PluginAppInfo = io.github.rygel.outerstellar.platform.plugin.PluginAppInfo

typealias HostedAppContext = io.github.rygel.outerstellar.platform.plugin.HostedAppContext

typealias PluginContext = io.github.rygel.outerstellar.platform.plugin.PluginContext

typealias PluginUsers = io.github.rygel.outerstellar.platform.plugin.PluginUsers

typealias PluginAnalytics = io.github.rygel.outerstellar.platform.plugin.PluginAnalytics

typealias PluginNotification = io.github.rygel.outerstellar.platform.plugin.PluginNotification

typealias PluginNotifications = io.github.rygel.outerstellar.platform.plugin.PluginNotifications

typealias PluginRendering = io.github.rygel.outerstellar.platform.plugin.PluginRendering

typealias PluginApiKeys = io.github.rygel.outerstellar.platform.plugin.PluginApiKeys

typealias PluginOAuth = io.github.rygel.outerstellar.platform.plugin.PluginOAuth

typealias PluginSecurity = io.github.rygel.outerstellar.platform.plugin.PluginSecurity

typealias HostedApp = io.github.rygel.outerstellar.platform.plugin.HostedApp

/**
 * Compatibility name for older integrations.
 *
 * New hosted apps should implement [HostedApp] directly from `outerstellar-platform-plugin-api`.
 */
@Deprecated("Use HostedApp directly.", ReplaceWith("HostedApp"))
interface PlatformPlugin : io.github.rygel.outerstellar.platform.plugin.HostedApp
