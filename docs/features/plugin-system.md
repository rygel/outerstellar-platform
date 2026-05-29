# Plugin System

## Overview

Hosted apps extend the platform via the `HostedApp` interface. `PlatformPlugin` remains as a compatibility alias for older integrations, but `HostedApp` and `HostedAppContext` are the primary API names. The public SPI now ships in the `outerstellar-platform-plugin-api` module so hosted apps do not need to depend on `platform-web`.

Hosted apps can contribute:
- routes and filters
- shell navigation, admin sections, banners, layout replacement, and assets
- i18n/text overrides and template overrides
- database migrations through `PluginMigrationSource`

## HostedApp Interface

```kotlin
interface HostedApp : PluginMigrationSource {
    val id: String
    val appLabel: String
    val manifest: HostedAppManifest
    val mode: PlatformMode

    fun contribute(context: HostedAppContributionContext) {}
    fun routeRegistrations(context: HostedAppContext): List<PluginRouteRegistration> = emptyList()
    fun filters(context: HostedAppContext): List<Filter> = emptyList()
    fun adminSections(context: HostedAppContext): List<AdminSection> = emptyList()
    fun bannerProviders(context: HostedAppContext): List<BannerProvider> = emptyList()
    fun includePlatformPages(): Set<PlatformPageSets> = emptySet()
    fun layoutRenderer(context: HostedAppContext): PluginLayoutRenderer? = null
}
```

## Plugin Context

`HostedAppContext` is the primary SPI context. `PluginContext` remains a compatibility alias to the same type and provides access to stable plugin-facing facades:
- `app` (`config` compatibility alias) — safe app info only: `version`, `appBaseUrl`, `devMode`, `registrationEnabled`
- `users` (`userRepository` alias) — `currentUser(request)`, `findById`, `findByUsername`, `findByEmail`
- `analytics` — `identify`, `track`, `page`
- `notifications` (`notificationService` alias) — create/list/count/mark/delete user notifications
- `rendering` (`renderer` alias) — template renderer plus `renderShell(shell, bodyHtml)`
- `security` (`apiKeyService` / `oauthService` aliases) — API key CRUD and OAuth user resolution

Convenience helpers remain on the context itself:
- `currentUser(request)` — authenticated user
- `renderShell(shell, bodyHtml)` — wrap plugin HTML in the platform shell

## Template Overrides

Plugins can override JTE templates by providing a `templateOverrides()` map. The key is the template name (e.g. `"LayoutHead.kte"`), the value is the full classpath to the replacement. A `PluginTemplateRenderer` composites the base renderer with overrides.

## Database Migrations

Plugins extend `PluginMigrationSource` (inherited via `PlatformPlugin`):
- `migrationLocation: String?` — classpath location for Flyway migrations
- `migrationHistoryTable: String` — defaults to `flyway_plugin_history`

Plugin migrations run after host migrations in a separate Flyway instance. Baseline version is 0 so V1 migrations execute on fresh databases.

**Important**: Register the plugin as `PluginMigrationSource` in Koin:
```kotlin
single<PlatformPlugin> { MyPlugin() }
// Koin doesn't resolve parent types — persistence module needs this:
single<PluginMigrationSource> { get<PlatformPlugin>() }
```

Or better, use the bridge already in `WebModule.kt`:
- The web module registers a bridge bean: `single<PluginMigrationSource> { getOrNull<PlatformPlugin>() ?: NoPluginMigrationSource }`
- Apps with a plugin only need `single<PlatformPlugin> { MyPlugin() }`

## Registration

The server wires a single hosted app into application startup and collects its contributions once. Ownership validation ensures hosted-app routes and assets stay inside the prefixes declared by `HostedAppManifest.ownership`.
