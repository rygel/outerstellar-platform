# Composition Model Improvements: Single Hosted App Layout, Ownership, Diagnostics

**Issue:** #376
**Date:** 2026-05-27
**Status:** Approved
**Depends on:** PR #377 (Phase 1 composition model)

## Problem

Phase 1 (PR #377) established the route registry, `PlatformMode`, and `PlatformPageSets`. The next iteration narrows the model from "many extensions in a marketplace" to the expected deployment shape: one platform host normally accepts one extension adapter that owns the product experience.

That still keeps the useful WordPress-like idea: the extension contributes routes, layout, assets, admin sections, banners, and selective platform pages through a stable host contract. It does not require dependency ordering, activation state, or conflict resolution for multiple active extensions.

Four gaps remain:

1. **Extensions cannot replace the page layout.** `ExtensionHost` still gets the Outerstellar sidebar/topbar chrome, branding, and footer. Issue #376 explicitly requires an extension-driven product to provide its own renderer and layout without inheriting default platform chrome.
2. **Startup diagnostics hide excluded page sets.** The route table only shows registered routes. When `ExtensionHost` omits settings/contacts/search, there is no indication they were considered and intentionally excluded.
3. **`mountPlatformPages()` naming is confusing.** "Mount" suggests adding routes, but the semantic is "include these platform pages." The term `shell` is also overloaded — this spec uses `layout` for the page wrapper concept.
4. **Extension hooks are scattered through `App.kt`.** The host asks the adapter for routes, filters, admin sections, banners, text, layout, and included page sets from several assembly paths. The extension should contribute capabilities once, and the host should wire the collected contribution consistently.

## Decisions

- Extensions replace layout through an explicit `ExtensionLayoutRenderer`, not a dynamic JTE template string.
- Extensions may implement `ExtensionLayoutRenderer` with their own generated JTE templates, so template existence is checked by the extension build instead of string dispatch in the host.
- `PlatformExtension` is the primary contract name. Matching compatibility typealiases remain under `io.github.rygel.outerstellar.platform.web` for existing integrations.
- `ExtensionHostContext`, `ExtensionContribution`, and `ExtensionContributionContext` are the primary API names. `ExtensionContext`, `ExtensionContribution`, and `ExtensionContributionContext` remain as Kotlin source compatibility aliases.
- `ExtensionManifest` declares the extension id, label, version, optional platform version requirement, and route/asset ownership prefixes.
- Extension route registrations and shell asset URLs are validated against manifest ownership prefixes at startup.
- The platform does not introduce multi-extension dependency ordering, activation state, or marketplace conflict resolution; normal runtime composition is one extension adapter.
- Route registration stays explicit (`ExtensionRouteRegistration` wrapping) — verbose but clear.
- `mountPlatformPages()` renamed to `includePlatformPages()`.
- Extension hooks are registered through a single `contribute(context)` hook with typed registries.
- Extension hooks are collected into one `ExtensionContribution` during startup. `App.kt` consumes that contribution instead of repeatedly calling extension SPI methods from route, admin, and filter assembly.
- Existing per-extension methods remain as compatibility adapters into the typed registries.
- Internal `ShellView`/`ShellRenderer` names are not renamed in this iteration to limit churn.

## Design

### 1. Extension layout renderer

Add a small renderer contract:

```kotlin
fun interface ExtensionLayoutRenderer {
    fun render(shell: ShellView, content: Content): Content
}
```

Add a new method to `PlatformExtension`:

```kotlin
fun layoutRenderer(context: ExtensionHostContext): ExtensionLayoutRenderer? = null
```

When an extension returns a renderer, the platform routes all shell rendering through that renderer instead of `SidebarLayout.kte` or `TopbarLayout.kte`.

This is intentionally not `layoutTemplate(): String?`. The platform's production JTE renderer uses `JteClassRegistry`, which only contains platform-generated template classes. An extension template on the extension classpath is not automatically available to that registry. A renderer keeps ownership with the extension: the extension calls its own generated JTE template class and returns `Content`.

#### ShellView extension

Add one field to `ShellView`:

```kotlin
data class ShellView(
    // ... existing fields ...
    val extensionLayoutRenderer: ExtensionLayoutRenderer? = null,
)
```

`ShellRenderer.shell()` sets this from `ExtensionOptions.layoutRenderer` when available.

#### LayoutRouter.kte change

```jte
@if(shell.extensionLayoutRenderer != null)
    ${shell.extensionLayoutRenderer.render(shell, content)}
@elseif(shell.layoutStyle == "topbar")
    @template...TopbarLayout(shell = shell, content = content)
@else
    @template...SidebarLayout(shell = shell, content = content)
@endif
```

The extension renderer returns `gg.jte.Content`, so JTE can write it as user content. A extension using JTE can implement the renderer by delegating to its own generated layout class.

#### ExtensionOptions extension

Add `layoutRenderer` to `ExtensionOptions`:

```kotlin
data class ExtensionOptions(
    val navItems: List<ExtensionNavItem> = emptyList(),
    val textResolver: TextResolver? = null,
    val adminNavItems: List<AdminNavItem> = emptyList(),
    val layoutRenderer: ExtensionLayoutRenderer? = null,
)
```

`PlatformExtension` provides it via a new method:

```kotlin
fun layoutRenderer(context: ExtensionHostContext): ExtensionLayoutRenderer? = null
```

`ExtensionContribution.from(extension, platformMode, extensionHostContext)` reads `extension.layoutRenderer(extensionHostContext)` once and carries it inside `ExtensionOptions`.

#### What the extension renderer receives

The extension layout renderer receives the same `ShellView` data class with all fields: nav links, user info, CSRF token, banners, theme selectors, i18n labels, SEO metadata. The extension decides which fields to use and how to render them. This is a data contract, not a rendering contract.

Example shape:

```kotlin
override fun layoutRenderer(context: ExtensionHostContext): ExtensionLayoutRenderer =
    ExtensionLayoutRenderer { shell, content ->
        Content { output ->
            JteRepoQualityLayoutGenerated.render(output as HtmlTemplateOutput, null, shell, content)
        }
    }
```

A extension can copy either `SidebarLayout.kte` or `TopbarLayout.kte` as a starting point and customize from there.

#### PlatformTheme interaction

`PlatformTheme` provides `headInjections()` and `bodyInjections()` hooks, but those hooks are not wired into the current platform layout path yet. This iteration should not claim theme injection support beyond preserving the existing interface. A later phase can make `ShellView` carry head/body injection data so both platform and extension layouts can render it deliberately.

### 2. Extension contribution aggregation

Add a host-side aggregate for the extension's startup contributions:

```kotlin
data class ExtensionContribution(
    val mode: PlatformMode,
    val appLabel: String,
    val manifest: ExtensionManifest? = null,
    val includedPlatformPages: Set<PlatformPageSets> = emptySet(),
    val routeRegistrations: List<ExtensionRouteRegistration> = emptyList(),
    val filters: List<Filter> = emptyList(),
    val adminSections: List<AdminSection> = emptyList(),
    val bannerProviders: List<BannerProvider> = emptyList(),
    val options: ExtensionOptions = ExtensionOptions(),
)

data class ExtensionRouteRegistration(
    val route: Any?,
    val group: RouteGroup,
    val description: String,
    val pathPattern: String = description,
    val method: String = "*",
)

data class ExtensionAssets(
    val stylesheets: List<String> = emptyList(),
    val scripts: List<String> = emptyList(),
)

data class ExtensionManifest(
    val id: String,
    val appLabel: String = "Outerstellar",
    val version: String = "dev",
    val requiredPlatformVersion: String? = null,
    val ownership: ExtensionOwnership = ExtensionOwnership.forExtension(id),
)

data class ExtensionOwnership(
    val uiPrefixes: List<String>,
    val apiPrefixes: List<String>,
    val adminPrefixes: List<String>,
    val assetPrefixes: List<String>,
)
```

`ExtensionContribution.from(...)` creates a `ExtensionContributionContext`, adapts existing SPI methods into its typed registries, then calls the new hook:

```kotlin
interface PlatformExtension : ExtensionMigrationSource {
    val manifest: ExtensionManifest
        get() = ExtensionManifest(id = id, appLabel = appLabel)

    fun contribute(context: ExtensionContributionContext) {}

    // Compatibility methods, still supported.
    fun routeRegistrations(context: ExtensionHostContext): List<ExtensionRouteRegistration> = emptyList()
    fun includePlatformPages(): Set<PlatformPageSets> = emptySet()
    fun filters(context: ExtensionHostContext): List<Filter> = emptyList()
    fun adminSections(context: ExtensionHostContext): List<AdminSection> = emptyList()
    fun bannerProviders(context: ExtensionHostContext): List<BannerProvider> = emptyList()
    fun layoutRenderer(context: ExtensionHostContext): ExtensionLayoutRenderer? = null
}

interface PlatformExtension : PlatformExtension
```

The new authoring model is:

```kotlin
override fun contribute(context: ExtensionContributionContext) {
    context.platformPages.include(PlatformPageSets.SEARCH)
    context.routes.protectedUi(reportsRoute, "Reports", "/extension/reports")
    context.routes.staticAssets(
        "/extensions/reports/assets",
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
    context.assets.stylesheet("/extensions/reports/assets/reports.css")
    context.assets.script("/extensions/reports/assets/reports.js")
}
```

The typed registries are:

- `context.routes` for `ExtensionRouteRegistration`; convenience methods exist for `publicUi`, `protectedUi`, `api`, and `admin`
- `context.routes.staticAssets(...)` for extension-owned classpath static resources
- `context.platformPages` for included platform page sets
- `context.filters` for http4k filters
- `context.admin` for admin sections; a convenience `section(...)` builder creates the nav item and summary card together
- `context.banners` for banner providers
- `context.navigation` for extension nav items; `item(label, url, icon)` covers the common case
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
- extension route registration
- shell state filter (`ExtensionOptions`, banners)
- shell head asset rendering
- extension filter chain

Each registered route carries a `pathPattern` used for diagnostics and ownership validation. The extension manifest owns default prefixes derived from its id:

- UI: `/<id>` and `/extension/<id>`
- API: `/api/<id>`, `/api/extension/<id>`, `/api/v1/<id>`, and `/api/v1/extension/<id>`
- Admin: `/admin/<id>`
- Assets: `/extensions/<id>/assets`

The extension can override those prefixes through `ExtensionManifest.ownership`. Startup fails fast when a contributed route or stylesheet/script URL falls outside the declared ownership. This keeps the one-extension model explicit without adding a multi-extension conflict system.

This makes `App.kt` closer to an extension composition root: it still owns assembly order and security wrapping, but it does not rediscover extension capabilities in each subsystem.

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

In `App.kt`, after page-set inclusion is resolved, register every page set not included by `ExtensionHost` or `Headless`:

```kotlin
if (extensionMode != PlatformMode.FullPlatform) {
    val included = extension?.includePlatformPages() ?: emptySet()
    PlatformPageSets.entries
        .filter { it !in included }
        .forEach { registry.registerExcludedPageSet(it.pageSet.id) }
}
```

In `FullPlatform` mode, no excluded entries exist because all platform page sets are included.

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
| `PlatformExtension.kt` | `mountPlatformPages()` → `includePlatformPages()` |
| `App.kt` | `extension?.mountPlatformPages()` → `extension?.includePlatformPages()` |
| `ExtensionHostTest.kt` | All references |
| `PlatformPageSetsTest.kt` | Comment/doc references if any |

Binary-compatible: no consumers outside this repository yet, so a straight rename is safe.

### 5. Primary extension names

The public-facing model is now "extension host" rather than "extension marketplace":

| Primary surface | Compatibility surface |
|-----------------|-----------------------|
| `io.github.rygel.outerstellar.platform.extension.PlatformExtension` | `io.github.rygel.outerstellar.platform.web.PlatformExtension` typealias |
| `io.github.rygel.outerstellar.platform.extension.ExtensionHostContext` | `ExtensionContext` Kotlin typealias |
| `io.github.rygel.outerstellar.platform.extension.ExtensionContribution` | `io.github.rygel.outerstellar.platform.web.ExtensionContribution` typealias |
| `io.github.rygel.outerstellar.platform.extension.ExtensionContributionContext` | `io.github.rygel.outerstellar.platform.web.ExtensionContributionContext` typealias |

New host assembly entrypoints accept `PlatformExtension?`. Existing integrations can keep importing the compatibility typealiases from `io.github.rygel.outerstellar.platform.web` while migrating package names.

## Scope boundaries

**In scope:**
- `ExtensionLayoutRenderer` and `layoutRenderer()` method on `PlatformExtension`
- `extensionLayoutRenderer` field on `ShellView`
- `layoutRenderer` field on `ExtensionOptions`
- `ExtensionContribution` aggregate for extension startup capabilities
- `ExtensionContributionContext` and typed contribution registries
- `PlatformExtension.contribute(context)` hook
- `ExtensionManifest` and `ExtensionOwnership` startup validation
- Extension asset URL contribution and extension-owned static routes
- `LayoutRouter.kte` template dispatch
- `registerExcludedPageSet()` on `RouteRegistry`
- `formatTable()` excluded page-set section
- Rename `mountPlatformPages()` → `includePlatformPages()`

**Out of scope (future phases):**
- Module split (platform-web-kernel vs platform-ui)
- `ShellRenderer` → `LayoutRenderer` internal rename
- `ShellView` → `LayoutData` internal rename
- `ExtensionHostContext` slim-down
- Additional route registration DSLs beyond the typed registry
- Section-level layout overrides (sidebar, topbar, footer independently)
- Example extension / quick-start template
- Asset fingerprinting/content-hash helpers for extension files
- Headless-specific route filtering beyond current implementation

## Testing

- **ExtensionLayoutRendererTest:** Verify `ShellView.extensionLayoutRenderer` is populated when extension provides it; verify null when not provided; verify `ExtensionOptions` propagation; verify `LayoutRouter.kte` delegates to the extension renderer; verify extension stylesheet and script URLs render through `LayoutHead.kte`.
- **ExtensionContributionTest:** Verify empty-host defaults, legacy SPI adaptation, static route helpers, asset collection, manifest ownership validation, and new `contribute(context)` typed registry collection.
- **ExcludedDiagnosticsTest:** Verify `registerExcludedPageSet()` entries appear in `formatTable()` output; verify empty excluded list for `FullPlatform`; verify correct page set IDs for `ExtensionHost` with partial inclusion.
- **IncludePagesRenameTest:** Existing `ExtensionHostTest` tests updated to use `includePlatformPages()`.
- **Full reactor verify:** `mvn clean verify` on all non-desktop modules.

## Acceptance criteria

- [x] A extension returning `layoutRenderer()` gets its renderer used instead of the platform's SidebarLayout/TopbarLayout
- [x] The extension renderer receives the same `ShellView` data contract
- [x] Extension startup capabilities are collected into one `ExtensionContribution`
- [x] New extensions can register capabilities through `contribute(context)` and typed registries
- [x] Extensions can contribute stylesheet/script URLs and extension-owned static routes
- [x] `PlatformExtension`/`ExtensionContribution` are the primary names with `PlatformExtension`/`ExtensionContribution` compatibility
- [x] Extension manifests validate route and asset ownership prefixes at startup
- [ ] `FullPlatform` mode is completely unaffected (no layout template, no disabled entries)
- [x] Startup route table shows excluded page sets in `ExtensionHost` and `Headless` modes
- [x] `includePlatformPages()` replaces `mountPlatformPages()` everywhere
- [x] Focused extension composition tests pass
- [x] Full reactor `mvn clean verify` passes
