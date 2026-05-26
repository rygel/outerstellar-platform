# Banner SPI Design

**Date:** 2026-05-24
**Issue:** #260
**Status:** Approved

## Problem

Private modules (e.g. `private-announcements`) and future public plugins need a way to inject banners into the platform layout, rendered server-side on every page. There is currently no mechanism for this.

## Design

### Types (platform-core)

```kotlin
package io.github.rygel.outerstellar.platform.banner

enum class BannerSeverity {
    CRITICAL,
    WARNING,
    INFO,
    SCHEDULED_MAINTENANCE
}

data class Banner(
    val id: String,
    val title: String,
    val body: String,
    val severity: BannerSeverity = BannerSeverity.INFO,
    val isDismissible: Boolean = true,
    val dismissUrl: String? = null
)

interface BannerProvider {
    fun getBanners(userId: UUID, userRole: String): List<Banner>
}
```

- `id` must be unique across all providers (providers should namespace, e.g. `"announcements:42"`).
- `body` is rendered inline in the template — providers must sanitize their content.
- `dismissUrl` is null for sticky (non-dismissible) banners. When non-null, the platform renders an HTMX dismiss button targeting this URL.
- Dismissal is provider-owned: the platform never stores banner state. Each provider handles its own dismiss endpoint and persistence.

### PlatformPlugin extension

```kotlin
interface PlatformPlugin : PluginMigrationSource {
    // ... existing members ...
    fun bannerProviders(context: PluginContext): List<BannerProvider> = emptyList()
}
```

Default implementation returns empty list — existing plugins are unaffected. New plugins override this to register their providers.

### ShellView

Add to `ShellView` in `ViewModels.kt`:

```kotlin
val banners: List<Banner> = emptyList()
```

### WebContext

`WebContext.shell()` calls each registered `BannerProvider`, merges results, sorts by severity ordinal (critical first), and passes to `ShellView`.

### Wiring

1. `PlatformPlugin.bannerProviders(context)` is called in `ServerComponents` during startup.
2. Providers are stored and passed through the filter chain to `WebContext`.
3. `WebContext` receives `List<BannerProvider>` as a constructor parameter.

### Layout templates

Both `SidebarLayout.kte` and `TopbarLayout.kte` render a new partial between the header and `<main>`:

```jte
@if(!shell.banners.isEmpty())
    @template.io.github.rygel.outerstellar.platform.web.components.Banners(banners = shell.banners)
@endif
```

### Banners.kte partial

A new JTE component template that:
- Iterates over banners (already sorted by severity)
- Maps severity to DaisyUI alert classes: `CRITICAL` → `alert-error`, `WARNING` → `alert-warning`, `INFO` → `alert-info`, `SCHEDULED_MAINTENANCE` → `alert-accent`
- For dismissible banners with a `dismissUrl`, renders an HTMX button: `hx-post="${banner.dismissUrl}" hx-target="closest .alert"` with `hx-swap="outerHTML"`
- Non-dismissible banners render without a dismiss button
- Wrapped in a container div with `aria-live="polite"` for accessibility

### No platform dismiss endpoint

The platform provides no `/api/v1/banners/{id}/dismiss` route. Each `BannerProvider` owns its own dismiss endpoint and persistence. This keeps the SPI minimal and allows providers to implement different dismissal semantics (one-time, time-limited, role-based).

## Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Provider naming | `BannerProvider` | Consistent with existing `SearchProvider` in platform-core |
| Dismissal ownership | Provider-owned | Different banner types need different semantics; motivating use case already has its own persistence |
| Registration mechanism | Via `PlatformPlugin` | Follows existing pattern for routes, adminSections, navItems |
| Banner ordering | Severity ordinal (critical first) | Most urgent banners should be seen first |
| Body content | Provider-sanitized | Platform doesn't sanitize — providers control their own content |

## Out of scope

- Client-side banner management UI
- Banner scheduling/scheduling UI
- Per-user banner preferences
- Platform-owned dismissed_banners table
- Banner analytics/impressions tracking
