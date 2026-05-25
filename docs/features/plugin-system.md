# Plugin System

## Overview

Plugins extend the platform via the `PlatformPlugin` interface. They can add routes, filters, templates, admin sections, navigation items, i18n, and database migrations.

## PlatformPlugin Interface

```kotlin
interface PlatformPlugin : PluginMigrationSource {
    val id: String
    val appLabel: String
    val excludeDefaultRoutes: Set<String>
    val navItems: List<NavItem>
    val textResolver: I18nTextResolver?
    fun templateOverrides(): Map<String, String>?
    fun routes(): List<org.http4k.routing.RoutingHttpHandler>
    fun filters(ctx: PluginContext): List<org.http4k.routing.RoutingHttpHandler>
    fun adminSections(ctx: PluginContext): List<AdminSection>
    fun koinModules(): List<Module>
}
```

## Plugin Context

`PluginContext` provides access to:
- `currentUser(request)` — authenticated user
- `buildPage(request, title, section, data)` — wrap ViewModel in platform shell
- `forTesting(renderer, securityService, userRepository)` — test factory

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

```kotlin
startKoin {
    modules(
        myPluginModule,         // Plugin's own Koin module(s)
        configModule,
        persistenceModule,
        coreModule,
        webModule,
        securityModule,
    )
}
```

The web module discovers the plugin via Koin and wires its routes, filters, and admin sections.
