# Migration Guide

This guide covers the main migration path from the older **1.6.x** plugin model to the current **3.6.4** platform surface.

## Recommended path

For most hosted apps, the fastest path is:

1. Depend on `outerstellar-platform-plugin-api`.
2. Implement `HostedApp` directly.
3. Start the app with `createServerComponents(plugin = MyHostedApp())`.
4. Move route/page selection to `mode` + `includePlatformPages()`.
5. Replace legacy migration properties with `override val migrations = PluginMigrations(...)`.

## API name changes

| Older shape | Current shape |
| --- | --- |
| `PlatformPlugin` | `HostedApp` is the primary SPI. `PlatformPlugin` remains as a deprecated compatibility alias. |
| `PluginContext` | `HostedAppContext` is the primary name. `PluginContext` remains as an alias. |
| `pluginMigrationSource` | `HostedApp.migrations: PluginMigrations?` |
| `excludeDefaultRoutes` | `mode` + `includePlatformPages()` |

## Minimal hosted app

```kotlin
class MyHostedApp : HostedApp {
    override val id = "my-app"
    override val appLabel = "My App"
    override val mode = PlatformMode.PluginHostedApp

    override fun contribute(context: HostedAppContributionContext) {
        context.routes.protectedUi(myHomeRoute(), "Home", "/")
        context.navigation.item("Home", "/", "home-4-line")
    }
}

val server = createServerComponents(plugin = MyHostedApp())
```

## Platform modes and route ownership

The biggest behavior change is that platform UI is now explicit:

- `FullPlatformApp` — the default Outerstellar product UI is mounted.
- `PluginHostedApp` — the plugin owns the product UI and opts into platform pages with `includePlatformPages()`.
- `HeadlessKernel` — no HTML product UI.

### Root routes in `PluginHostedApp`

In `PluginHostedApp`, the hosted app now gets `/` as a default **UI ownership prefix**. That means a plugin can register:

- `/`
- `/dashboard`
- `/about`

without forcing everything under `/<plugin-id>`.

API, admin, and asset ownership are still explicit and stay under their own prefixes unless the manifest overrides them.

## Replacing `excludeDefaultRoutes`

Older integrations often fought the platform UI by filtering or excluding platform-owned paths. The new model is opt-in.

```kotlin
override val mode = PlatformMode.PluginHostedApp

override fun includePlatformPages() = setOf(
    PlatformPageSets.SETTINGS,
    PlatformPageSets.SEARCH,
)
```

If the plugin does not include a page set, the default platform UI for that page set is not mounted.

## Hosted app migrations

Use `HostedApp.migrations` instead of the legacy `migrationLocation`, `migrationHistoryTable`, and `migrationNames` properties:

```kotlin
override val migrations =
    PluginMigrations(
        location = "classpath:db/migration/my-app",
        historyTable = "flyway_my_app_history",
        migrationNames = listOf("V1__init", "V2__seed"),
    )
```

`PluginMigrations` means:

- `location` — Flyway classpath location for plugin migrations
- `historyTable` — separate Flyway history table for the hosted app
- `migrationNames` — optional explicit filenames, mainly useful for native-image packaging

The host passes `plugin.migrations` into persistence startup automatically when you use `createServerComponents(plugin = ...)`.

## Context and facade changes

`HostedAppContext` is narrower than the older raw host-service surface. Prefer these facades:

| Use this now | Compatibility alias |
| --- | --- |
| `app` | `config` |
| `users` | `userRepository` |
| `rendering` | `renderer` |
| `security.apiKeys` | `apiKeyService` |
| `security.oauth` | `oauthService` |
| `notifications` | `notificationService` |

The compatibility aliases still exist, but they are deprecated.

## Testing

Two patterns are supported:

### SPI-only tests

Use `HostedAppContext.forTesting(...)` when testing the plugin contract itself without booting the whole platform:

```kotlin
val context = HostedAppContext.forTesting(rendering = rendering, users = users, security = security)
```

### Full-stack web tests

For integration coverage against the real platform wiring, prefer the platform test harnesses such as `WebTest`, or boot through `createServerComponents(plugin = ...)` instead of manually wiring every `createXxxComponents(...)` function.

When a test already owns an `AppConfig` or a test database, pass it directly:

```kotlin
val server = createServerComponents(
    config = testConfig.copy(platformMode = PlatformMode.PluginHostedApp),
    plugin = MyHostedApp(),
)

val response = server.app.http!!(Request(GET, "/my-app"))
```

Close `server.persistence` when the test is done.

## Factory wiring guidance

The lower-level `createSecurityComponents(...)`, `createCoreComponents(...)`, and `createWebComponents(...)` functions are still available, but they are assembly seams, not the preferred application entrypoint.

For applications and most tests, prefer:

```kotlin
createServerComponents(plugin = MyHostedApp())
```

That keeps persistence migrations, email wiring, websocket/event wiring, and hosted-app context assembly aligned with production.
