# Changelog

All notable changes to this project are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [Unreleased]

---

## [1.3.2] – 2026-03-28

### Security
- `globalErrorHandler`: HTMX error responses now only expose `e.message` for `OuterstellarException` subclasses (user-facing, intentional messages). Generic JVM exceptions (JDBI SQL errors, NPEs, etc.) return a safe `"Action failed"` fallback, preventing internal details from leaking to any client that sends `HX-Request: true`.
- `devAutoLogin`: Added loopback guard — rejects auto-login when `X-Forwarded-For` is present (proxy) or `Host` is a non-loopback address. Eliminates the misconfiguration risk where a production deployment with `devMode=true` would auto-authenticate remote clients as admin.

### Performance
- `platform-web` tests now use `TemplateEngine.createPrecompiled()` via `jte.production=true` Surefire property — eliminates runtime JTE compilation, saving ~60 s per CI run
- Shared `TemplateRenderer` cached as `by lazy` in `H2WebTest.Companion` — one engine instance reused across all tests in the JVM process

### Changed
- Bumped `outerstellar-framework` from 1.0.13 to 1.0.14
- Bumped `http4k` from 6.37.0.0 to 6.38.0.0
- Bumped `jOOQ` from 3.20.11 to 3.21.1
- Bumped `junit` from 5.12.2 to 5.14.3
- Bumped `flyway` from 12.1.1 to 12.2.0

### CI
- Fixed `codeql-action` SHA comment from generic `# v4` to precise `# v4.34.1` (resolves zizmor/Scorecard mismatch warning)

---

## [1.3.1] – 2026-03-24

### Added
- Maven Central publishing support via `central-publishing-maven-plugin` (`mvn deploy -Prelease`)
- `<developers>` and `<scm>` metadata required by Central
- `maven-source-plugin` for source JARs, GPG signing and javadoc JARs in release profile

### Fixed
- Applied ktfmt formatting across core and web modules (78 files)

---

## [1.3.0] – 2026-03-24

### Changed
- Renamed all module artifactIds to `platform-*` prefix (`core` → `platform-core`, `web` → `platform-web`, etc.)
- Renamed all module directories to match their artifactIds
- Renamed `platform-api-client` to `platform-sync-client` for clarity
- Renamed parent artifactId from `outerstellar-platform-parent` to `platform-parent`
- Bumped `outerstellar-framework` from 1.0.7 to 1.0.11

### Fixed
- CodeQL workflow skips enforcer during `mvn compile` (test-jars unavailable at compile phase)
- Consolidated consecutive `RUN` instructions in Dockerfile (CodeQL warning)

---

## [1.2.8] – 2026-03-23

### Changed
- Maven version now matches release tag (was stuck at 1.2.0 for all releases)
- Framework dependency bumped to 1.0.7 (PluginManager SLF4J, ThemeService performance, ParameterInjector regex, I18n hot-reload fix)

### Performance
- Health endpoint uses `countAll()` instead of `findAll().size` — no longer loads all users into memory
- `deleteAccount()` uses `countByRole()` instead of loading all users to count admins
- Rate limiter replaced unbounded `ConcurrentHashMap` with Caffeine (TTL + max 10K entries)
- HikariCP: added `maxLifetime` (30min), `leakDetectionThreshold` (60s), pool size increased to 20
- Pagination enforced `MAX_PAGE_LIMIT = 1000` on all list endpoints

### Security
- CSRF token comparison uses `MessageDigest.isEqual` (constant-time, prevents timing attacks)
- Avatar URL validation blocks private/internal IPs (SSRF protection) and enforces 2048 char limit
- Password reset rate limit tightened to 5 requests per 15 minutes (was 10 per 60s)
- JWT cache exposes `invalidate()` method for token revocation on logout

### Reliability
- `AsyncActivityUpdater` registers JVM shutdown hook to flush pending writes

### CI
- Publish workflow auto-creates GitHub release on version bump merge to main
- Sync workflow creates PR instead of direct push (respects branch protection)

---


## [1.2.7] – 2026-03-23

### Added
- Fine-grained permissions — wildcard `domain:action:instance` permission model with `Permission`, `PermissionResolver` interface, and `RoleBasedPermissionResolver`
- `SecurityRules.hasPermission()` — route-level permission enforcement using pluggable resolvers
- Multi-realm authentication — `AuthRealm` interface with `AuthResult` sealed class for composable bearer token resolution
- `SessionRealm` and `ApiKeyRealm` — built-in realm implementations wrapping existing auth mechanisms
- Dynamic test passwords — `testPassword()` replaces all hardcoded credentials across 26 test files

### Changed
- Bearer auth filter refactored to iterate a configurable `List<AuthRealm>` chain instead of hardcoded session→API key logic
- `X-Session-Expired` header preserved via `AuthResult.Expired` in the realm chain

### Fixed
- `testPassword()` stored in variables to ensure register/login use the same password (was generating different passwords per call)
- `testPassword()` inside raw string literals fixed to use string interpolation
- Duplicate `outerstellar.version` and `http4k.version` entries removed from `pom.xml`

---

## [1.2.6] – 2026-03-22

### Fixed
- `baselineOnMigrate(true)` added to `migratePlugin()` for Flyway baseline support

### Changed
- Dependencies bumped: http4k 6.37.0.0, JDBI 3.52.0, ByteBuddy 1.18.7

---

## [1.2.5] – 2026-03-22

### Fixed
- CodeQL Maven auth using `s4u/maven-settings-action`

---

## [1.2.4] – 2026-03-22

### Added
- Plugin filter hook — `PlatformPlugin.filters` for custom request/response filters
- Parallel test execution enabled for faster CI builds

### Fixed
- detekt `MaximumLineLength` and `MaxLineLength` rules disabled (ktfmt handles line wrapping)
- SpotBugs null-pointer warnings resolved in `SyncApi`
- JDBI and jOOQ persistence modules aligned

### Changed
- Platform made domain-agnostic with optional services (message, contact, notification services all nullable)

---

## [1.2.3] – 2026-03-22

### Changed
- All platform database tables prefixed with `plt_` to avoid naming collisions with consumer apps

### Fixed
- Remaining unprefixed table references resolved

---

## [1.2.2] – 2026-03-22

### Added
- User preferences persistence — language, theme, and layout are stored in the User model (V4 migration) so preferences follow the user across devices and sessions
- `persistUserPreferences` — authenticated users' preference changes are automatically saved to the database via `stateFilter`
- Automated main→develop sync workflow — no more manual sync PRs after releases
- ktfmt/detekt import ordering conflict resolved via `.editorconfig` (`ktlint_standard_import-ordering = disabled`)

### Performance
- ThemeCatalog CSS caching — `toCssVariables()` and `toExtendedCssVariables()` results are cached in `ConcurrentHashMap`, eliminating repeated string building on every profile page load

### Fixed
- SidebarSelector JTE template compilation error — nullable `previewColors` accessed without smart-cast inside null-checked block
- ThemeCatalog `parseHexRgb` returns empty `IntArray` instead of `null` to satisfy SpotBugs `PZLA_PREFER_ZERO_LENGTH_ARRAYS`
- jOOQ generated code directory path corrected from `dev/outerstellar` to `io/github/rygel/outerstellar`

### Changed
- Framework dependency bumped to 1.0.6 (Translatable listener pattern, Language registry)
- WebContext reads preferences from User model first, then cookies, then defaults (backward compatible for anonymous users)
- ktfmt formatting applied across web and desktop modules (import ordering, line wrapping at 120 chars)

---

## [1.0.6] – 2026-03-18

### Added
- Shared `components/Pagination.kte` component — single source of truth for prev/next navigation; replaces three divergent inline implementations across AuditLog, UserAdmin, and Contacts pages
- Shared `components/PageHeader.kte` component — unified page title/description/breadcrumb block with an optional `@Content` slot for action buttons
- Shared `components/ContactForm.kte` HTMX modal — create and edit contacts without leaving the page
- `.data-table` CSS class (plus `.th-center`, `.td-center`, `.td-mono` modifiers) — replaces inline `style=` on every `<th>` and `<td>` across AuditLog, UserAdmin, and ApiKeys pages
- Contact CRUD routes: `POST /contacts`, `GET /contacts/new`, `GET /contacts/{id}/edit`, `POST /contacts/{id}/update`, `POST /contacts/{id}/delete`
- i18n keys for contact form labels (EN + FR)

### Fixed
- `jackson-module-kotlin` promoted from `test` to `compile` scope in `platform-web/pom.xml`; the previous `test`-scope declaration silently overrode the transitive `compile` dependency from `http4k-format-jackson`, breaking `ThemeCatalog` at compile time

---

## [1.0.5] – 2026-03-18

### Performance
- Skip BCrypt encode on every restart — admin password is only hashed when the admin user does not yet exist, saving ~100 ms per boot after first run
- HikariCP `minimumIdle` reduced from 2 to 1, opening one fewer eager DB connection at startup
- Enabled `CaffeineMessageCache` (max 1 000 entries, 10-minute TTL) in place of the no-op cache; entity-level hits benefit the sync push path and list results are cached between writes

### Fixed
- `AppConfig` profile load order corrected so profile-specific YAML (`application-prod.yaml`) overrides the base `application.yaml` as intended

### Changed
- `SwingAppConfig` now supports profile-based config loading (`APP_PROFILE` env var) matching the web application pattern
- Added `platform-desktop/src/main/resources/application.yaml`, `application-dev.yaml`, and `application-prod.yaml` resource files for the desktop module

---

## [1.0.4] – 2026-03-18

### Security
- Replaced raw UUID bearer tokens with opaque session tokens (`oss_` prefix, 192-bit entropy)
- Session tokens are stored as SHA-256 hashes in a new `sessions` table; raw tokens are never persisted
- Sliding-window expiry: every authenticated request extends the session by the configured timeout (default 30 minutes)
- Expired sessions return `HTTP 401` with `X-Session-Expired: true` header so clients can distinguish expiry from invalid tokens
- API key bearer auth preserved as a fallback for `osk_`-prefixed tokens

---

## [1.0.3] – 2026-03-17

### Added
- `OAuthRepository` and `DeviceTokenRepository` interfaces with Jooq and Jdbi implementations
- `MessageRestoreIntegrationTest` covering soft-deleted message restore flow

---

## [1.0.2] – 2026-03-17

### Changed
- All CDN-hosted frontend dependencies (htmx, Alpine.js, chart.js, etc.) vendored as local static assets — no external network calls at runtime

---

## [1.0.1] – 2026-03-16

### Changed
- Updated all dependencies to latest stable versions
- Upgraded ktfmt, Jackson, and Maven plugins
- Fixed Playwright test compatibility after dependency updates
- Applied ktfmt formatting to `SyncModels.kt`

---

## [1.0.0] – 2026-03-16

Initial release.
