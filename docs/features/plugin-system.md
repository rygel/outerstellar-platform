# Plugin Author Guide

Hosted apps extend the platform through the `outerstellar-platform-plugin-api` module. New plugins should implement
`HostedApp`; `PlatformPlugin` remains only as a compatibility alias for older integrations.

For upgrade notes from the older plugin model, see [MIGRATION.md](../../MIGRATION.md).

## Dependency

Plugin modules should depend on the public SPI instead of `platform-web`:

```xml
<dependency>
    <groupId>io.github.rygel</groupId>
    <artifactId>outerstellar-platform-plugin-api</artifactId>
    <version>${outerstellar-platform.version}</version>
</dependency>
```

Only use `platform-web` in an application launcher or integration-test module that boots the real host with
`createServerComponents`.

## Minimal Hosted App

```kotlin
import io.github.rygel.outerstellar.platform.composition.PlatformMode
import io.github.rygel.outerstellar.platform.plugin.HostedApp
import io.github.rygel.outerstellar.platform.plugin.HostedAppContributionContext
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

class ReportsApp : HostedApp {
    override val id = "reports"
    override val appLabel = "Reports"
    override val mode = PlatformMode.PluginHostedApp

    override fun contribute(context: HostedAppContributionContext) {
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

Every contributed route must stay inside the prefixes declared by `HostedAppManifest.ownership`.

By default, plugin `reports` owns:

| Group | Default prefixes |
|---|---|
| UI | `/reports`, `/plugin/reports` |
| API | `/api/reports`, `/api/plugin/reports`, `/api/v1/reports`, `/api/v1/plugin/reports` |
| Admin | `/admin/reports` |
| Static assets | `/plugins/reports/assets` |

`PluginHostedApp` mode additionally grants `/` to UI routes only. That lets the hosted app own `/`, `/dashboard`, and
other product UI routes. API, admin, and static asset routes do not get root ownership automatically.

Use custom ownership only when the defaults do not match your product:

```kotlin
override val manifest =
    HostedAppManifest(
        id = id,
        appLabel = appLabel,
        ownership =
            HostedAppOwnership(
                uiPrefixes = listOf("/"),
                apiPrefixes = listOf("/api/reports"),
                adminPrefixes = listOf("/admin/reports"),
                assetPrefixes = listOf("/assets/reports"),
            ),
    )
```

## Migrations

Plugins with database tables declare isolated Flyway migrations:

```kotlin
override val migrations =
    PluginMigrations(
        location = "classpath:db/migration/reports",
        historyTable = "flyway_reports_history",
        migrationNames = listOf("V1__init", "V2__seed"),
    )
```

Plugin migrations run after platform migrations in a separate Flyway instance. Version numbers do not conflict with
platform migrations because the history table is separate.

## SPI Contract Tests

Use `HostedAppContract` for fast plugin tests that do not boot the full platform. This catches ownership mistakes and
returns the same diagnostics shown by the host.

### Verify My Plugin Pattern

Every hosted app should keep one cheap SPI contract test next to the plugin module. The test should collect
`HostedAppDiagnostics` through `HostedAppContract`, then assert the externally visible contract the platform will
mount:

- expected route path patterns and route groups
- expected shell navigation entries or other capabilities
- expected platform page sets
- plugin migration metadata, when the plugin owns tables
- at least one invalid-route assertion when the plugin uses custom ownership

This test does not need PostgreSQL, JTE templates, or the web server. It should depend only on
`outerstellar-platform-plugin-api` plus the test libraries the plugin already uses.

```kotlin
class ReportsAppContractTest {
    @Test
    fun `plugin contribution is valid`() {
        val diagnostics = HostedAppContract.diagnostics(ReportsApp(), testHostedAppContext())

        assertEquals("reports", diagnostics.hostedAppId)
        assertEquals(listOf("/"), diagnostics.routes.map { it.pathPattern })
        assertEquals(1, diagnostics.capabilities.single { it.id == "navigation" }.count)
    }

    @Test
    fun `plugin rejects routes outside its ownership`() {
        val app = ReportsAppWithBadRoute(pathPattern = "/wrong")

        val error = assertFailsWith<IllegalArgumentException> {
            HostedAppContract.collect(app, testHostedAppContext())
        }

        assertTrue(error.message.orEmpty().contains("outside hosted app 'reports' ownership"))
    }
}
```

Build the test context with `HostedAppContext.forTesting(rendering = ..., users = ..., security = ...)`. Stub only the
facades your plugin uses. If the plugin only registers routes and navigation, the stubs can return empty values or
`error("Not used")` for methods that should not be reached.

Keep the helper local to the plugin test source set:

```kotlin
private fun testHostedAppContext(): HostedAppContext =
    HostedAppContext.forTesting(
        rendering = testRendering(),
        users = testUsers(),
        security = testSecurity(),
    )
```

The in-repo `HostedAppContractTest` is the canonical copyable example when the stubs need to implement every facade
method explicitly.

Recommended contract assertions:

- route path patterns are the paths you expect
- diagnostics show the expected capabilities
- invalid routes fail with a useful ownership message
- migration metadata uses a plugin-specific history table

## Full-Stack Hosted-App Tests

Use full-stack tests when you need real routing, filters, persistence, migrations, auth, or shell rendering:

```kotlin
val components =
    createServerComponents(
        config = testConfig.copy(platformMode = PlatformMode.PluginHostedApp),
        plugin = ReportsApp(),
    )

try {
    val response = components.app.http!!(Request(GET, "/"))
    assertThat(response, hasStatus(Status.OK))
} finally {
    components.persistence.close()
}
```

Do not manually call lower-level factories such as `createPersistenceComponents`, `createSecurityComponents`, and
`createWebComponents` in plugin tests unless the test is specifically about those internals. `createServerComponents`
is the production assembly path and keeps migrations, filters, routes, shell options, and hosted-app context aligned.

## Diagnostics

Startup validates plugin ownership before routes are mounted. Common failures:

| Message | Meaning | Fix |
|---|---|---|
| `pathPattern starting with '/'` | The route metadata is not an absolute path | Pass an absolute `pathPattern` to the route helper |
| `outside hosted app ... ownership` | The route or asset is outside manifest prefixes | Move the path, update `HostedAppManifest.ownership`, or use the matching route group |
| `Route conflicts detected` | A plugin route has the same method/path as a mounted platform route | Move the plugin route or exclude the colliding platform page set |

The plugin admin dashboard also exposes `HostedAppDiagnostics`: routes, capabilities, ownership prefixes, included
platform pages, stylesheets, and scripts.

## Public APIs To Prefer

To keep plugins isolated from platform internals, prefer:

- `HostedApp`, `HostedAppContributionContext`, `HostedAppContext`
- `HostedAppContract` for SPI-only tests
- `PluginMigrations` for plugin schema changes
- `createServerComponents(config, plugin)` for full-stack tests and launchers
- plugin-facing facades on `HostedAppContext`: `users`, `analytics`, `notifications`, `rendering`, `security`

Avoid depending on internal page factories, route registrars, repository implementations, `WebTest`, or low-level
component factories from plugin modules.
