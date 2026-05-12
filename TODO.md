# TODO

Architecture, security, and maintainability improvements identified during code review.

---

## High Priority

### Security

- [x] ~~**CORS defaults to `*` — restrict to specific origins**~~
  Fixed in PR #230 — default changed from `"*"` to `""` (no CORS headers unless explicitly configured).

- [x] ~~**Error messages leak internal details in API responses**~~
  Fixed in PR #230 — non-`OuterstellarException` errors return generic message in API responses.

- [x] ~~**Password reset token exposed in URL query parameter**~~
  Fixed — changed from query param (`?token=X`) to path param (`/auth/reset/{token}`) to prevent Referer/log leakage.
  — `platform-security/.../security/PasswordResetService.kt:38`, `platform-web/.../web/AuthRoutes.kt:193`

- [x] ~~**Session cookie `Secure` flag defaults to `false`**~~
  Fixed — both the data class default and YAML/env fallback now default to `true`.
  — `platform-core/.../AppConfig.kt:51`

### Hardcoded Values

- [x] ~~**Seed data has hardcoded weak password `"password123"` in `src/main`**~~
  Fixed — reads from `SEED_USER_PASSWORD` env var with fallback to `"password123"` (warns when using default).
  — `platform-seed/.../seed/SeedData.kt:49`

- [x] ~~**Default JDBC password `"outerstellar"` has no production guard**~~
  Fixed — added `DEFAULT_JDBC_PASSWORD` constant, exposed `profile` on `AppConfig`, startup guard in `Main.kt` logs FATAL when default password is used with non-default profile.
  — `platform-core/.../AppConfig.kt:48`, `platform-web/.../Main.kt:46`

### Architecture

- [ ] **Refactor `SwingSyncApp.kt` God class (856 lines)**
  Contains main window, all navigation, menu items, translations, and ~50 fields. Extract `SyncWindow` into its own class; split navigation and menu logic.
  — `platform-desktop/.../swing/SwingSyncApp.kt`

- [x] ~~**Replace `sendAsync` in `SegmentAnalyticsService` with sync + background thread**~~
  Fixed in PR #230 — replaced with synchronous `client.send()` in a daemon thread.

- [ ] **Reduce generic `catch (Exception)` boilerplate in `DesktopSyncEngine`**
  22 methods duplicate the same try/catch pattern. Extract a higher-order function like `runCatching(operation, onSessionExpired, onError)`. Partially done — 8 methods refactored via `runGuarded`/`runGuardedResult` in PR #231.
  — `platform-sync-client/.../engine/DesktopSyncEngine.kt`

---

## Medium Priority

### Security

- [x] ~~**Invalidate all user sessions on password change**~~
  Fixed in PR #229 — `sessionRepository?.deleteByUserId()` called in `changePassword()`.

- [x] ~~**Add dependency vulnerability scanning to CI**~~
  Dependabot already configured with Maven, GitHub Actions, and npm ecosystems — grouped by domain (http4k, opentelemetry, persistence, test, build plugins, etc.).
  — `.github/dependabot.yml`

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

- [x] ~~**Login rate limiting is per-IP, not per-account**~~
  Fixed in PR #234 — added per-account token bucket (20 req / 15 min) alongside per-IP bucket. Extracts account identifier from JSON body (`username`/`email`) or form-encoded body.
  — `platform-web/.../web/RateLimiter.kt`

- [x] ~~**No account lockout after failed logins**~~
  Fixed in PR #227 — configurable failed attempts (default 10) with auto-unlock.

- [x] ~~**Missing HSTS header**~~
  Fixed in PR #225 — `Strict-Transport-Security: max-age=31536000; includeSubDomains`.

- [x] ~~**SSRF protection gaps: IPv6, DNS rebinding, 0.0.0.0**~~
  Fixed in PR #225 — private host patterns now cover IPv6 loopback, link-local, unique-local, `.local`, `.internal`.

### Architecture

- [x] ~~**Refactor `App.kt` (534 lines, 15-parameter function)**~~
  Fixed in PR #239 — extracted `AppContext` (7 core params) + `OptionalServices` (9 nullable service deps) private classes; `PluginContext` duplication eliminated; `app()` public signature unchanged. SpotBugs `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` excluded for `AppKt` (Kotlin/http4c DSL false positive).
  — `platform-web/.../App.kt`, `config/spotbugs-exclude.xml`

- [x] ~~**Declare `platform-core` as explicit dependency in desktop modules**~~
  Fixed in PR #230 — both `platform-desktop` and `platform-desktop-javafx` pom.xml now declare it explicitly.

### Hardcoded Values

- [x] ~~**Replace `"ADMIN"` / `"USER"` string comparisons with `UserRole` enum**~~
  Fixed in PR #231 — `UserRole` centralized in `platform-core`, all role checks use enum.
  — `UserAdminRoutes.kt`, `SyncViewModel.kt`, `SwingSyncApp.kt`, JavaFX controllers

- [x] ~~**Centralize `appBaseUrl = "http://localhost:8080"`**~~
  Fixed in PR #231 — `AppConfig.DEFAULT_APP_BASE_URL` constant used across all modules.
  — `AppConfig.kt:64`, `SecurityConfig`, `PasswordResetService.kt`, `DesktopAppConfig.kt`

---

## Low Priority

### Security

- [x] ~~**JWT secret defaults to empty string with no startup validation**~~
  Fixed in PR #230 — added startup warning when JWT is enabled but secret is blank.

- [ ] **Health endpoint is unauthenticated, leaks DB status**
  `/health` returns whether the database is UP or DOWN with no authentication.
  — `platform-web/.../App.kt:464-478`

- [x] ~~**No input size validation on contact/message content fields**~~
  Fixed in PR #240 — max length constants matching DB column sizes added to `MessageService`, `ContactService`, `SyncMessage`, `SyncContact`. Validation applied in all create/update paths with user-friendly error messages.
  — `platform-core/.../service/MessageService.kt`, `platform-core/.../service/ContactService.kt`, `platform-core/.../sync/SyncModels.kt`

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

- [x] ~~**Outbox status strings (`"PENDING"`, `"PROCESSED"`, `"FAILED"`) scattered**~~
  Fixed in PR #243 — extracted `OutboxStatus` enum in `OutboxRepository.kt`. All 19 usages across `MessageService`, `JooqOutboxRepository`, `JdbiOutboxRepository`, `DevDashboardRoutes`, and tests updated.
  — `platform-core/.../persistence/OutboxRepository.kt`

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

- [x] ~~**Add `hreflang` alternate links for i18n SEO**~~
  Fixed in PR #236 — `<link rel="alternate" hreflang="en/fr/x-default">` emitted on non-noindex pages with canonical URL. `supportedLocales` and `appBaseUrl` added to `ShellView`.
  — `LayoutHead.kte`, `ViewModels.kt`, `WebContext.kt`

- [x] ~~**Add `<link rel="preload">` for CSS and icon font**~~
  Fixed in PR #236 — `<link rel="preload">` for `site.css` (as=style) and `remixicon.woff2` (as=font, crossorigin).
  — `LayoutHead.kte`

- [x] ~~**Fix heading hierarchy — pages missing `<h1>`**~~
  Fixed in PR #236 — changed `<h2>` to `<h1>` in ProfilePage, NotificationsPage, PluginAdminDashboard.
  — `ProfilePage.kte`, `NotificationsPage.kte`, `PluginAdminDashboard.kte`

- [x] ~~**Add `/robots.txt` route**~~
  Fixed in PR #236 — dynamic route at `/robots.txt` disallows `/api/`, `/admin/`, `/ws/`, `/auth/`, `/errors/`, `/components/`, `/messages/`, `/notifications/`, `/settings/`.
  — `App.kt:buildRobotsTxtResponse()`

- [ ] **Integrate `fragments-sitemap-core` for XML sitemap generation**
  Generate `/sitemap.xml` listing public pages (`/`, `/auth`, `/search`). Low priority since most pages require auth, but important for public content discovery.
  — Dependency: `io.github.rygel:fragments-sitemap-core`

### Low Priority

- [x] ~~**Add `aria-hidden="true"` to decorative Remixicon `<i>` elements**~~
  Fixed in PR #238 — `aria-hidden="true"` added to all decorative icon elements across templates.
  — All `.kte` templates using `<i class="ri-...">`

- [x] ~~**Add `aria-live="polite"` to HTMX dynamic regions**~~
  Fixed in PR #238 — `aria-live="polite"` added to `#auth-form-slot`, `#error-help-slot`, `#ws-updates`.
  — `AuthPage.kte`, `ErrorPage.kte`, layout templates

- [x] ~~**Add skip-to-content link for keyboard navigation**~~
  Fixed in PR #238 — hidden skip link added at top of `TopbarLayout.kte` and `SidebarLayout.kte`, targeting `#main-content`.
  — `TopbarLayout.kte`, `SidebarLayout.kte`

- [x] ~~**Add `<meta name="theme-color">` for mobile browsers**~~
  Fixed in PR #236 — `<meta name="theme-color" content="#0f172a">` added to LayoutHead.
  — `LayoutHead.kte`

- [ ] **Add JSON-LD structured data (home page only)**
  `WebSite` schema with `SearchAction` pointing to `/search?q=`. Low priority for an authenticated app.
  — Can use `fragments-seo-core` `SeoMetadata` JSON-LD generation

- [x] ~~**Add `loading="lazy"` to avatar image**~~
  Fixed in PR #236 — `loading="lazy"` added to Gravatar `<img>` in ProfilePage.
  — `ProfilePage.kte`

- [x] ~~**Add `font-display: swap` to Remix Icon font**~~
  Already present in `remixicon.css` — no change needed.
  — `platform-web/src/main/resources/static/vendor/remixicon/remixicon.css:19`

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

- [x] **UserRole enum centralized in platform-core** (PR #231)
- [x] **appBaseUrl default consolidated as AppConfig.DEFAULT_APP_BASE_URL** (PR #231)
- [x] **DesktopSyncEngine catch boilerplate extracted via runGuarded/runGuardedResult** (PR #231)
- [x] **Password reset token: path param instead of query param** (PR #232)
- [x] **Seed data password: env var override with dev fallback** (PR #232)
- [x] **JDBC password production guard** (PR #232)
- [x] **Per-account rate limiting for brute-force protection** (PR #234)
- [x] **Dependency vulnerability scanning (Dependabot + security scanning suite)**
- [x] **SEO: hreflang, preload, robots.txt, heading hierarchy, theme-color, lazy avatar** (PR #236)
- [x] **Accessibility: aria-hidden, aria-live, skip-to-content** (PR #238)
- [x] **App.kt refactor: AppContext + OptionalServices extraction** (PR #239)
- [x] **Input size validation for message and contact fields** (PR #240)
