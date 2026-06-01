# Extension Author Guide

Extensions extend the platform through the `outerstellar-platform-extension-api` module. New extensions should implement
`io.github.rygel.outerstellar.platform.extension.PlatformExtension`. Compatibility typealiases remain under
`io.github.rygel.outerstellar.platform.web` for incremental migrations.

For upgrade notes from the older extension model, see [MIGRATION.md](../../MIGRATION.md).

## Starter project

If you want a runnable starting point instead of building from snippets, copy the
[`starter-extension-app`](../../starter-extension-app/README.md) project. It already includes:

- an extension module that depends only on `outerstellar-platform-extension-api`
- a launcher module that boots the real host with `createServerComponents`
- a contract test using `ExtensionContract`
- a local PostgreSQL compose file

## Dependency

Extension modules should depend on the public SPI instead of `platform-web`:

```xml
<dependency>
    <groupId>io.github.rygel</groupId>
    <artifactId>outerstellar-platform-extension-api</artifactId>
    <version>${outerstellar-platform.version}</version>
</dependency>
```

Only use `platform-web` in an application launcher or integration-test module that boots the real host with
`createServerComponents`.

## Minimal Extension

```kotlin
import io.github.rygel.outerstellar.platform.composition.PlatformMode
import io.github.rygel.outerstellar.platform.extension.PlatformExtension
import io.github.rygel.outerstellar.platform.extension.ExtensionContributionContext
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

class ReportsApp : PlatformExtension {
    override val id = "reports"
    override val appLabel = "Reports"
    override val mode = PlatformMode.ExtensionHost

    override fun contribute(context: ExtensionContributionContext) {
        val home =
            "/" meta { summary = "Reports home" } bindContract GET to
                { _: Request -> Response(Status.OK).body("Reports") }

        context.routes.publicUi(home, "Reports home", "/")
        context.navigation.item("Reports", "/", "bar-chart")
    }
}
```

Prefer the typed `contribute(context)` API for new code. It groups the extension points in one place:

| Need | Use |
|---|---|
| UI/API/admin routes | `context.routes.publicUi`, `protectedUi`, `api`, `admin` |
| Static files | `context.routes.staticAssets`, `context.assets.stylesheet`, `context.assets.script` |
| Shell navigation | `context.navigation.item` |
| Platform pages | `context.platformPages.include(...)` |
| Admin pages | `context.admin.section(...)` |
| Banners | `context.banners.provider(...)` |
| Layout replacement | `context.layout.replaceWith(...)` |

The older override methods such as `routeRegistrations(context)` still work, but `contribute(context)` is easier to
test and keeps route ownership metadata beside the route.

## Route Ownership

Every contributed route must stay inside the prefixes declared by `ExtensionManifest.ownership`.

By default, extension `reports` owns:

| Group | Default prefixes |
|---|---|
| UI | `/reports`, `/extension/reports` |
| API | `/api/reports`, `/api/extension/reports`, `/api/v1/reports`, `/api/v1/extension/reports` |
| Admin | `/admin/reports` |
| Static assets | `/extensions/reports/assets` |

`ExtensionHost` mode additionally grants `/` to UI routes only. That lets the extension own `/`, `/dashboard`, and
other product UI routes. API, admin, and static asset routes do not get root ownership automatically.

Use custom ownership only when the defaults do not match your product:

```kotlin
override val manifest =
    ExtensionManifest(
        id = id,
        appLabel = appLabel,
        ownership =
            ExtensionOwnership(
                uiPrefixes = listOf("/"),
                apiPrefixes = listOf("/api/reports"),
                adminPrefixes = listOf("/admin/reports"),
                assetPrefixes = listOf("/assets/reports"),
            ),
    )
```

## Migrations

Extensions with database tables declare isolated Flyway migrations:

```kotlin
override val migrations =
    ExtensionMigrations(
        location = "classpath:db/migration/reports",
        historyTable = "flyway_reports_history",
        migrationNames = listOf("V1__init", "V2__seed"),
    )
```

Extension migrations run after platform migrations in a separate Flyway instance. Version numbers do not conflict with
platform migrations because the history table is separate.

## SPI Contract Tests

Use `ExtensionContract` for fast extension tests that do not boot the full platform. This catches ownership mistakes and
returns the same diagnostics shown by the host.

```kotlin
class ReportsAppContractTest {
    @Test
    fun `extension contribution is valid`() {
        val diagnostics = ExtensionContract.diagnostics(ReportsApp(), testExtensionHostContext())

        assertEquals("reports", diagnostics.extensionId)
        assertEquals(listOf("/"), diagnostics.routes.map { it.pathPattern })
        assertEquals(1, diagnostics.capabilities.single { it.id == "navigation" }.count)
    }
}
```

Build the test context with `ExtensionHostContext.forTesting(rendering = ..., users = ..., security = ...)`. Stub only the
facades your extension uses. The in-repo `ExtensionContractTest` is the canonical copyable example.

Recommended contract assertions:

- route path patterns are the paths you expect
- diagnostics show the expected capabilities
- invalid routes fail with a useful ownership message
- migration metadata uses an extension-specific history table

## Full-Stack Extension Tests

Use full-stack tests when you need real routing, filters, persistence, migrations, auth, or shell rendering:

```kotlin
val components =
    createServerComponents(
        config = testConfig.copy(platformMode = PlatformMode.ExtensionHost),
        extension = ReportsApp(),
    )

try {
    val response = components.app.http!!(Request(GET, "/"))
    assertThat(response, hasStatus(Status.OK))
} finally {
    components.persistence.close()
}
```

Do not manually call lower-level factories such as `createPersistenceComponents`, `createSecurityComponents`, and
`createWebComponents` in extension tests unless the test is specifically about those internals. `createServerComponents`
is the production assembly path and keeps migrations, filters, routes, shell options, and extension host context aligned.

## Diagnostics

Startup validates extension ownership before routes are mounted. Common failures:

| Message | Meaning | Fix |
|---|---|---|
| `pathPattern starting with '/'` | The route metadata is not an absolute path | Pass an absolute `pathPattern` to the route helper |
| `outside extension ... ownership` | The route or asset is outside manifest prefixes | Move the path, update `ExtensionManifest.ownership`, or use the matching route group |
| `Route conflicts detected` | An extension route has the same method/path as a mounted platform route | Move the extension route or exclude the colliding platform page set |

The extension admin dashboard also exposes `ExtensionDiagnostics`: routes, capabilities, ownership prefixes, included
platform pages, stylesheets, and scripts.

## Public APIs To Prefer

To keep extensions isolated from platform internals, prefer:

- `PlatformExtension`, `ExtensionContributionContext`, `ExtensionHostContext`
- `ExtensionContract` for SPI-only tests
- `ExtensionMigrations` for extension schema changes
- `createServerComponents(config, extension)` for full-stack tests and launchers
- extension-facing facades on `ExtensionHostContext`: `users`, `analytics`, `notifications`, `rendering`, `security`

Avoid depending on internal page factories, route registrars, repository implementations, `WebTest`, or low-level
component factories from extension modules.
