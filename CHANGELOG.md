# Changelog

All notable changes to this project are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [Unreleased]

---

## [2.0.0] – 2026-05-04

### Breaking
- **Removed H2 database support.** PostgreSQL is now the sole database engine. H2 file-based and in-memory databases are no longer supported. All tests and production defaults use PostgreSQL exclusively.
- Production default `jdbcUrl` changed from `jdbc:h2:file:...` to `jdbc:postgresql://localhost:5432/outerstellar` with credentials `outerstellar`/`outerstellar`. Existing deployments must set `JDBCURL`, `JDBCUSER`, and `JDBCPASSWORD` environment variables or use `APP_PROFILE=postgres`.
- jOOQ code generation (`-Pjooq-codegen`) now requires a running PostgreSQL instance instead of a file-based H2 database. Use `docker/podman-compose.yml` to start PostgreSQL locally.
- Test base classes renamed: `H2JooqTest` → `JooqTest`, `H2JdbiTest` → `JdbiTest`, `H2WebTest` → `WebTest`. Downstream code extending these must update imports.

### Changed
- `SQLDialect.H2` removed from `PersistenceModule` — always uses `SQLDialect.POSTGRES`
- Maven `test-db` profile auto-starts Podman PostgreSQL for local test runs
- CI uses GitHub Actions PostgreSQL service containers
- `flyway-database-postgresql` added to persistence modules (required by Flyway 12+)

---

## [1.4.2] – 2026-04-13

### Fixed
- Eliminated sidebar selector reload delay: inlined `SidebarSelector` into `ShellView` so theme/language/layout changes no longer trigger a separate HTMX component fetch before applying (#139)
- Resolved all Detekt weighted violations in `platform-web`: import ordering across 70+ files, lambda wrapping/indentation in `App.kt`, `ReturnCount` in `ThemeCatalog.kt`, `MagicNumber` in `WebPageFactory.kt` (#144)
- Resolved open code scanning alerts from Scorecard, Semgrep, and zizmor (#138)

### Changed
- Bumped `jdbi3-core` and `jdbi3-kotlin` from 3.52.0 to 3.52.1 (#141)
- Bumped `koin-core-jvm`, `koin-test-jvm`, and `koin-test-junit5` from 4.2.0 to 4.2.1 (#140)

---

## [1.4.1] – 2026-04-08

### Fixed
- BOM `dependencyManagement` entries now use `${outerstellar.platform.version}` (a literal property) instead of `${project.version}`, preventing downstream projects from resolving platform modules at their own version rather than `1.4.1`
- `devAutoLogin` loopback guard now validates `request.source?.address` in addition to the `Host` header, preventing spoofing via a `Host: localhost` header from a remote client
- Docker multi-stage build: corrected stale JAR glob, wrong main class (`dev.outerstellar` → `io.github.rygel`), and missing `/app/data` write permissions for H2
- Switched from `maven-assembly-plugin` to `maven-shade-plugin` with `ServicesResourceTransformer` so SPI registrations (e.g. hoplite-yaml parser) survive fat-jar packaging
- CI: `docker-e2e` test tag now excluded from the standard build so `DockerSmokeTest` only runs in the dedicated Docker E2E workflow

### CI
- Added Docker E2E smoke-test workflow (`ci-docker-e2e.yml`): builds image, starts container with `DEVMODE=true`, polls `/health`, then runs Playwright smoke tests with browser cache inside `target/`
- Updated `codeql-action`, `hadolint-action`, `scorecard-action`, `semgrep` and `zizmor` to SHA-pinned v4.35.1 / latest

### Changed
- Bumped `outerstellar-framework` from 1.0.13 to 1.0.14
- Bumped `http4k` from 6.37.0.0 to 6.39.1.0
- Bumped `jOOQ` from 3.20.11 to 3.21.1
- Bumped `flyway` from 12.1.1 to 12.3.0
- Bumped `SpotBugs Maven Plugin`, `byte-buddy`, `outerstellar-i18n-validator-maven-plugin`

---

## [1.3.5] – 2026-03-28

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

## [1.3.5] – 2026-03-28

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

## [1.3.4] – 2026-03-26

### Fixed
- Isolate platform JTE classes in `gg.jte.generated.precompiled.outerstellar` to prevent fat JAR conflicts when platform-web is used as a library alongside apps with their own JTE templates

---

## [1.3.3] – 2026-03-24

### Added
- `PluginContext.currentUser(request)` — one-liner to get the authenticated user without manual LensFailure handling
- `PluginContext.buildPage(request, title, section, data)` — wraps plugin ViewModels in the platform shell with CSRF token, theme, and nav
- `PluginContext.forTesting(renderer, securityService, userRepository)` — factory with sensible defaults for plugin testing

### Fixed
- Migrated deprecated Koin `checkModules()` to `verify()` API (core, persistence, sync-client modules)
- Migrated deprecated http4k `RequestContexts` to `RequestKey` in SecurityRulesTest
- Fixed `PolyHandler` import from deprecated `org.http4k.server` to `org.http4k.core`
- Fixed parameter name mismatches in repository implementations (`since`, `token`)
- Removed unnecessary `!!` on non-null receivers and redundant elvis operators
- Simplified always-true condition in SwingSyncApp
- Renamed test methods with Windows-unsafe `?` character
- Bumped `outerstellar-framework` from 1.0.11 to 1.0.13

---

## [1.3.2] – 2026-03-24

### Fixed
- Add `<name>` to all child poms (required by Maven Central validation)
- Add explicit versions to OpenTelemetry deps in `dependencyManagement`
- Attach source JARs for all modules via `maven-source-plugin`
- Bump `central-publishing-maven-plugin` from 0.7.0 to 0.10.0

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
