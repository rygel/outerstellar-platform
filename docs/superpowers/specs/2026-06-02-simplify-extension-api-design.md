# Simplify the Extension Developer Experience

**Date:** 2026-06-02
**Issue:** [#448](https://github.com/rygel/outerstellar-platform/issues/448)
**Status:** Design approved

## Problem

Building an extension requires understanding too many low-level concepts. The `PlatformExtension` interface has 15 methods/properties, many deprecated with overlapping paths. JTE template registration requires 5 manual steps. Adding a page requires understanding `bindContract`, `ContractRoute`, `ViewModel`, and route registration.

## Design

Four changes. No new frameworks, no DSLs, no annotations. Just removing complexity and adding one convenience method.

### 1. Clean up `PlatformExtension` interface

Remove 8 deprecated/redundant methods. Keep only what matters:

```kotlin
interface PlatformExtension {
    val id: String
    val appLabel: String get() = "Outerstellar"
    val mode: PlatformMode get() = PlatformMode.FullPlatform
    val manifest: ExtensionManifest get() = ExtensionManifest(id = id, appLabel = appLabel)
    val migrations: ExtensionMigrations? get() = null
    val textResolver: TextResolver? get() = null

    fun contribute(context: ExtensionContributionContext) {}
}
```

**Removed:**
- `routeRegistrations()` — use `context.routes.*` inside `contribute()`
- `includePlatformPages()` — use `context.platformPages.include()`
- `layoutRenderer()` — use `context.layout.replaceWith()`
- `filters()` — use `context.filters.add()`
- `adminSections()` — use `context.admin.section()`
- `bannerProviders()` — use `context.banners.provider()`
- `templateOverrides()` — never wired, unused
- `migrationLocation`, `migrationHistoryTable`, `migrationNames` — use `migrations`

**Internal change:** `ExtensionContribution.from()` stops calling the removed methods. Only `contribute()` is invoked.

### 2. Add `page()` convenience method

Add to `ExtensionRouteContributionRegistry`:

```kotlin
fun page(
    path: String,
    templateName: String,
    description: String = path,
    group: RouteGroup = RouteGroup.ProtectedUi,
    model: (Request) -> Map<String, Any> = { emptyMap() },
)

fun publicPage(
    path: String,
    templateName: String,
    description: String = path,
    model: (Request) -> Map<String, Any> = { emptyMap() },
) = page(path, templateName, description, RouteGroup.PublicUi, model)
```

Extension usage:

```kotlin
context.routes.publicPage("/", "starter/index", "Home") { req ->
    mapOf("version" to host.app.version)
}
```

**Internal requirement:** The route registry needs a `host: ExtensionHostContext` reference (passed via constructor) so it can call `host.rendering.renderer()`.

**Existing methods stay:** `publicUi()`, `protectedUi()`, `api()`, `admin()`, `staticAssets()`, `register()` — for full http4k control.

### 3. Zero-config JTE via extension parent POM

New module: `outerstellar-platform-extension-parent`

Extension authors inherit from this parent instead of manually configuring JTE:

```xml
<parent>
    <groupId>io.github.rygel</groupId>
    <artifactId>outerstellar-platform-extension-parent</artifactId>
    <version>3.6.8</version>
</parent>
```

The parent provides:
- `pluginManagement` for `jte-maven-plugin` with `JteClassRegistryExtension` + `NativeResourcesExtension` pre-configured
- `pluginManagement` for `kotlin-maven-plugin` with correct JVM target
- `dependencyManagement` for `extension-api`, `jte-runtime`, `jte-kotlin`
- GitHub Packages repository declaration

Extension authors drop `.jte` files in `src/main/jte/` and it works.

### 4. Clean up `ExtensionHostContext`

Remove deprecated aliases:

| Removed | Replacement |
|---------|-------------|
| `renderer` | `rendering.renderer` |
| `config` | `app` |
| `apiKeyService` | `security.apiKeys` |
| `oauthService` | `security.oauth` |
| `userRepository` | `users` |
| `notificationService` | `notifications` |
| `typealias ExtensionContext` | `ExtensionHostContext` directly |

## Files touched

| File | Change |
|------|--------|
| `platform-extension-api/.../PlatformExtensionApi.kt` | Remove deprecated methods, deprecated context aliases, `ExtensionContext` typealias |
| `platform-extension-api/.../ExtensionContributionContext.kt` | Add `host` to route registry constructor, add `page()` and `publicPage()` methods |
| `platform-extension-api/.../ExtensionContribution.kt` | Remove calls to deprecated extension methods in `from()` |
| New: `outerstellar-platform-extension-parent/pom.xml` | Parent POM with JTE plugin config |
| `starter-extension-app/` | Inherit from new parent, simplify pom.xml, demonstrate `page()` |
| Root `pom.xml` | Add new module to reactor |

## Migration for existing extensions

Breaking changes. Extension authors must:
1. Move any `routeRegistrations()`, `filters()`, `adminSections()`, `bannerProviders()`, `layoutRenderer()`, `includePlatformPages()` logic into `contribute()`
2. Replace deprecated host context aliases with grouped field access
3. Optionally switch parent POM for zero-config JTE

## What doesn't change

- `ExtensionContributionContext` and all 8 sub-registries
- `ExtensionContribution`, `ExtensionDiagnostics`, `ExtensionContract` test helper
- Route ownership validation
- Host service interfaces (`HostUsers`, `HostAnalytics`, `HostNotifications`, `HostRendering`, `HostApiKeys`, `HostOAuth`, `HostSecurity`)
- `PlatformMode`, `RouteGroup`, `RouteOwner`, `RouteRegistry`
- The platform's internal route assembly in `App.kt`
