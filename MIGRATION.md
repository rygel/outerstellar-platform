# Migration Guide

This guide covers the main migration path from the older **1.6.x** extension model to the current **3.6.4** platform surface.

## Quick checklist: 1.6.x to 3.6.x

- [ ] Depend on `outerstellar-platform-extension-api` instead of `platform-web`
- [ ] Import `PlatformExtension` from `io.github.rygel.outerstellar.platform.extension`
- [ ] Start the app with `createServerComponents(extension = MyPlatformExtension())`
- [ ] Set `mode = PlatformMode.ExtensionHost` for custom product UI
- [ ] Move route/page selection to `mode` + `includePlatformPages()`
- [ ] Replace legacy migration properties with `override val migrations = ExtensionMigrations(...)`
- [ ] Use `ExtensionContributionContext` in `contribute()` for route registration
- [ ] Add `emailService` parameter to `createSecurityComponents(...)` calls
- [ ] Check Jackson version alignment if your app uses Jackson 3.x

## Primary import paths

```kotlin
import io.github.rygel.outerstellar.platform.extension.PlatformExtension
import io.github.rygel.outerstellar.platform.extension.ExtensionContributionContext
import io.github.rygel.outerstellar.platform.ExtensionMigrations
import io.github.rygel.outerstellar.platform.composition.PlatformMode
import io.github.rygel.outerstellar.platform.web.composition.PlatformPageSets
import io.github.rygel.outerstellar.platform.createServerComponents
```

The `outerstellar-platform-extension-api` module also exposes compatibility typealiases under
`io.github.rygel.outerstellar.platform.web`. New code should import from the `extension` package directly.

## Recommended path

For most extensions, the fastest path is:

1. Depend on `outerstellar-platform-extension-api`.
2. Implement `PlatformExtension` directly.
3. Start the app with `createServerComponents(extension = MyPlatformExtension())`.
4. Move route/page selection to `mode` + `includePlatformPages()`.
5. Replace legacy migration properties with `override val migrations = ExtensionMigrations(...)`.

## API name changes

| Older shape | Current shape |
| --- | --- |
| `io.github.rygel.outerstellar.platform.web.PlatformExtension` | `io.github.rygel.outerstellar.platform.extension.PlatformExtension` is the primary SPI. The old `web` package import remains as a compatibility typealias. |
| `ExtensionContext` | `ExtensionHostContext` is the primary name. `ExtensionContext` remains as an alias. |
| `extensionMigrationSource` | `PlatformExtension.migrations: ExtensionMigrations?` |
| `excludeDefaultRoutes` | `mode` + `includePlatformPages()` |

## Minimal extension

```kotlin
class MyPlatformExtension : PlatformExtension {
    override val id = "my-app"
    override val appLabel = "My App"
    override val mode = PlatformMode.ExtensionHost

    override fun contribute(context: ExtensionContributionContext) {
        context.routes.protectedUi(myHomeRoute(), "Home", "/")
        context.navigation.item("Home", "/", "home-4-line")
    }
}

val server = createServerComponents(extension = MyPlatformExtension())
```

## Platform modes and route ownership

The biggest behavior change is that platform UI is now explicit:

- `FullPlatform` — the default Outerstellar product UI is mounted.
- `ExtensionHost` — the extension owns the product UI and opts into platform pages with `includePlatformPages()`.
- `Headless` — no HTML product UI.

### Root routes in `ExtensionHost`

In `ExtensionHost`, the extension automatically gets `/` as a **UI ownership prefix**. This means the extension
can register routes at `/`, `/dashboard`, `/about`, and any other top-level UI path without prefixing them with
`/<extension-id>`.

How it works internally: `ExtensionContribution.from(...)` calls `ExtensionOwnership.withMode(mode)` which prepends `"/"`
to the `uiPrefixes` list when the mode is `ExtensionHost`. The validation in `requirePathOwnedByManifest` then
accepts any path that matches `/` or starts with `/…`.

**What stays under explicit prefixes:**

- API routes — still require `/api/<extension-id>` or similar declared prefixes
- Admin routes — still require `/admin/<extension-id>`
- Static assets — still require `/extensions/<extension-id>/assets`

Example of an extension claiming the root:

```kotlin
class DashboardApp : PlatformExtension {
    override val id = "dashboard"
    override val appLabel = "Dashboard"
    override val mode = PlatformMode.ExtensionHost

    override fun contribute(context: ExtensionContributionContext) {
        // These all work because ExtensionHost grants "/" as a UI prefix
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
class MyExtension : PlatformExtension {
    override fun excludeDefaultRoutes() = setOf("home", "contacts")
}
```

**After (3.6.x):**

```kotlin
override val mode = PlatformMode.ExtensionHost

override fun includePlatformPages() = setOf(
    PlatformPageSets.SETTINGS,
    PlatformPageSets.SEARCH,
)
```

If the extension does not include a page set, the default platform UI for that page set is not mounted.

## Extension migrations

**Before (1.6.x):**

```kotlin
override val migrationLocation = "classpath:db/migration/my-app"
override val migrationHistoryTable = "flyway_my_app_history"
override val migrationNames = listOf("V1__init", "V2__seed")
```

**After (3.6.x):**

```kotlin
override val migrations =
    ExtensionMigrations(
        location = "classpath:db/migration/my-app",
        historyTable = "flyway_my_app_history",
        migrationNames = listOf("V1__init", "V2__seed"),
    )
```

`ExtensionMigrations` means:

- `location` — Flyway classpath location for extension migrations
- `historyTable` — separate Flyway history table for the extension
- `migrationNames` — optional explicit filenames, mainly useful for native-image packaging

The host passes `extension.migrations` into persistence startup automatically when you use `createServerComponents(extension = ...)`.

## Context and facade changes

`ExtensionHostContext` is narrower than the older raw host-service surface. Prefer these facades:

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
override fun routeRegistrations(context: ExtensionHostContext): List<ExtensionRouteRegistration> =
    listOf(
        ExtensionRouteRegistration(myRoute(), RouteGroup.ProtectedUi, "My page", "/my-page"),
    )
```

**After (3.6.x) — `contribute` with the contribution context:**

```kotlin
override fun contribute(context: ExtensionContributionContext) {
    context.routes.protectedUi(myRoute(), "My page", "/my-page")
    context.navigation.item("My Page", "/my-page", "my-icon")
}
```

The `contribute` method is the preferred registration path. `routeRegistrations(...)` still works for backward
compatibility — both are called during `ExtensionContribution.from(...)`.

## Testing

Two patterns are supported:

### SPI-only tests

Use `ExtensionHostContext.forTesting(...)` when testing the extension contract itself without booting the whole platform:

```kotlin
val context = ExtensionHostContext.forTesting(rendering = rendering, users = users, security = security)
```

### Full-stack web tests

For integration coverage against the real platform wiring, use `createServerComponents` directly:

```kotlin
val server = createServerComponents(
    config = testConfig.copy(platformMode = PlatformMode.ExtensionHost),
    extension = MyPlatformExtension(),
)

val response = server.app.http!!(Request(GET, "/my-app"))
```

Close `server.persistence` when the test is done.

The platform's `WebTest` base class is an internal test harness. Downstream extensions should **not** extend it.
Instead, create a test database (e.g. using `SharedPostgres` or Testcontainers) and boot through
`createServerComponents(config, extension)`.

## Factory wiring guidance

The lower-level `createSecurityComponents(...)`, `createCoreComponents(...)`, and `createWebComponents(...)` functions are still available, but they are assembly seams, not the preferred application entrypoint.

For applications and most tests, prefer:

```kotlin
createServerComponents(extension = MyPlatformExtension())
```

That keeps persistence migrations, email wiring, websocket/event wiring, and extension host context assembly aligned with production.

### Deprecated factory overloads

The following deprecated overloads are provided for backward compatibility:

**`createPersistenceComponents(config, extensionMigrationSource: String?)`** — wraps the string location into a
`ExtensionMigrations`. Migrate to `createPersistenceComponents(config, ExtensionMigrations?)` or use
`createServerComponents(extension = ...)`.

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
3. Use the Maven Enforcer extension's `dependencyConvergence` rule to catch conflicts early.

The platform already runs `dependencyConvergence` and `banDuplicateClasses` as part of its build, so the published
artifacts have a clean classpath. Conflicts only appear when downstream consumers introduce different Jackson versions.
