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

- [x] ~~**Refactor `SwingSyncApp.kt` God class (856 lines)**~~
  Fixed in PR #254 — extracted `SyncWindowMenu`, `SyncWindowNav`, removed dead dialog delegation. Now ~530 lines.
  — `platform-desktop/.../swing/SwingSyncApp.kt`

- [x] ~~**Replace `sendAsync` in `SegmentAnalyticsService` with sync + background thread**~~
  Fixed in PR #230.

- [x] ~~**Reduce generic `catch (Exception)` boilerplate in `DesktopSyncEngine`**~~
  Fixed in PR #263 — 7 of 13 methods refactored via enhanced `runGuarded`/`runGuardedResult` with optional `onError` callbacks.
  — `platform-sync-client/.../engine/DesktopSyncEngine.kt`

---

## Medium Priority

### Security

- [x] ~~**Invalidate all user sessions on password change**~~
  Fixed in PR #229 — `sessionRepository?.deleteByUserId()` called in `changePassword()`.

- [x] ~~**Harden password reset tokens**~~
  Replaced with UUID v7 (SecureRandom, time-ordered, 122-bit randomness). Fixed in PR #277.
  — `platform-security/.../security/PasswordResetService.kt`

- [x] ~~**Use configured `appBaseUrl` for OAuth redirect URIs**~~
  Fixed in PR #277 — OAuth redirect URIs now use `AppConfig.appBaseUrl` instead of request headers.
  — `platform-web/.../web/OAuthRoutes.kt`

- [x] ~~**Trust forwarded IP headers only from configured proxies**~~
  Fixed in PR #277 — `X-Forwarded-For` only read when source IP matches `AppConfig.trustedProxies`.
  — `platform-web/.../web/RateLimiter.kt`

- [x] ~~**Scope device-token deregistration to authenticated user**~~
  Fixed in PR #277 — DELETE now requires token ownership (token + user_id match).
  — `platform-web/.../web/DeviceRegistrationApi.kt`, `platform-persistence-jooq/.../JooqDeviceTokenRepository.kt`, `platform-persistence-jdbi/.../JdbiDeviceTokenRepository.kt`

- [x] ~~**Neutralize formula injection in CSV exports**~~
  Fixed in PR #277 — `CsvUtils.escapeCsv()` prefixes `=`, `+`, `-`, `@`, tab, CR with `'`.
  — `platform-core/.../export/ExportService.kt`, `platform-web/.../export/*ExportProvider.kt`, `platform-web/.../web/UserAdminRoutes.kt`

- [x] ~~**Remove `unsafe-inline` from script CSP**~~
  Fixed in PR #277 — `script-src` no longer includes `'unsafe-inline'`.
  — `platform-core/.../AppConfig.kt`, `platform-web/.../web/Filters.kt`, `platform-web/src/main/jte/...`

- [x] ~~**Apply the web filter chain only once**~~
  Fixed in PR #277 — removed inner `buildFilterChain()` call; filters applied once to all routes.
  — `platform-web/.../App.kt`

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

- [x] ~~**Health endpoint is unauthenticated, leaks DB status**~~
  Fixed in PR #242 — `/health` now restricted to localhost only via a `localhostOnly` filter that checks `request.source`. Non-loopback connections receive `403 Forbidden`. No auth needed since it's internal-only.
  — `platform-web/.../App.kt`

- [x] ~~**No input size validation on contact/message content fields**~~
  Fixed in PR #240 — max length constants matching DB column sizes added to `MessageService`, `ContactService`, `SyncMessage`, `SyncContact`. Validation applied in all create/update paths with user-friendly error messages.
  — `platform-core/.../service/MessageService.kt`, `platform-core/.../service/ContactService.kt`, `platform-core/.../sync/SyncModels.kt`

### Architecture

- [x] ~~**Extract SSRF validation from `SecurityService.updateProfile()` into a separate `UrlValidator`**~~
  Fixed in PR #250.
  — `platform-core/.../service/UrlValidator.kt`

- [x] ~~**Split `WebPageFactory.kt` (582 lines) into domain-specific factories**~~
  Fixed in PR #252 — 10 domain factories, 136-line delegating class.
  — `platform-web/.../web/WebPageFactory.kt`

- [ ] **Add `DesktopSyncEngine` interface for testability**
  Currently a concrete class; `SyncViewModel` and JavaFX controllers depend on it directly.
  — `platform-sync-client/.../engine/DesktopSyncEngine.kt`

- [x] ~~**Dead scaffolding: `AppleOAuthProvider` and `PushNotificationService`**~~
  Fixed in PR #244 — wired to config. `AppleOAuthProvider` now reads from `AppConfig.appleOAuth` (teamId, clientId, keyId, privateKeyPem) and is only active when enabled. `PushNotificationService` registered in Koin with config-driven implementation selection (console/fcm/apns). Both are fully configurable via YAML/env vars.
  — `AppConfig.kt`, `AppleOAuthProvider.kt`, `CoreModule.kt`, `App.kt`

### Hardcoded Values

- [x] ~~**Hardcoded JTE development paths in `JteInfra.kt`**~~
  Fixed in PR #251 — `JTE_SOURCE_DIR` env var.
  — `platform-web/.../infra/JteInfra.kt`

- [x] ~~**Supported languages/layouts/shells are inline sets**~~
  Fixed in PR #251 — centralized into `WebContext` constants.
  — `platform-web/.../web/WebContext.kt`

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

- [x] ~~**Integrate `fragments-seo-core` for Open Graph + Twitter Card meta tags**~~
  Fixed in PR #259 — uses `fragments-seo-core:0.6.5` `SeoMetadata.forPage()` + `generateAllMetaTags()`.

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

- [x] ~~**Integrate `fragments-sitemap-core` for XML sitemap generation**~~
  Fixed in PR #255 — inline `/sitemap.xml` route in `App.kt`.

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

- [x] ~~**Add JSON-LD structured data (home page only)**~~
  Fixed in PR #253 — `fragments-seo-core` generates `WebSite` JSON-LD on all non-noindex pages.

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

---

## Future

### Usability and Missing Features

- [ ] **Implement web message edit and delete actions**
  The message list renders edit/delete controls, but `HomeRoutes` does not expose matching edit, update, or delete handlers. Wire the existing `MessageService.updateMessage()` and `deleteMessage()` paths into routes and HTMX fragments.
  — `platform-web/src/main/jte/.../components/MessageList.kte`, `platform-web/.../web/HomeRoutes.kt`, `platform-core/.../service/MessageService.kt`

- [ ] **Return an HTMX-safe response from message creation**
  The home composer posts with `hx-target="#message-list-container"`, but the route returns a redirect to `/`. Return the refreshed message-list fragment or retarget the form so HTMX does not swap a full page into the list container.
  — `platform-web/src/main/jte/.../HomePage.kte`, `platform-web/.../web/HomeRoutes.kt`

- [ ] **Finish the unified `/settings` tabs**
  The `/settings` page currently shows descriptive placeholder text for Profile, Password, API Keys, Notifications, and Appearance; only Security loads the TOTP fragment. Embed or route to the existing working forms and retire duplicate account links.
  — `platform-web/.../web/SettingsPageFactory.kt`, `platform-web/src/main/jte/.../SettingsPage.kte`

- [ ] **Add contact trash and restore flow**
  Contacts are soft-deleted and the repository supports `restore()`, but the service/routes/UI only expose delete. Add a deleted-contacts view, restore action, and clear user messaging around soft deletion.
  — `platform-core/.../persistence/ContactRepository.kt`, `platform-core/.../service/ContactService.kt`, `platform-web/.../web/ContactsRoutes.kt`

- [ ] **Preserve contact list state during create/update/delete**
  Contact create/update re-render the default first page, pagination URLs drop `q`, and the modal closes after any response. Preserve query/limit/offset, keep the modal open on validation errors, and return targeted list/form fragments.
  — `platform-web/.../web/ContactsRoutes.kt`, `platform-web/.../web/ContactsPageFactory.kt`, `platform-web/src/main/jte/.../components/ContactForm.kte`

- [ ] **Bring desktop message/contact actions to parity with web/domain services**
  Desktop exposes message creation and contact create/edit, but not message edit/delete or contact delete/restore. Extend `SyncEngine`, `DesktopSyncEngine`, `SyncViewModel`, and Swing views for the missing lifecycle actions.
  — `platform-sync-client/.../engine/SyncEngine.kt`, `platform-sync-client/.../engine/DesktopSyncEngine.kt`, `platform-desktop/.../swing/SyncViews.kt`

- [ ] **Clean up primary navigation discoverability**
  Default nav still exposes `Auth` and a demo-like `Errors` page, while Search and unified Settings are not primary nav items. Adjust nav based on login state and make production features easier to find.
  — `platform-web/.../web/WebContext.kt`

- [ ] **Expose JSON export routes and UI entry points**
  `ExportProvider.exportJson()` is implemented by providers, but `ExportRoutes` only exposes CSV and there is no obvious web entry point for user/admin exports. Add JSON endpoints and discoverable export actions where appropriate.
  — `platform-core/.../export/ExportService.kt`, `platform-web/.../web/ExportRoutes.kt`, `platform-web/.../export/*ExportProvider.kt`

- [ ] **Make search discoverable and more useful**
  Search routes and providers exist, but the shell has no search nav/global search box. Add search discoverability, result highlighting, type filters/grouping, and entity-specific quick actions.
  — `platform-web/.../App.kt`, `platform-web/.../web/WebContext.kt`, `platform-web/src/main/jte/.../SearchPage.kte`

- [ ] **Complete TOTP setup UX**
  TOTP setup uses hardcoded English labels/errors, raw text responses, and lacks backup-code regeneration/copy/download affordances. Localize the flow, return styled fragments/status codes, and improve backup-code management.
  — `platform-web/.../web/TOTPRoutes.kt`, `platform-web/src/main/jte/.../TotpSetupFragment.kte`, `platform-web/src/main/jte/.../TotpChallengeForm.kte`

- [ ] **Hide or finish Sign in with Apple**
  `AppleOAuthProvider.exchangeCode()` still throws `not yet implemented`. Do not present Apple sign-in as production-ready until the token exchange and ID-token validation flow are implemented.
  — `platform-security/.../security/AppleOAuthProvider.kt`, `platform-web/.../web/OAuthRoutes.kt`

- [ ] **Expand admin user-management workflows**
  Admin UI supports toggle enabled, toggle role, unlock, and export, but lacks create user, edit user, force password reset, explicit role selection, and confirmations for role/enable changes.
  — `platform-web/.../web/UserAdminRoutes.kt`, `platform-web/src/main/jte/.../UserAdminPage.kte`, `platform-web/.../web/UserAdminApi.kt`

- [ ] **Localize remaining desktop user-facing strings**
  Swing still has hardcoded text in conflict resolution, contact dialogs, list management, theme preview, spell-check dialogs, and generic action failures. Move these to i18n keys and keep runtime language switching intact.
  — `platform-desktop/.../swing/SyncDialogs.kt`, `platform-desktop/.../swing/SyncViews.kt`, `platform-desktop/.../swing/components/SpellCheckingTextArea.kt`, `platform-desktop/.../swing/components/SpellCheckingTextField.kt`

- [ ] **Improve accessibility labels for icon-only controls**
  Several icon-only links/buttons rely on `title` or have no accessible label, especially notification/profile/password actions in the topbar and message/contact action icons. Add `aria-label` or visible text where appropriate.
  — `platform-web/src/main/jte/.../layouts/TopbarLayout.kte`, `platform-web/src/main/jte/.../components/NotificationBell.kte`, `platform-web/src/main/jte/.../components/MessageList.kte`, `platform-web/src/main/jte/.../ContactsPage.kte`

### Performance

- [x] ~~**Apply the web filter chain only once**~~
  Fixed in PR #277 — removed inner `buildFilterChain()` call; filters applied once to all routes.
  — `platform-web/.../App.kt`

- [ ] **Restrict dynamic ETag hashing**
  `etagCachingFilter` buffers and hashes full non-JSON responses with `readBytes()`. Limit this to small/static text responses, precompute static asset ETags, or skip dynamic HTML.
  — `platform-web/.../web/Filters.kt`

- [ ] **Resolve session state once per request**
  `WebContext.user` and `WebContext.sessionExpired` can each call `SecurityService.lookupSession(rawToken)`. Cache a single `SessionLookup` lazy value and derive both properties from it.
  — `platform-web/.../web/WebContext.kt`

- [ ] **Batch sync push upserts**
  Message/contact sync push performs per-item read/update/read cycles. Add bulk prefetch by `sync_id` and batched `INSERT ... ON CONFLICT DO UPDATE ... RETURNING` or repository bulk upsert methods.
  — `platform-core/.../service/MessageService.kt`, `platform-core/.../service/ContactService.kt`, `platform-persistence-jooq/.../*Repository.kt`, `platform-persistence-jdbi/.../*Repository.kt`

- [ ] **Bound sync pull and dirty push batches**
  Sync pull uses unbounded `findChangesSince()`, and desktop push loads all dirty messages at once. Add page/batch limits with continuation cursors or repeated batch sync until drained.
  — `platform-core/.../service/MessageService.kt`, `platform-core/.../service/ContactService.kt`, `platform-sync-client/.../sync/SyncService.kt`

- [ ] **Avoid loading all users for admin row actions**
  Admin enable/role actions call `securityService.listUsers()` and then search in memory. Add a `findUserSummary(id)` or use `findById` for the target row.
  — `platform-web/.../web/UserAdminRoutes.kt`, `platform-security/.../security/SecurityService.kt`

- [ ] **Stream or page CSV exports**
  Export routes build full CSV responses in memory; audit export requests `Int.MAX_VALUE`. Stream rows, page through repositories, or cap/export asynchronously for large datasets.
  — `platform-web/.../web/ExportRoutes.kt`, `platform-web/.../web/UserAdminRoutes.kt`, `platform-web/.../export/*ExportProvider.kt`

- [ ] **Wire runtime cache settings into `CaffeineMessageCache`**
  `RuntimeConfig.cacheMessageMaxSize` and `cacheMessageExpireMinutes` are parsed but the cache still uses hardcoded defaults. Pass runtime config into the cache constructor.
  — `platform-core/.../RuntimeConfig.kt`, `platform-core/.../persistence/CaffeineMessageCache.kt`, `platform-web/.../di/WebModule.kt`

- [ ] **Replace prefix-scan message cache invalidation**
  `CaffeineMessageCache.invalidateByPrefix()` scans all cache keys on every message mutation. Split list/entity caches, use generation-versioned list keys, or another O(1) invalidation scheme.
  — `platform-core/.../persistence/CaffeineMessageCache.kt`, `platform-core/.../service/MessageService.kt`

- [ ] **Add text-search indexes for `%LIKE%` search paths**
  Message/contact search uses case-insensitive contains queries without trigram/full-text indexes. Add PostgreSQL `pg_trgm` GIN indexes or full-text search for these paths.
  — `platform-persistence-jooq/src/main/resources/db/migration/`, `platform-persistence-jooq/.../JooqMessageRepository.kt`, `platform-persistence-jdbi/.../JdbiMessageRepository.kt`

- [ ] **Reduce paired list/count pagination queries**
  Message and contact page builders run a list query plus an exact count query for each paginated view. For larger tables and filtered searches, consider seek pagination, approximate counts, cached counts, or only counting when totals are visible/needed.
  — `platform-core/.../service/MessageService.kt`, `platform-web/.../web/ContactsPageFactory.kt`, `platform-core/.../service/ContactService.kt`

- [ ] **Push search ranking and limits closer to providers**
  Search currently asks each provider for up to the full page limit, merges all results, sorts in memory, then truncates. As providers grow, use provider-side ranking/limits or a central indexed search provider to avoid extra query and sort work.
  — `platform-web/.../web/SearchRoutes.kt`, `platform-web/.../web/SearchPageFactory.kt`, `platform-web/.../search/*SearchProvider.kt`

- [ ] **Use a bounded executor for Segment analytics**
  `SegmentAnalyticsService` starts one daemon thread per event. Replace with a bounded single-thread executor or persistent batching.
  — `platform-core/.../analytics/SegmentAnalyticsService.kt`

- [ ] **Precompute JTE template class lookup**
  Precompiled template rendering scans `JteClassRegistry.allClasses` on each render. Build a `Map<String, Class<*>>` once; consider caching template wrappers if safe.
  — `platform-web/.../infra/JteClassRegistry.kt`, `platform-web/.../infra/JteInfra.kt`

- [ ] **Debounce desktop search reloads**
  `DesktopSyncEngine.setSearchQuery()` reloads both messages and contacts immediately on every query change. Debounce input and only load the active data set where possible.
  — `platform-sync-client/.../engine/DesktopSyncEngine.kt`

### High Priority

- [x] ~~**Configurable CSP policy via AppConfig**~~
  Currently hardcoded in `DEFAULT_CSP_POLICY`. Add `cspPolicy` field that can be overridden via env/YAML. Keep default for security but allow deployments to customize.
  — `platform-core/.../AppConfig.kt`

- [ ] **TOTP two-factor authentication**
  Mentioned in `architecture.md` as a future evolution. Add TOTP secret generation, verification, setup flow in settings, and enforcement during login.

### Medium Priority

- [ ] **Unified Settings page — `/settings` with tabs**
  Consolidate Profile, Password, API Keys, Notifications, and Appearance into a single tabbed `/settings` page. Currently each is on its own separate route.

- [ ] **Search SPI — `SearchProvider` interface**
  Define a `SearchProvider` interface and aggregate results across plugins on `/search?q=`. Currently search uses a hardcoded empty list.
  — `platform-web/.../web/SearchRoutes.kt`

- [ ] **Export SPI — CSV/JSON export for any entity**
  Generic export framework that can serialize lists of entities (messages, contacts, etc.) to CSV or JSON. Useful for admin dashboard and user data portability.

- [x] ~~**Mobile responsive layout**~~
  `SidebarLayout` and `TopbarLayout` are desktop-oriented. Add responsive breakpoints, hamburger navigation, touch-friendly controls.

- [ ] **Jazzer fuzz tests for high-risk surfaces**
  Add Jazzer (JVM fuzzing) tests for: CSP parsing, JWT validation, OAuth callback parsing, rate limiter token bucket math, input validation.

### Low Priority

- [ ] **JavaFX desktop module implementation**
  Design spec exists and module is scaffolded (`platform-desktop-javafx`) but not implemented. Implement sync client UI using JavaFX as an alternative to Swing.
