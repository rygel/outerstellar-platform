# Migration Guide

This guide covers the main migration path from the older **1.6.x** plugin model to the current **3.6.4** platform surface.

## Quick checklist: 1.6.x to 3.6.x

- [ ] Depend on `outerstellar-platform-plugin-api` instead of `platform-web`
- [ ] Implement `HostedApp` directly (or keep `PlatformPlugin` — it is a deprecated alias)
- [ ] Start the app with `createServerComponents(plugin = MyHostedApp())`
- [ ] Set `mode = PlatformMode.PluginHostedApp` for custom product UI
- [ ] Move route/page selection to `mode` + `includePlatformPages()`
- [ ] Replace legacy migration properties with `override val migrations = PluginMigrations(...)`
- [ ] Use `HostedAppContributionContext` in `contribute()` for route registration
- [ ] Add `emailService` parameter to `createSecurityComponents(...)` calls
- [ ] Check Jackson version alignment if your app uses Jackson 3.x

## Primary import paths

```kotlin
import io.github.rygel.outerstellar.platform.plugin.HostedApp
import io.github.rygel.outerstellar.platform.plugin.HostedAppContributionContext
import io.github.rygel.outerstellar.platform.PluginMigrations
import io.github.rygel.outerstellar.platform.composition.PlatformMode
import io.github.rygel.outerstellar.platform.web.composition.PlatformPageSets
import io.github.rygel.outerstellar.platform.createServerComponents
```

The `outerstellar-platform-plugin-api` module re-exports these under `io.github.rygel.outerstellar.platform.web` as
typealiases for older integrations. New code should import from the `plugin` package directly.

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

In `PluginHostedApp`, the hosted app automatically gets `/` as a **UI ownership prefix**. This means the hosted app
can register routes at `/`, `/dashboard`, `/about`, and any other top-level UI path without prefixing them with
`/<plugin-id>`.

How it works internally: `HostedAppContribution.from(...)` calls `HostedAppOwnership.withMode(mode)` which prepends `"/"`
to the `uiPrefixes` list when the mode is `PluginHostedApp`. The validation in `requirePathOwnedByManifest` then
accepts any path that matches `/` or starts with `/…`.

**What stays under explicit prefixes:**

- API routes — still require `/api/<plugin-id>` or similar declared prefixes
- Admin routes — still require `/admin/<plugin-id>`
- Static assets — still require `/plugins/<plugin-id>/assets`

Example of a hosted app claiming the root:

```kotlin
class DashboardApp : HostedApp {
    override val id = "dashboard"
    override val appLabel = "Dashboard"
    override val mode = PlatformMode.PluginHostedApp

    override fun contribute(context: HostedAppContributionContext) {
        // These all work because PluginHostedApp grants "/" as a UI prefix
        context.routes.publicUi(rootRoute(), "Root", "/")
        context.routes.protectedUi(dashboardRoute(), "Dashboard", "/dashboard")
        context.routes.publicUi(aboutRoute(), "About", "/about")
    }
}
```

## Replacing `excludeDefaultRoutes`

Older integrations often fought the platform UI by filtering or excluding platform-owned paths. The new model is opt-in.

**Before (1.6.x):**

```kotlin
class MyPlugin : PlatformPlugin {
    override fun excludeDefaultRoutes() = setOf("home", "contacts")
}
```

**After (3.6.x):**

```kotlin
override val mode = PlatformMode.PluginHostedApp

override fun includePlatformPages() = setOf(
    PlatformPageSets.SETTINGS,
    PlatformPageSets.SEARCH,
)
```

If the plugin does not include a page set, the default platform UI for that page set is not mounted.

## Hosted app migrations

**Before (1.6.x):**

```kotlin
override val migrationLocation = "classpath:db/migration/my-app"
override val migrationHistoryTable = "flyway_my_app_history"
override val migrationNames = listOf("V1__init", "V2__seed")
```

**After (3.6.x):**

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

## Route registration: before and after

**Before (1.6.x) — `routeRegistrations` returning a list:**

```kotlin
override fun routeRegistrations(context: HostedAppContext): List<PluginRouteRegistration> =
    listOf(
        PluginRouteRegistration(myRoute(), RouteGroup.ProtectedUi, "My page", "/my-page"),
    )
```

**After (3.6.x) — `contribute` with the contribution context:**

```kotlin
override fun contribute(context: HostedAppContributionContext) {
    context.routes.protectedUi(myRoute(), "My page", "/my-page")
    context.navigation.item("My Page", "/my-page", "my-icon")
}
```

The `contribute` method is the preferred registration path. `routeRegistrations(...)` still works for backward
compatibility — both are called during `HostedAppContribution.from(...)`.

## Testing

Two patterns are supported:

### SPI-only tests

Use `HostedAppContext.forTesting(...)` when testing the plugin contract itself without booting the whole platform:

```kotlin
val context = HostedAppContext.forTesting(rendering = rendering, users = users, security = security)
```

### Full-stack web tests

For integration coverage against the real platform wiring, use `createServerComponents` directly:

```kotlin
val server = createServerComponents(
    config = testConfig.copy(platformMode = PlatformMode.PluginHostedApp),
    plugin = MyHostedApp(),
)

val response = server.app.http!!(Request(GET, "/my-app"))
```

Close `server.persistence` when the test is done.

The platform's `WebTest` base class is an internal test harness. Downstream hosted apps should **not** extend it.
Instead, create a test database (e.g. using `SharedPostgres` or Testcontainers) and boot through
`createServerComponents(config, plugin)`.

## Factory wiring guidance

The lower-level `createSecurityComponents(...)`, `createCoreComponents(...)`, and `createWebComponents(...)` functions are still available, but they are assembly seams, not the preferred application entrypoint.

For applications and most tests, prefer:

```kotlin
createServerComponents(plugin = MyHostedApp())
```

That keeps persistence migrations, email wiring, websocket/event wiring, and hosted-app context assembly aligned with production.

### Deprecated factory overloads

The following deprecated overloads are provided for backward compatibility:

**`createPersistenceComponents(config, pluginMigrationSource: String?)`** — wraps the string location into a
`PluginMigrations`. Migrate to `createPersistenceComponents(config, PluginMigrations?)` or use
`createServerComponents(plugin = ...)`.

**`createSecurityComponents(config, userRepository, ..., oauthRepository?, sessionRepository?)`** — calls without
`emailService` default to `NoOpEmailService`. Migrate by adding an explicit `emailService` parameter.

## Jackson and Flyway dependency compatibility

The platform manages Jackson **2.21.4** via the `jackson-bom` in its root POM dependency management. Flyway 11.x
also depends on Jackson 2.x, so there is no conflict within the platform itself.

**If your downstream app uses Jackson 3.x**, Flyway 11 will still pull in Jackson 2.x on the classpath. To avoid
convergence failures:

1. Declare the platform's `jackson-bom` in your dependency management to pin the 2.x versions that Flyway requires.
2. Or exclude `jackson-*` from the Flyway dependency and ensure Flyway's Jackson usage still resolves:
   ```xml
   <dependency>
       <groupId>org.flywaydb</groupId>
       <artifactId>flyway-core</artifactId>
       <exclusions>
           <exclusion>
               <groupId>com.fasterxml.jackson.core</groupId>
               <artifactId>jackson-databind</artifactId>
           </exclusion>
       </exclusions>
   </dependency>
   ```
3. Use the Maven Enforcer plugin's `dependencyConvergence` rule to catch conflicts early.

The platform already runs `dependencyConvergence` and `banDuplicateClasses` as part of its build, so the published
artifacts have a clean classpath. Conflicts only appear when downstream consumers introduce different Jackson versions.
