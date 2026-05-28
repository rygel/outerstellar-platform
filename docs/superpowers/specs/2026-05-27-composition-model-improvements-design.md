# Composition Model Improvements: Single Hosted App Layout, Ownership, Diagnostics

**Issue:** #376
**Date:** 2026-05-27
**Status:** Approved
**Depends on:** PR #377 (Phase 1 composition model)

## Problem

Phase 1 (PR #377) established the route registry, `PlatformMode`, and `PlatformPageSets`. The next iteration narrows the model from "many plugins in a marketplace" to the expected deployment shape: one platform host normally accepts one hosted app adapter that owns the product experience.

That still keeps the useful WordPress-like idea: the hosted app contributes routes, layout, assets, admin sections, banners, and selective platform pages through a stable host contract. It does not require dependency ordering, activation state, or conflict resolution for multiple active plugins.

Four gaps remain:

1. **Hosted apps cannot replace the page layout.** A `PluginHostedApp` still gets the Outerstellar sidebar/topbar chrome, branding, and footer. Issue #376 explicitly requires: "a plugin-hosted product should be able to provide its own renderer and layout without inheriting default platform chrome."
2. **Startup diagnostics hide excluded page sets.** The route table only shows registered routes. When `PluginHostedApp` omits settings/contacts/search, there is no indication they were considered and intentionally excluded.
3. **`mountPlatformPages()` naming is confusing.** "Mount" suggests adding routes, but the semantic is "include these platform pages." The term `shell` is also overloaded — this spec uses `layout` for the page wrapper concept.
4. **Hosted app extension points are scattered through `App.kt`.** The host asks the adapter for routes, filters, admin sections, banners, text, layout, and included page sets from several assembly paths. The hosted app should contribute capabilities once, and the host should wire the collected contribution consistently.

## Decisions

- Hosted apps replace layout through an explicit `PluginLayoutRenderer`, not a dynamic JTE template string.
- Hosted apps may implement `PluginLayoutRenderer` with their own generated JTE templates, so template existence is checked by the hosted app build instead of string dispatch in the host.
- `HostedApp` is the primary contract name. `PlatformPlugin` remains as a compatibility interface for existing integrations.
- `HostedAppContext`, `HostedAppContribution`, and `HostedAppContributionContext` are the primary API names. `PluginContext`, `PluginContribution`, and `PluginContributionContext` remain as Kotlin source compatibility aliases.
- `HostedAppManifest` declares the hosted app id, label, version, optional platform version requirement, and route/asset ownership prefixes.
- Hosted app route registrations and shell asset URLs are validated against manifest ownership prefixes at startup.
- The platform does not introduce multi-plugin dependency ordering, activation state, or marketplace conflict resolution; normal runtime composition is one hosted app adapter.
- Route registration stays explicit (`PluginRouteRegistration` wrapping) — verbose but clear.
- `mountPlatformPages()` renamed to `includePlatformPages()`.
- Hosted app extension points are registered through a single `contribute(context)` hook with typed registries.
- Hosted app extension points are collected into one `HostedAppContribution` during startup. `App.kt` consumes that contribution instead of repeatedly calling hosted app SPI methods from route, admin, and filter assembly.
- Existing per-extension methods remain as compatibility adapters into the typed registries.
- Internal `ShellView`/`ShellRenderer` names are not renamed in this iteration to limit churn.

## Design

### 1. Plugin layout renderer

Add a small renderer contract:

```kotlin
fun interface PluginLayoutRenderer {
    fun render(shell: ShellView, content: Content): Content
}
```

Add a new method to `HostedApp`:

```kotlin
fun layoutRenderer(context: HostedAppContext): PluginLayoutRenderer? = null
```

When a plugin returns a renderer, the platform routes all shell rendering through that renderer instead of `SidebarLayout.kte` or `TopbarLayout.kte`.

This is intentionally not `layoutTemplate(): String?`. The platform's production JTE renderer uses `JteClassRegistry`, which only contains platform-generated template classes. A plugin template on the plugin classpath is not automatically available to that registry. A renderer keeps ownership with the plugin: the plugin calls its own generated JTE template class and returns `Content`.

#### ShellView extension

Add one field to `ShellView`:

```kotlin
data class ShellView(
    // ... existing fields ...
    val pluginLayoutRenderer: PluginLayoutRenderer? = null,
)
```

`ShellRenderer.shell()` sets this from `PluginOptions.layoutRenderer` when available.

#### LayoutRouter.kte change

```jte
@if(shell.pluginLayoutRenderer != null)
    ${shell.pluginLayoutRenderer.render(shell, content)}
@elseif(shell.layoutStyle == "topbar")
    @template...TopbarLayout(shell = shell, content = content)
@else
    @template...SidebarLayout(shell = shell, content = content)
@endif
```

The plugin renderer returns `gg.jte.Content`, so JTE can write it as user content. A plugin using JTE can implement the renderer by delegating to its own generated layout class.

#### PluginOptions extension

Add `layoutRenderer` to `PluginOptions`:

```kotlin
data class PluginOptions(
    val navItems: List<PluginNavItem> = emptyList(),
    val textResolver: TextResolver? = null,
    val adminNavItems: List<AdminNavItem> = emptyList(),
    val layoutRenderer: PluginLayoutRenderer? = null,
)
```

`HostedApp` provides it via a new method:

```kotlin
fun layoutRenderer(context: HostedAppContext): PluginLayoutRenderer? = null
```

`HostedAppContribution.from(plugin, fallbackMode, hostedAppContext)` reads `plugin.layoutRenderer(hostedAppContext)` once and carries it inside `PluginOptions`.

#### What the plugin renderer receives

The hosted app's layout renderer receives the same `ShellView` data class with all fields: nav links, user info, CSRF token, banners, theme selectors, i18n labels, SEO metadata. The hosted app decides which fields to use and how to render them. This is a data contract, not a rendering contract.

Example shape:

```kotlin
override fun layoutRenderer(context: HostedAppContext): PluginLayoutRenderer =
    PluginLayoutRenderer { shell, content ->
        Content { output ->
            JteRepoQualityLayoutGenerated.render(output as HtmlTemplateOutput, null, shell, content)
        }
    }
```

A plugin can copy either `SidebarLayout.kte` or `TopbarLayout.kte` as a starting point and customize from there.

#### PlatformTheme interaction

`PlatformTheme` provides `headInjections()` and `bodyInjections()` hooks, but those hooks are not wired into the current platform layout path yet. This iteration should not claim theme injection support beyond preserving the existing interface. A later phase can make `ShellView` carry head/body injection data so both platform and plugin layouts can render it deliberately.

### 2. Hosted app contribution aggregation

Add a host-side aggregate for the hosted app's startup contributions:

```kotlin
data class HostedAppContribution(
    val mode: PlatformMode,
    val appLabel: String,
    val manifest: HostedAppManifest? = null,
    val includedPlatformPages: Set<PlatformPageSets> = emptySet(),
    val routeRegistrations: List<PluginRouteRegistration> = emptyList(),
    val filters: List<Filter> = emptyList(),
    val adminSections: List<AdminSection> = emptyList(),
    val bannerProviders: List<BannerProvider> = emptyList(),
    val options: PluginOptions = PluginOptions(),
)

data class PluginRouteRegistration(
    val route: Any?,
    val group: RouteGroup,
    val description: String,
    val pathPattern: String = description,
    val method: String = "*",
)

data class PluginAssets(
    val stylesheets: List<String> = emptyList(),
    val scripts: List<String> = emptyList(),
)

data class HostedAppManifest(
    val id: String,
    val appLabel: String = "Outerstellar",
    val version: String = "dev",
    val requiredPlatformVersion: String? = null,
    val ownership: HostedAppOwnership = HostedAppOwnership.forPlugin(id),
)

data class HostedAppOwnership(
    val uiPrefixes: List<String>,
    val apiPrefixes: List<String>,
    val adminPrefixes: List<String>,
    val assetPrefixes: List<String>,
)
```

`HostedAppContribution.from(...)` creates a `HostedAppContributionContext`, adapts existing SPI methods into its typed registries, then calls the new hook:

```kotlin
interface HostedApp : PluginMigrationSource {
    val manifest: HostedAppManifest
        get() = HostedAppManifest(id = id, appLabel = appLabel)

    fun contribute(context: HostedAppContributionContext) {}

    // Compatibility methods, still supported.
    fun routeRegistrations(context: HostedAppContext): List<PluginRouteRegistration> = emptyList()
    fun includePlatformPages(): Set<PlatformPageSets> = emptySet()
    fun filters(context: HostedAppContext): List<Filter> = emptyList()
    fun adminSections(context: HostedAppContext): List<AdminSection> = emptyList()
    fun bannerProviders(context: HostedAppContext): List<BannerProvider> = emptyList()
    fun layoutRenderer(context: HostedAppContext): PluginLayoutRenderer? = null
}

interface PlatformPlugin : HostedApp
```

The new authoring model is:

```kotlin
override fun contribute(context: HostedAppContributionContext) {
    context.platformPages.include(PlatformPageSets.SEARCH)
    context.routes.protectedUi(reportsRoute, "Reports", "/plugin/reports")
    context.routes.staticAssets(
        "/plugins/reports/assets",
        ResourceLoader.Classpath("reports-static"),
        "Reports static assets",
    )
    context.admin.section(
        id = "reports",
        navLabel = "Reports",
        navIcon = "bar-chart",
        route = reportsAdminRoute,
        linkUrl = "/admin/reports",
    )
    context.banners.provider(reportsBannerProvider)
    context.navigation.item("Reports", "/reports", "bar-chart")
    context.layout.replaceWith(reportsLayoutRenderer)
    context.assets.stylesheet("/plugins/reports/assets/reports.css")
    context.assets.script("/plugins/reports/assets/reports.js")
}
```

The typed registries are:

- `context.routes` for `PluginRouteRegistration`; convenience methods exist for `publicUi`, `protectedUi`, `api`, and `admin`
- `context.routes.staticAssets(...)` for plugin-owned classpath static resources
- `context.platformPages` for included platform page sets
- `context.filters` for http4k filters
- `context.admin` for admin sections; a convenience `section(...)` builder creates the nav item and summary card together
- `context.banners` for banner providers
- `context.navigation` for plugin nav items; `item(label, url, icon)` covers the common case
- `context.layout` for replacing the shell layout renderer
- `context.assets` for stylesheet and script URLs rendered in the shell head

The compatibility adapter registers:

- `includePlatformPages()`
- `routeRegistrations(context)`
- `filters(context)`
- `adminSections(context)`
- `bannerProviders(context)`
- `layoutRenderer(context)`
- `textResolver`

Then `contribute(context)` can add new-style contributions on top.

The host then passes the contribution into:

- API/UI/component/admin route registration
- plugin route registration
- shell state filter (`PluginOptions`, banners)
- shell head asset rendering
- plugin filter chain

Each registered route carries a `pathPattern` used for diagnostics and ownership validation. The hosted app manifest owns default prefixes derived from its id:

- UI: `/<id>` and `/plugin/<id>`
- API: `/api/<id>`, `/api/plugin/<id>`, `/api/v1/<id>`, and `/api/v1/plugin/<id>`
- Admin: `/admin/<id>`
- Assets: `/plugins/<id>/assets`

The hosted app can override those prefixes through `HostedAppManifest.ownership`. Startup fails fast when a contributed route or stylesheet/script URL falls outside the declared ownership. This keeps the one-hosted-app model explicit without adding a multi-plugin conflict system.

This makes `App.kt` closer to a hosted-app composition root: it still owns assembly order and security wrapping, but it does not rediscover hosted app capabilities in each subsystem.

### 3. Excluded page-set diagnostics

#### RouteRegistry changes

Add a `registerExcludedPageSet(id: String)` method:

```kotlin
class RouteRegistry {
    private val entries = mutableListOf<RegisteredRoute>()
    private val excludedPageSets = mutableListOf<String>()

    fun register(entry: RegisteredRoute) { entries.add(entry) }
    fun registerAll(entries: List<RegisteredRoute>) { this.entries.addAll(entries) }
    fun registerExcludedPageSet(id: String) { excludedPageSets.add(id) }
    // ... existing methods ...
}
```

#### When to call registerExcludedPageSet

In `App.kt`, after page-set inclusion is resolved, register every page set not included by `PluginHostedApp` or `HeadlessKernel`:

```kotlin
if (pluginMode != PlatformMode.FullPlatformApp) {
    val included = plugin?.includePlatformPages() ?: emptySet()
    PlatformPageSets.entries
        .filter { it !in included }
        .forEach { registry.registerExcludedPageSet(it.pageSet.id) }
}
```

In `FullPlatformApp` mode, no excluded entries exist because all platform page sets are included.

#### formatTable changes

```text
Platform Route Table (42 routes):
  GET    /                              PlatformUi          [PublicUi]
  GET    /auth/login                    PlatformKernel       [PublicUi]
  GET    /static                        PlatformKernel       [Static]
  ...
  Excluded page sets: admin, contacts, dev-dashboard, notifications, profile, search, settings
```

When no excluded entries exist, omit the line entirely.

### 4. Rename mountPlatformPages → includePlatformPages

Rename across:

| File | Change |
|------|--------|
| `PlatformPlugin.kt` | `mountPlatformPages()` → `includePlatformPages()` |
| `App.kt` | `plugin?.mountPlatformPages()` → `plugin?.includePlatformPages()` |
| `PluginHostedAppTest.kt` | All references |
| `PlatformPageSetsTest.kt` | Comment/doc references if any |

Binary-compatible: no consumers outside this repository yet, so a straight rename is safe.

### 5. Primary hosted-app names

The public-facing model is now "hosted app" rather than "plugin marketplace":

| Primary name | Compatibility name |
|--------------|--------------------|
| `HostedApp` | `PlatformPlugin` interface extends it |
| `HostedAppContext` | `PluginContext` Kotlin typealias |
| `HostedAppContribution` | `PluginContribution` Kotlin typealias |
| `HostedAppContributionContext` | `PluginContributionContext` Kotlin typealias |

New host assembly entrypoints accept `HostedApp?`. Existing `PlatformPlugin` implementors still satisfy that parameter because `PlatformPlugin : HostedApp`.

## Scope boundaries

**In scope:**
- `PluginLayoutRenderer` and `layoutRenderer()` method on `HostedApp`
- `pluginLayoutRenderer` field on `ShellView`
- `layoutRenderer` field on `PluginOptions`
- `HostedAppContribution` aggregate for hosted app startup capabilities
- `HostedAppContributionContext` and typed contribution registries
- `HostedApp.contribute(context)` hook
- `HostedAppManifest` and `HostedAppOwnership` startup validation
- Plugin asset URL contribution and plugin-owned static routes
- `LayoutRouter.kte` template dispatch
- `registerExcludedPageSet()` on `RouteRegistry`
- `formatTable()` excluded page-set section
- Rename `mountPlatformPages()` → `includePlatformPages()`

**Out of scope (future phases):**
- Module split (platform-web-kernel vs platform-ui)
- `ShellRenderer` → `LayoutRenderer` internal rename
- `ShellView` → `LayoutData` internal rename
- `HostedAppContext` slim-down
- Additional route registration DSLs beyond the typed registry
- Section-level layout overrides (sidebar, topbar, footer independently)
- Example plugin / quick-start template
- Asset fingerprinting/content-hash helpers for plugin files
- HeadlessKernel-specific route filtering beyond current implementation

## Testing

- **PluginLayoutRendererTest:** Verify `ShellView.pluginLayoutRenderer` is populated when plugin provides it; verify null when not provided; verify `PluginOptions` propagation; verify `LayoutRouter.kte` delegates to the plugin renderer; verify plugin stylesheet and script URLs render through `LayoutHead.kte`.
- **PluginContributionTest:** Verify empty-host defaults, legacy SPI adaptation, static route helpers, asset collection, manifest ownership validation, and new `contribute(context)` typed registry collection.
- **ExcludedDiagnosticsTest:** Verify `registerExcludedPageSet()` entries appear in `formatTable()` output; verify empty excluded list for `FullPlatformApp`; verify correct page set IDs for `PluginHostedApp` with partial inclusion.
- **IncludePagesRenameTest:** Existing `PluginHostedAppTest` tests updated to use `includePlatformPages()`.
- **Full reactor verify:** `mvn clean verify` on all non-desktop modules.

## Acceptance criteria

- [x] A plugin returning `layoutRenderer()` gets its renderer used instead of the platform's SidebarLayout/TopbarLayout
- [x] The plugin renderer receives the same `ShellView` data contract
- [x] Hosted app startup capabilities are collected into one `HostedAppContribution`
- [x] New plugins can register capabilities through `contribute(context)` and typed registries
- [x] Plugins can contribute stylesheet/script URLs and plugin-owned static routes
- [x] `HostedApp`/`HostedAppContribution` are the primary names with `PlatformPlugin`/`PluginContribution` compatibility
- [x] Hosted app manifests validate route and asset ownership prefixes at startup
- [ ] `FullPlatformApp` mode is completely unaffected (no layout template, no disabled entries)
- [x] Startup route table shows excluded page sets in `PluginHostedApp` and `HeadlessKernel` modes
- [x] `includePlatformPages()` replaces `mountPlatformPages()` everywhere
- [x] Focused plugin composition tests pass
- [x] Full reactor `mvn clean verify` passes
