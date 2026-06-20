# Theme Extension SPI Design

**Date:** 2026-06-20
**Status:** Draft
**Approach:** Theme registry + contributed CSS file (Approach 1)

## Motivation

Extensions (plugins) need the ability to contribute custom themes to the platform's
theme picker. Currently `ThemeCatalog` is a closed singleton listing only hardcoded
DaisyUI built-in theme IDs — there is no extension point and no way for a plugin
to add a custom branded theme. The CSS infrastructure already supports it: DaisyUI
themes are applied via `data-theme` on `<html>`, and extensions can already inject
stylesheets via the existing `assets.stylesheet()` mechanism. The gap is purely in
the catalog, validation, and picker wiring.

## Design

### Extension API Surface

A new `ExtensionThemeContributionRegistry` in platform-extension-api, registered
alongside the existing 10 registries in `ExtensionContributionContext`. Its API:

```kotlin
class ExtensionThemeContributionRegistry internal constructor() {
    private val themes = mutableListOf<ExtensionTheme>()

    fun theme(id: String, label: String, stylesheet: String) {
        require(id.isNotBlank()) { "Theme id must not be blank" }
        require(label.isNotBlank()) { "Theme label must not be blank" }
        require(themes.none { it.id == id }) { "Duplicate theme id within extension: $id" }
        themes += ExtensionTheme(id = id, label = label, stylesheet = stylesheet)
    }

    internal fun snapshot(): List<ExtensionTheme> = themes.toList()
}
```

The `ExtensionTheme` DTO lives in platform-extension-api:

```kotlin
data class ExtensionTheme(
    val id: String,
    val label: String,
    val stylesheet: String,
)
```

The stylesheet URL is included in the registration to make it atomic — a plugin
cannot register a theme without its CSS, removing the "forgot the stylesheet" footgun.
The URL must be owned by the extension (validated against its `assetPrefixes` at
composition time, consistent with `ExtensionContribution.kt:146-150`).

#### Usage from an extension

```kotlin
class MyExtension : PlatformExtension {
    override val id = "myext"

    override fun contribute(context: ExtensionContributionContext) {
        // Serve the theme CSS
        context.routes.staticAssets(
            "/extensions/myext/assets",
            ResourceLoader.Classpath("myext-static"),
            "MyExt static assets",
        )
        // Register the custom theme (atomic with its CSS)
        context.themes.theme("mybrand", "My Brand", "/extensions/myext/assets/theme.css")
    }
}
```

### Runtime Catalog Composition

`ThemeCatalog` stays unchanged (it is shared with the desktop `ThemeManager`).
A composed view is built in platform-web:

- `ExtensionContribution` gains a `themes: List<ExtensionTheme>` field, collected
  from the registry snapshot in `ExtensionContribution.from()`.
- `ShellConfig` carries `contributedThemes: List<ExtensionTheme>` (alongside existing
  `extensionOptions`).
- At composition time in `App.kt`, a merged valid-theme-ids set is computed:
  `ThemeCatalog.allThemeIds().toSet() + contributedThemes.map { it.id }`.
- This set is threaded through `ShellConfig` to:

| Call site | File:Line | Change |
|---|---|---|
| RequestContext.theme | `RequestContext.kt:66-69` | `isValidTheme` checks merged set |
| Cookie validator | `Filters.kt:375-378` | `preferenceCookie` predicate checks merged set |
| Picker options | `SidebarFactory.kt:38` | `allThemes()` → composed list (built-in + contributed mapped to ThemeOption) |

The picker appends contributed themes after the built-in section, optionally
separated by an `<optgroup>` or disabled separator. For v1, a simple flat list
with contributed themes at the bottom is sufficient.

### CSS Delivery

The contributed theme's stylesheet is collected at composition time (from all
registered `ExtensionTheme.stylesheet` URLs) and passed into the existing
`extensionStylesheets` → `LayoutHead.kte:32` `<link>` path. This means:

- The theme CSS loads site-wide for every page request (matching existing
  extension-stylesheet behavior).
- DaisyUI's `data-theme="mybrand"` on `<html>` activates the rules in that
  stylesheet — no new CSS mechanism needed.
- CSP already allows `style-src 'self' 'unsafe-inline'`, so DaisyUI's
  CSS-variable approach works without changes.
- Per-theme lazy loading is deferred as a later optimization.

### Conflict and Error Handling

#### Startup Conflict Detection (fail-loud)

At composition time (`ExtensionContribution.from()` or `ShellConfig.from()`),
a unified check runs:

```
contributed theme id + built-in id
```

If any contributed id collides with a DaisyUI built-in or with another extension's
theme id, composition fails with a clear error identifying the conflicting extension
and id. This matches the existing route-ownership validation pattern in
`ExtensionContribution.kt:127-151`.

**Design choice: flat namespace.** Theme ids are just strings (no `pluginId:` prefix).
The CSS author writes `[data-theme="mybrand"]` — the same value used in `data-theme`
on `<html>`. Simpler for plugin authors. Namespace separation is achieved through
startup conflict detection, not prefixes.

#### Input Validation

- Blank id or label → `require()` failure at registration time.
- Malformed stylesheet URL → caught by the existing `requirePathOwnedByManifest`
  ownership check at composition time.

#### Runtime Fallback (intentional, documented)

If a user's saved theme id (from `plt_users.theme VARCHAR(50)`) is no longer valid
— e.g., the plugin was removed — the existing fallback in `RequestContext.kt:68`
catches it and returns `"dark"`. This is the one intentional fallback in the system.
Theme selection is cosmetic, not a correctness path. No migration or garbage
collection is needed for orphaned theme ids.

### Data Flow Summary

**Startup:**
1. `App.kt:42` → `extension.contribute(ctx)` → plugin calls `ctx.themes.theme(...)` and `ctx.routes.staticAssets(...)`
2. `ExtensionContribution.from()` snapshots → `ExtensionContribution.themes: List<ExtensionTheme>`
3. `ShellConfig.from(contribution)` carries contributed themes
4. Conflict detection runs → fail-loud on duplicate
5. Merged valid-theme-ids set built once in `ShellConfig`
6. Contributed stylesheet URLs collected into extensionStylesheets

**Per request:**
7. `Filters.stateFilter` reads merged set from ShellConfig → passes through to `RequestContext`
8. `RequestContext.theme` validates against merged set; fallback `"dark"`
9. `SidebarFactory.buildThemeSelector` lists merged themes for picker
10. `LayoutHead.kte` emits contributed theme CSS `<link>` tags (existing path)

### Testing Strategy

All tests extend the existing `ExtensionContributionTest` and
`ExtensionRenderShellTest` patterns.

| Test | Scope |
|---|---|
| Registry snapshot correctness | Unit — registry snapshots empty, single, multiple themes |
| Merge produces valid composed set | Unit — built-in + contributed = merged set |
| Duplicate id within one extension | Unit — second `theme("same-id")` is accepted (snapshot has both); conflict detection at composition catches it |
| Duplicate id across extensions | Integration — two extensions register same id → composition throws with clear error |
| Duplicate id vs built-in | Integration — extension registers `"dark"` → composition throws |
| Blank id / label | Unit — `require` throws |
| Picker includes contributed theme | Integration — `SidebarSelector` HTML contains contributed theme option |
| Theme CSS `<link>` in `<head>` | Integration — response contains `<link href="/ext/my/assets/theme.css">` |
| Cookie accepts contributed theme | Integration — setting cookie with contributed id → validated, persisted |
| Stale theme fallback | Integration — user has contributed theme id, plugin not loaded → resolves to `"dark"` |
| Stylesheet URL ownership | Integration — URL outside `assetPrefixes` → composition throws |

## Scope Boundaries

**Not included (v1):**
- Per-theme lazy CSS loading (stylesheet always site-wide).
- Plugin control over the default theme (users still see "dark" initially).
- Reordering contributed themes in the picker (insertion-order append).
- `ThemeDefinition.kt` remains desktop-only, unchanged.

**Not affected:**
- Desktop `ThemeManager` — no changes to Swing/FlatLav theming.
- DaisyUI build chain — `input.css` stays `@plugin "daisyui"` with no config.
- Existing `assets.stylesheet()` mechanism — works alongside the new theme registry.
