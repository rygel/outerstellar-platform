# TODO

Architecture, security, and maintainability improvements identified during code review.

---

## High Priority

### Security

- [x] ~~**CORS defaults to `*` — restrict to specific origins**~~
  Fixed in PR #230 — default changed from `"*"` to `""` (no CORS headers unless explicitly configured).

- [x] ~~**Error messages leak internal details in API responses**~~
  Fixed in PR #230 — non-`OuterstellarException` errors return generic message in API responses.

- [ ] **Password reset token exposed in URL query parameter**
  Token is sent as `?token=$tokenValue` in the reset link, leaking via Referer header, browser history, and server logs. Should use POST-based submission.
  — `platform-security/.../security/PasswordResetService.kt:38`

- [x] ~~**Session cookie `Secure` flag defaults to `false`**~~
  Fixed — both the data class default and YAML/env fallback now default to `true`.
  — `platform-core/.../AppConfig.kt:51`

### Hardcoded Values

- [ ] **Seed data has hardcoded weak password `"password123"` in `src/main`**
  Four development users (admin, alice, bob, carol) all use the same weak hardcoded password. Move to env-var override or test-only.
  — `platform-seed/.../seed/SeedData.kt:67`

- [ ] **Default JDBC password `"outerstellar"` has no production guard**
  The default password is hardcoded in both YAML files (6 places) and Kotlin defaults. No startup assertion prevents deploying with defaults.
  — `platform-core/.../AppConfig.kt:44-46`, all `application.yaml` files

### Architecture

- [ ] **Refactor `SwingSyncApp.kt` God class (856 lines)**
  Contains main window, all navigation, menu items, translations, and ~50 fields. Extract `SyncWindow` into its own class; split navigation and menu logic.
  — `platform-desktop/.../swing/SwingSyncApp.kt`

- [x] ~~**Replace `sendAsync` in `SegmentAnalyticsService` with sync + background thread**~~
  Fixed in PR #230 — replaced with synchronous `client.send()` in a daemon thread.

- [ ] **Reduce generic `catch (Exception)` boilerplate in `DesktopSyncEngine`**
  22 methods duplicate the same try/catch pattern. Extract a higher-order function like `runCatching(operation, onSessionExpired, onError)`.
  — `platform-sync-client/.../engine/DesktopSyncEngine.kt` (22 instances)

---

## Medium Priority

### Security

- [x] ~~**Invalidate all user sessions on password change**~~
  Fixed in PR #229 — `sessionRepository?.deleteByUserId()` called in `changePassword()`.

- [ ] **Add dependency vulnerability scanning to CI**
  No OWASP Dependency-Check, Snyk, or Dependabot is configured. Dependency vulnerabilities go undetected.
  — CI pipeline, `.github/dependabot.yml` (missing)

- [x] ~~**Persist authentication failures to audit table**~~
  Fixed in PR #229 — `AUTHENTICATION_FAILED` entries for all failure paths.

- [x] ~~**Add audit logging for API key operations**~~
  Fixed in PR #229 — `API_KEY_CREATED` and `API_KEY_DELETED` audit entries.

- [x] ~~**Add SameSite/Secure flags to preference cookies**~~
  Fixed in PR #229 — `SameSite.Strict` + configurable `Secure` flag via `sessionCookieSecure`.

- [x] ~~**CSP `connect-src` allows `ws:` (unencrypted WebSocket)**~~
  Fixed in PR #229 — removed `ws:`, only `wss:` remains.

- [x] ~~**CSP missing `base-uri` and `form-action` directives**~~
  Fixed in PR #229 — added `base-uri 'self'; form-action 'self'`.

- [ ] **Login rate limiting is per-IP, not per-account**
  Attacker can brute-force a single account through IP rotation.
  — `platform-web/.../web/RateLimiter.kt:79`

- [x] ~~**No account lockout after failed logins**~~
  Fixed in PR #227 — configurable failed attempts (default 10) with auto-unlock.

- [x] ~~**Missing HSTS header**~~
  Fixed in PR #225 — `Strict-Transport-Security: max-age=31536000; includeSubDomains`.

- [x] ~~**SSRF protection gaps: IPv6, DNS rebinding, 0.0.0.0**~~
  Fixed in PR #225 — private host patterns now cover IPv6 loopback, link-local, unique-local, `.local`, `.internal`.

### Architecture

- [ ] **Refactor `App.kt` (534 lines, 15-parameter function)**
  The `app()` function assembles the entire HTTP handler chain directly. `PluginContext` is constructed 4 times redundantly. Extract into smaller assembly functions.
  — `platform-web/.../App.kt`

- [x] ~~**Declare `platform-core` as explicit dependency in desktop modules**~~
  Fixed in PR #230 — both `platform-desktop` and `platform-desktop-javafx` pom.xml now declare it explicitly.

### Hardcoded Values

- [ ] **Replace `"ADMIN"` / `"USER"` string comparisons with `UserRole` enum**
  Role checks use raw string comparisons in 5 production files. Use the `UserRole` enum for type safety.
  — `UserAdminRoutes.kt:106`, `SyncViewModel.kt:328`, `SwingSyncApp.kt:641`, JavaFX controllers

- [ ] **Centralize `appBaseUrl = "http://localhost:8080"`**
  Hardcoded in 4 separate production files. Should be injected from a single config source.
  — `SecurityService.kt:24`, `PasswordResetService.kt:18`, `SecurityModule.kt:21`, `DesktopAppConfig.kt:7`

---

## Low Priority

### Security

- [x] ~~**JWT secret defaults to empty string with no startup validation**~~
  Fixed in PR #230 — added startup warning when JWT is enabled but secret is blank.

- [ ] **Health endpoint is unauthenticated, leaks DB status**
  `/health` returns whether the database is UP or DOWN with no authentication.
  — `platform-web/.../App.kt:464-478`

- [ ] **No input size validation on contact/message content fields**
  Message content, author, and contact fields have no server-side maximum length limits.
  — `platform-web/.../web/HomeRoutes.kt:66-67`, `ContactsRoutes.kt:60-67`

### Architecture

- [ ] **Extract SSRF validation from `SecurityService.updateProfile()` into a separate `UrlValidator`**
  The avatar URL SSRF checks are inlined in `SecurityService`. This logic is duplicated in principle and should be a reusable validator.
  — `platform-security/.../security/SecurityService.kt:196-220`

- [ ] **Split `WebPageFactory.kt` (582 lines) into domain-specific factories**
  Annotated `@Suppress("TooManyFunctions")` with ~20 builder methods. Extract contact, admin, settings pages.
  — `platform-web/.../web/WebPageFactory.kt`

- [ ] **Add `DesktopSyncEngine` interface for testability**
  Currently a concrete class; `SyncViewModel` and JavaFX controllers depend on it directly.
  — `platform-sync-client/.../engine/DesktopSyncEngine.kt`

- [ ] **Dead scaffolding: `AppleOAuthProvider` and `PushNotificationService`**
  Both contain TODO placeholders and are non-functional. Either wire to config or remove.
  — `platform-security/.../security/AppleOAuthProvider.kt`, `platform-core/.../service/PushNotificationService.kt`

### Hardcoded Values

- [ ] **Hardcoded JTE development paths in `JteInfra.kt`**
  Assumes working directory is project root with `web/` subdirectory — breaks in production deployments.
  — `platform-web/.../infra/JteInfra.kt:37-39`

- [ ] **Supported languages/layouts/shells are inline sets**
  `setOf("en", "fr")`, `setOf("nice", "cozy", "compact")`, `setOf("sidebar", "topbar")` in `Filters.kt` should be configurable.
  — `platform-web/.../web/Filters.kt:281-296`

- [ ] **Outbox status strings (`"PENDING"`, `"PROCESSED"`, `"FAILED"`) scattered**
  Used as raw strings in `JooqOutboxRepository`, `MessageService`, and `DevDashboardRoutes`. Should be an enum or constants.
  — Multiple files

---

## SEO & fragments4k Integration

Integrate [fragments4k](https://github.com/rygel/fragments4k) (v0.6.5+) for SEO metadata, sitemap, and content management. The library provides `SeoMetadata`, Open Graph, Twitter Card, JSON-LD, canonical URLs, and `/robots.txt` out of the box via the `fragments-http4k` adapter.

### High Priority

- [x] ~~**Add `<meta name="description">` per page**~~
  Fixed — `pageDescription` field on `ShellView`, i18n key `web.page.description.{activeSection}` per page, rendered in `LayoutHead.kte` with safe fallback for missing keys.
  — `platform-web/.../web/ViewModels.kt:46`, `platform-web/.../web/WebContext.kt:286`, `LayoutHead.kte:8`

- [x] ~~**Add `<link rel="canonical">` per page**~~
  Fixed — `canonicalUrl` field on `ShellView`, constructed from `appBaseUrl + currentPath`, rendered in `LayoutHead.kte`.
  — `platform-web/.../web/WebContext.kt:288`, `LayoutHead.kte:11`

- [x] ~~**Add `defer` to all `<script>` tags**~~
  Fixed — htmx scripts in `LayoutHead.kte` now use `defer` to avoid render-blocking. `platform.js` was already at end of `<body>`.
  — `LayoutHead.kte:19-20`

- [x] ~~**Add `<meta name="robots" content="noindex">` to admin/auth/error pages**~~
  Fixed — `noIndex` field on `ShellView`, set via `NO_INDEX_SECTIONS` set in `WebContext.companion`, emitted in `LayoutHead.kte`.
  — `platform-web/.../web/WebContext.kt:41-52`, `LayoutHead.kte:14`

### Medium Priority

- [ ] **Integrate `fragments-seo-core` for Open Graph + Twitter Card meta tags**
  Use `SeoMetadata.fromFragment()` to generate `og:title`, `og:description`, `og:image`, `og:url`, `og:type`, `twitter:card`, etc. Extend `ShellView` with SEO fields and emit in `LayoutHead.kte`.
  — Dependency: `io.github.rygel:fragments-seo-core`

- [ ] **Add `hreflang` alternate links for i18n SEO**
  App supports `en`/`fr` but no `<link rel="alternate" hreflang="...">` tags. Search engines cannot discover alternate language versions.
  — `platform-web/src/main/jte/.../layouts/LayoutHead.kte`

- [ ] **Add `<link rel="preload">` for CSS and icon font**
  `site.css` and `remixicon.woff2` are render-critical resources that should be preloaded.
  — `platform-web/src/main/jte/.../layouts/LayoutHead.kte`

- [ ] **Fix heading hierarchy — pages missing `<h1>`**
  `ProfilePage.kte`, `NotificationsPage.kte`, `PluginAdminDashboard.kte` start at `<h2>` without an `<h1>`.
  — `platform-web/src/main/jte/.../ProfilePage.kte`, `NotificationsPage.kte`, `PluginAdminDashboard.kte`

- [ ] **Add `/robots.txt` route**
  No robots.txt exists. Should disallow `/api/`, `/admin/`, `/ws/`, `/auth/`, `/errors/`, `/components/` and allow `/`, `/contacts`, `/search`. Can be provided by `fragments-http4k` adapter or a static file.
  — `platform-web/src/main/resources/static/` (missing)

- [ ] **Integrate `fragments-sitemap-core` for XML sitemap generation**
  Generate `/sitemap.xml` listing public pages (`/`, `/auth`, `/search`). Low priority since most pages require auth, but important for public content discovery.
  — Dependency: `io.github.rygel:fragments-sitemap-core`

### Low Priority

- [ ] **Add `aria-hidden="true"` to decorative Remixicon `<i>` elements**
  Screen readers may announce empty content for decorative icons across all templates.
  — All `.kte` templates using `<i class="ri-...">`

- [ ] **Add `aria-live="polite"` to HTMX dynamic regions**
  `#auth-form-slot`, `#error-help-slot`, `#ws-updates` lack live region attributes for screen reader announcements.
  — `AuthPage.kte`, `ErrorPage.kte`, layout templates

- [ ] **Add skip-to-content link for keyboard navigation**
  No skip navigation link exists. Add hidden link at top of layout templates.
  — `TopbarLayout.kte`, `SidebarLayout.kte`

- [ ] **Add `<meta name="theme-color">` for mobile browsers**
  Should match the accent color of the current theme.
  — `platform-web/src/main/jte/.../layouts/LayoutHead.kte`

- [ ] **Add JSON-LD structured data (home page only)**
  `WebSite` schema with `SearchAction` pointing to `/search?q=`. Low priority for an authenticated app.
  — Can use `fragments-seo-core` `SeoMetadata` JSON-LD generation

- [ ] **Add `loading="lazy"` to avatar image**
  External Gravatar loads may affect LCP.
  — `platform-web/src/main/jte/.../ProfilePage.kte:13`

- [ ] **Add `font-display: swap` to Remix Icon font**
  Prevents FOIT (Flash of Invisible Text).
  — `platform-web/src/main/resources/static/` (remixicon.css)

---

## Completed

- [x] **Invalidate all user sessions on password change** (PR #229)
- [x] **Persist authentication failures to audit table** (PR #229)
- [x] **Add audit logging for API key operations** (PR #229)
- [x] **Add SameSite/Secure flags to preference cookies** (PR #229)
- [x] **Harden CSP: base-uri, form-action, remove ws:** (PR #229)
- [x] **Session cookie `Secure` flag defaults to `true`**
- [x] **Account lockout after failed logins** (PR #227)
- [x] **HSTS header** (PR #225)
- [x] **SSRF protection for IPv6, DNS rebinding** (PR #225)

- [x] **Targeted cache invalidation in `MessageService`**
  — `platform-core/.../service/MessageService.kt` lines 129–130, 150–152, 197, 207–208, 240–242, 276–278

- [x] **Bound the `gravatarCache` with Caffeine**
  — `platform-web/.../web/WebPageFactory.kt` line 9

- [x] **Eliminate extra SELECT in `JooqContactRepository.updateContact()`**
  — `platform-persistence-jooq/.../persistence/JooqContactRepository.kt` lines 247–252

- [x] **Continuous outbox drain when batch is full**
  — `platform-core/.../service/OutboxProcessor.kt` line 8 · `Main.kt` line 61

- [x] **Document the jOOQ vs. JDBI persistence selection policy**
  Both modules implement the same repository interfaces with no runtime flag or documented rule for which is active.

- [x] **Guard `WebContext.user` against cache removal**
  — `platform-web/.../web/WebContext.kt` lines 69–86

- [x] **Update dependency bumps** (http4k, opentelemetry, angus-mail, assertj-core, ktfmt)

- [x] **Fix desktop `SyncViewModel.onSessionExpired()` race condition** (PR #230)

- [x] **CORS wildcard default dropped** (PR #230)

- [x] **API error messages sanitized** (PR #230)

- [x] **SegmentAnalyticsService async violation fixed** (PR #230)

- [x] **JWT secret startup warning added** (PR #230)

- [x] **platform-core declared as explicit dep in desktop modules** (PR #230)
