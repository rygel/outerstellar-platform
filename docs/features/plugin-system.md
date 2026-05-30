# Plugin System

## Overview

Hosted apps extend the platform via the `HostedApp` interface. `PlatformPlugin` remains as a compatibility alias for older integrations, but `HostedApp` and `HostedAppContext` are the primary API names. The public SPI now ships in the `outerstellar-platform-plugin-api` module so hosted apps do not need to depend on `platform-web`.

For step-by-step upgrade notes, see the repository's [migration guide](../../MIGRATION.md).

Hosted apps can contribute:
- routes and filters
- shell navigation, admin sections, banners, layout replacement, and assets
- i18n/text overrides and template overrides
- database migrations through `HostedApp.migrations`

## HostedApp Interface

```kotlin
interface HostedApp {
    val id: String
    val appLabel: String
    val manifest: HostedAppManifest
    val mode: PlatformMode
    val migrations: PluginMigrations?

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

Plugins declare migrations with `HostedApp.migrations`:
- `location: String` — classpath location for Flyway migrations
- `historyTable: String` — defaults to `flyway_plugin_history`
- `migrationNames: List<String>` — optional explicit filenames for native-image classpath extraction

Plugin migrations run after host migrations in a separate Flyway instance. Baseline version is 0 so V1 migrations execute on fresh databases.

```kotlin
override val migrations =
    PluginMigrations(
        location = "classpath:db/migration/my-plugin",
        historyTable = "flyway_my_plugin_history",
        migrationNames = listOf("V1__init", "V2__seed"),
    )
```

Older plugins can keep overriding `migrationLocation`, `migrationHistoryTable`, and `migrationNames` for now, but those compatibility properties are deprecated in favor of `migrations`.

## Route ownership in PluginHostedApp

`HostedAppManifest.ownership` still defines the hosted app's declared route prefixes, but in `PlatformMode.PluginHostedApp` the platform grants `/` as a default UI ownership prefix. That lets a hosted app own root-facing UI routes such as `/`, `/dashboard`, and `/about` without forcing everything under `/<plugin-id>`.

API, admin, and asset ownership remain explicit and continue to use their declared prefixes unless the manifest overrides them.

## Testing hosted apps

For SPI-only tests, use `HostedAppContext.forTesting(...)` from the plugin API module.

For full-stack integration coverage, prefer the platform test harnesses such as `WebTest`, or boot the real app through `createServerComponents(plugin = ...)` instead of manually wiring each lower-level `createXxxComponents(...)` helper.

## Registration

The server wires a single hosted app into application startup and collects its contributions once. Ownership validation ensures hosted-app routes and assets stay inside the prefixes declared by `HostedAppManifest.ownership`.
