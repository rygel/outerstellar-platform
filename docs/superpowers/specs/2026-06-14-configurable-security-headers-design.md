# Design: Configurable Security Response Headers

**Date:** 2026-06-14
**Branch:** feat/501-configurable-security-headers
**Source:** GitHub issue #501 — Make security response headers (Permissions-Policy, CORS, etc.) configurable per-route

---

## Problem

The platform hard-codes five security response headers in `Filters.kt:194-212` with no way to override them from configuration or extension code:

| Header | Hard-coded value | Configurable? |
|---|---|---|
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=()` | NO |
| `X-Content-Type-Options` | `nosniff` | NO |
| `X-Frame-Options` | `DENY` | NO |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | NO |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | NO |
| `Content-Security-Policy` | (default policy with nonce) | YES via `cspPolicy` / `CSP_POLICY` |

The CORS filter at `Filters.kt:176-192` accepts `allowedOrigins: String` from `corsOrigins` / `CORSORIGINS`, but only as a single global value.

**Concrete incident:** the outerstellar-website community map feature uses Leaflet, which loads map tiles from `https://*.tile.openstreetmap.org`. The CSP `img-src 'self' data:` blocks these images. The Permissions-Policy `geolocation=()` blocks browser geolocation. Extensions cannot override either header because `FilterChainFactory.kt:34` places `securityHeaders` BEFORE extension filters (line 66-68) in the chain; `securityHeaders` applies its headers on the response AFTER extensions, overwriting any header an extension sets.

---

## Solution

1. Add a `SecurityHeadersConfig` data class to `AppConfig` exposing all five currently-hard-coded headers as configurable strings, with defaults that match today's values (zero-config backward compatibility).
2. Add a `RouteHeaderOverride` data class and `perRouteOverrides: List<RouteHeaderOverride>` for per-path overrides using Ant-style glob patterns.
3. Modify `Filters.securityHeaders` to accept `SecurityHeadersConfig` and resolve per-route overrides based on the request path.
4. Modify `Filters.cors` to accept per-route CORS origin overrides.
5. Add a `PathPatternMatcher` utility for Ant-style glob matching.

---

## Design Decisions (confirmed with user)

### Filter chain order: Per-route wins

Per-route overrides are resolved INSIDE the `securityHeaders` filter, which sits at position 5 in the chain — above extension filters (position 11+). On the response path, `securityHeaders` applies its headers after extensions have already modified the response. By resolving per-route overrides within `securityHeaders`, the effective order is:

```
defaults (from config) → extensions (modify response) → securityHeaders applies final headers (base or per-route)
```

Per-route overrides always win. An extension cannot accidentally regress a declared security policy. This matches the issue's stated intent: "defaults → per-route overrides → extension-supplied headers."

### Path pattern syntax: Ant-style glob

- `*` matches exactly one path segment (no slashes)
- `**` matches any number of path segments (including zero)
- A plain string with no wildcards is an exact match
- Matching is case-sensitive
- First matching override in the list wins (ordered, not map)

Examples:

| Pattern | Matches | Does NOT match |
|---|---|---|
| `/map/**` | `/map`, `/map/europe`, `/map/europe/france` | `/api/map` |
| `/api/*/users` | `/api/v1/users` | `/api/v1/admin/users` |
| `/static/*.css` | `/static/site.css` | `/static/sub/site.css` |
| `/login` | `/login` only | `/login/callback` |

Implementation: ~50 LOC, no external dependency.

---

## Architecture

### New types in `platform-core`

```kotlin
data class SecurityHeadersConfig(
    val permissionsPolicy: String = "camera=(), microphone=(), geolocation=()",
    val referrerPolicy: String = "strict-origin-when-cross-origin",
    val xFrameOptions: String = "DENY",
    val xContentTypeOptions: String = "nosniff",
    val strictTransportSecurity: String = "max-age=31536000; includeSubDomains",
    val perRouteOverrides: List<RouteHeaderOverride> = emptyList(),
)

data class RouteHeaderOverride(
    val pattern: String,
    val permissionsPolicy: String? = null,
    val referrerPolicy: String? = null,
    val xFrameOptions: String? = null,
    val xContentTypeOptions: String? = null,
    val strictTransportSecurity: String? = null,
    val csp: String? = null,
    val corsAllowedOrigins: List<String>? = null,
)
```

All fields in `RouteHeaderOverride` are nullable. `null` means "do not override this header for this pattern." A non-null value replaces the base config value for matching paths. An empty string for `strictTransportSecurity` means "do not emit HSTS" (lets dev mode skip it).

### AppConfig changes

Add `securityHeaders: SecurityHeadersConfig = SecurityHeadersConfig()` to `AppConfig`.

Existing top-level fields `cspPolicy` and `corsOrigins` remain unchanged for backward compatibility. The `securityHeaders` filter receives both.

### Env var mapping

| YAML Key | Env Var | Default |
|---|---|---|
| `securityHeaders.permissionsPolicy` | `PERMISSIONS_POLICY` | `camera=(), microphone=(), geolocation=()` |
| `securityHeaders.referrerPolicy` | `REFERRER_POLICY` | `strict-origin-when-cross-origin` |
| `securityHeaders.xFrameOptions` | `X_FRAME_OPTIONS` | `DENY` |
| `securityHeaders.xContentTypeOptions` | `X_CONTENT_TYPE_OPTIONS` | `nosniff` |
| `securityHeaders.strictTransportSecurity` | `STRICT_TRANSPORT_SECURITY` | `max-age=31536000; includeSubDomains` |

Per-route overrides are YAML-only (too complex for env var representation). The existing `CSP_POLICY` and `CORSORIGINS` env vars continue to work unchanged.

### YAML structure

```yaml
securityHeaders:
  permissionsPolicy: "camera=(), microphone=(), geolocation=()"
  referrerPolicy: "strict-origin-when-cross-origin"
  xFrameOptions: "DENY"
  xContentTypeOptions: "nosniff"
  strictTransportSecurity: "max-age=31536000; includeSubDomains"
  perRouteOverrides:
    - pattern: "/map/**"
      permissionsPolicy: "geolocation=(self), camera=(), microphone=()"
      csp: "default-src 'self'; script-src 'self' {nonce}; style-src 'self' 'unsafe-inline'; font-src 'self'; connect-src 'self' wss:; img-src 'self' data: https://*.tile.openstreetmap.org; base-uri 'self'; form-action 'self'"
    - pattern: "/api/external/**"
      corsAllowedOrigins:
        - "*"
    - pattern: "/embed/*"
      xFrameOptions: "ALLOW-FROM https://example.com"
```

### PathPatternMatcher utility

New file: `platform-web/src/main/kotlin/.../web/PathPatternMatcher.kt`

```kotlin
object PathPatternMatcher {
    fun matches(pattern: String, path: String): Boolean
}
```

Algorithm: split both pattern and path on `/`. Compare segment by segment. `**` in the pattern consumes any number of path segments. `*` matches exactly one segment. A segment with both literal text and `*` (e.g., `*.css`) does a glob match within the segment.

### Filters.securityHeaders changes

Change signature from:
```kotlin
fun securityHeaders(cspPolicy: String = DEFAULT_CSP_POLICY): Filter
```
to:
```kotlin
fun securityHeaders(
    cspPolicy: String = DEFAULT_CSP_POLICY,
    headerConfig: SecurityHeadersConfig = SecurityHeadersConfig(),
): Filter
```

Inside the filter, after `next(requestWithNonce)` returns the response:

1. Resolve any per-route override: `val override = headerConfig.findOverride(request.uri.path)`
2. Apply headers using the override value if non-null, otherwise the base config value
3. For CSP: use `override?.csp ?: cspPolicy`, then apply `.withCspNonce(cspNonce)` so `{nonce}` substitution still works in per-route CSP strings
4. For HSTS: if the resolved value is blank, skip the header entirely (dev mode can disable HSTS)
5. CSP is still only added for non-API paths (existing behavior preserved)

### Filters.cors changes

Change signature from:
```kotlin
fun cors(allowedOrigins: String): Filter
```
to:
```kotlin
fun cors(
    allowedOrigins: String,
    headerConfig: SecurityHeadersConfig = SecurityHeadersConfig(),
): Filter
```

Inside the filter:
1. Resolve per-route CORS override: `val override = headerConfig.findOverride(request.uri.path)`
2. If override has `corsAllowedOrigins`, use that list; otherwise use the global `allowedOrigins` string
3. Rest of CORS logic (OPTIONS preflight, header application) unchanged

### FilterChainFactory changes

Update two lines in `build()`:

```kotlin
.then(Filters.cors(config.corsOrigins, config.securityHeaders))
...
.then(Filters.securityHeaders(config.cspPolicy, config.securityHeaders))
```

---

## What becomes configurable

### Permissions-Policy (all browser features)

The entire `Permissions-Policy` header is a free-form string. Any browser feature can be allowed per-route:

`geolocation`, `camera`, `microphone`, `accelerometer`, `gyroscope`, `magnetometer`, `fullscreen`, `autoplay`, `payment`, `picture-in-picture`, `display-capture`, `clipboard-read`, `clipboard-write`, `web-share`, `usb`, `bluetooth`, `serial`, `nfc`, `encrypted-media`, `sync-xhr`, `gamepad`, `screen-wake-lock`, `interest-cohort`, and all other features the browser supports.

### CSP directives (all)

The per-route `csp` field replaces the entire CSP for matching paths. Any CSP directive can be changed: `img-src`, `script-src`, `style-src`, `connect-src`, `font-src`, `media-src`, `frame-src`, `object-src`, `worker-src`, `manifest-src`, `frame-ancestors`, `base-uri`, `form-action`, etc. The `{nonce}` placeholder is substituted in per-route CSP strings just as in the global policy.

### Other security headers (all)

`X-Frame-Options`, `Referrer-Policy`, `X-Content-Type-Options`, `Strict-Transport-Security` are each individually configurable globally and per-route.

### CORS

`Access-Control-Allow-Origin` is configurable globally (existing `corsOrigins`) and per-route (`corsAllowedOrigins` list).

---

## Backward compatibility

| Existing config | Behavior after change |
|---|---|
| `cspPolicy` / `CSP_POLICY` | Unchanged — still sets the global CSP |
| `corsOrigins` / `CORSORIGINS` | Unchanged — still sets global CORS origins |
| No `securityHeaders` section | All defaults match current hard-coded values — zero behavioral change |
| Extension filters setting security headers | Still overwritten by `securityHeaders` filter (same as today) — but now configurable instead of hard-coded |

---

## File map

| File | Change |
|---|---|
| `platform-core/.../AppConfig.kt` | Add `SecurityHeadersConfig`, `RouteHeaderOverride`, env var parsing |
| `platform-core/.../AppConfigTest.kt` | New env var parsing tests |
| `platform-web/.../PathPatternMatcher.kt` | NEW — Ant-style glob matcher |
| `platform-web/.../PathPatternMatcherTest.kt` | NEW — exhaustive matching tests |
| `platform-web/.../Filters.kt` | Update `securityHeaders` and `cors` signatures + per-route resolution |
| `platform-web/.../assembly/FilterChainFactory.kt` | Pass `config.securityHeaders` to filters |
| `platform-web/.../SecurityHeadersFilterTest.kt` | NEW — integration tests for header application + per-route overrides |
| `docs/configuration/security-headers.md` | NEW — all config options documented |
| `AGENTS.md` | Update Configuration Reference table with new env vars |
| `CHANGELOG.md` | Add entry under [Unreleased] |

---

## Testing strategy

### Unit tests

**PathPatternMatcherTest:**
- `**` matches zero, one, and multiple segments
- `*` matches exactly one segment, not multiple
- `*` within a segment matches characters (`*.css`)
- Exact match (no wildcards) matches only the exact path
- Case sensitivity
- First-match-wins ordering for `findOverride`
- No-match returns null

**AppConfigTest:**
- Each new env var sets the correct field
- Missing env vars fall back to defaults
- `perRouteOverrides` parsed from YAML map section
- `perRouteOverrides` with null fields only override what's set

**SecurityHeadersFilterTest:**
- Default config produces the same headers as before (backward compat)
- Global config overrides each header individually
- Per-route override wins over global config for matching path
- Per-route override does not affect non-matching paths
- Blank `strictTransportSecurity` suppresses the HSTS header
- Per-route CSP with `{nonce}` gets nonce substitution
- Per-route CSP without `{nonce}` is used as-is
- CSP not applied to `/api/` paths (existing behavior)
- First matching per-route pattern wins

### Integration test

Full `WebTest`-based test that:
1. Starts the app with a per-route override for `/map/**`
2. Requests `/map/europe` and verifies the overridden headers
3. Requests `/other` and verifies the default headers
4. Verifies CSP nonce is present and valid in both responses

---

## Concrete use case: Leaflet maps

The community map feature in outerstellar-website needs:

1. **External map tile images** — OSM tiles from `https://*.tile.openstreetmap.org`
2. **Browser geolocation** — for "find my location"

Config:

```yaml
securityHeaders:
  perRouteOverrides:
    - pattern: "/map/**"
      permissionsPolicy: "geolocation=(self), camera=(), microphone=()"
      csp: "default-src 'self'; script-src 'self' {nonce}; style-src 'self' 'unsafe-inline'; font-src 'self'; connect-src 'self' wss:; img-src 'self' data: https://*.tile.openstreetmap.org https://*.basemaps.cartocdn.com; base-uri 'self'; form-action 'self'"
```

This unblocks Leaflet while keeping all other routes at full security. The `{nonce}` placeholder ensures inline scripts on `/map/**` pages still work with nonce-based CSP.

---

## Out of scope

- Spring PathPattern or regex-based path matching (Ant-style glob is sufficient)
- Variable capture in path patterns (`{id}`)
- Per-method overrides (same headers for GET/POST/etc on the same path)
- Dynamic/computed headers (config is static YAML/env; dynamic headers remain an extension-filter concern)
- Deprecating `cspPolicy` or `corsOrigins` top-level fields (kept for backward compat)
