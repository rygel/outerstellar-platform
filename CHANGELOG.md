# Changelog

All notable changes to this project are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [3.6.19] – 2026-07-07

### Fixed

- **Platform-rendered pages resolve their own i18n bundle** — `ShellRenderer.cachedI18n` now loads the platform `web.*` message bundle through the platform's own classloader instead of the thread context classloader. When the platform is consumed as a library by a host app, the TCCL may not see `platform-core`'s `messages.properties`, or may find a host-shipped root-level `messages.properties` first and shadow it — either way every `web.*` key rendered raw on platform pages (`/auth` showed `web.auth.heading`, `web.app.title`, …). Pinning to the platform classloader resolves the bundle regardless of host wiring and stays locale-aware (`messages_fr.properties` still resolves). Also aligns the two desktop i18n load sites to the same pattern (#594, #595).

---

## [3.6.20] – 2026-07-07

### Fixed

- **Platform Flyway migrations namespaced under `db/migration/platform/`** *(boot-blocking regression in v3.6.19)* — the platform's own migrations moved out of the shared `db/migration/` root into a platform-owned subdirectory, and `DatabaseInfra.migrate()` now scans `classpath:db/migration/platform` instead of the shared parent. Before this, a host app bundling the platform alongside other migration-bearing modules failed to boot with `Found more than one migration with version 1`: Flyway recursed into sibling subtrees (`db/migration/audit/`, `db/migration/<extension>/`, …) and collided on `V1`, and discarded nested extension locations as sub-locations of the parent. Existing databases validate unchanged (history rows are keyed by version + checksum, not path). Enacts ADR-0004 (#601).
- **`UrlValidator` over-broad catch** — `catch (e: Exception)` narrowed to `catch (e: URISyntaxException)`; `URI(...)` only throws `URISyntaxException`, so the broad catch no longer swallows unrelated runtime exceptions and misreports them as URL parse failures.
- **`OutboxProcessor` dead local store** — `var batchSize` now initialized at declaration (`= 0`).
- **`ContactExportProvider` defensive copy** — the exported row's `emails` list is now copied (`.toList()`) at construction.

### Changed

- **SpotBugs exclusion filter documented** — `config/spotbugs-exclude.xml` reorganized into 7 commented sections (generated code, Kotlin idiom false positives, Kotlin-collection exposure, companion false positives, interface contracts, Kotlin `use {}` resource analysis, desktop/JavaFX scaffold) with a per-entry rationale and a policy header. Two stale `AppKt` suppressions (referencing code that no longer exists) removed. The audit found most existing suppressions were legitimate SpotBugs/Kotlin-idiom false positives, not hidden bugs.
- **Supply-chain hardening** — Dependabot configs gained a 7-day `cooldown` per ecosystem; `.npmrc` adds `min-release-age=7` (npm v11.10.0+); the Gravatar MD5 use in `AvatarUrls.kt` is suppressed with an explicit rule-id (MD5 is spec-mandated by the Gravatar API). The chronic `Semgrep Scan` advisory CI failure is now green.
- **ADR-0004** added — records the decision that platform Flyway migrations must be namespaced under a platform-owned subdirectory.

### Dependencies

- Bumped `bytebuddy.version`, `junit.version`, `spotless-maven-plugin`, `playwright`, `logback-classic`, graalvm `native-maven-plugin`, and grouped `http4k` / `persistence` / `github-actions` updates via Dependabot.

---

## [Unreleased]

### Fixed

- **Extension migrations run in an isolated Flyway pass with their own history table** *(boot-blocking regression in v3.6.19–v3.6.20)* — `DatabaseInfra.migrate()` previously merged the platform's and the extension's migration locations into a single Flyway pass against the default `flyway_schema_history`, so a platform `V1` and an extension `V1` collided and host apps failed to boot with `Found more than one migration with version 1`. The extension's declared `ExtensionMigrations.historyTable` was ignored. Migrations now run as **two** isolated `Flyway.migrate()` calls: the platform pass against `flyway_schema_history`, the extension pass against the extension's declared history table (baselined at 0). The two V1s never share a resolver. `ExtensionMigrations.historyTable` is no longer deprecated. The legacy `repairLegacyExtensionHistoryTable` consolidation step (added in #453) is removed — it was the opposite of the separate-table design and would destroy an extension's active table (#611).

---

## [3.6.18] – 2026-06-20

### Security

- **Token hashing upgraded to HMAC-SHA256 with deployment pepper** — session tokens, API keys, and password-reset tokens are now hashed with `HmacSHA256` keyed by a configurable `TOKEN_PEPPER`, not un-keyed SHA-256. A DB-read attacker can no longer use stored hashes directly; the pepper is rotatable (#571).
- **TOTP enable/disable writes are transactional** — a failure between `updateTotpSecret` and `enableTotp` no longer leaves 2FA in an inconsistent state (C1, #572).
- **OAuth user creation is transactional** — user + OAuth connection are saved atomically; no more orphaned accounts with no linkage (C2, #572).
- **CachingUserRepository cache-invalidation on lockout** — 5 lockout/failed-attempt methods now invalidate the cache, closing a 60s window where a locked account could still authenticate (H1, #572).
- **Registration validates email format + username length** — `register()` now enforces `MAX_USERNAME_LENGTH` (50) and `EMAIL_REGEX`, matching `updateProfile`. No more unbounded non-email usernames stored as emails (H4, #573).
- **Error-page fallback no longer leaks raw exception messages** — the fallback when the JTE error template itself fails returns a static `"Internal Server Error"` body instead of echoing SQL/JDBI internals (H6, #573).
- **Open-redirect backslash bypass blocked** — `safeReturnTo` now rejects `/\` prefixes that some browsers normalize to `//` (M4, #574).
- **Vote race condition guarded** — `VoteService.vote` wraps the check-then-act in a transaction; duplicate-key violations are caught gracefully (H3, #574).

### Changed

- **Duplicate-key handling on profile/OAuth** — `AccountService.updateProfile` and `OAuthService.findOrCreateOAuthUser` catch concurrent unique-constraint violations and translate to user-facing errors or re-read the winner, instead of raw 500s (M1, M2, #574).
- **Email drops surfaced as ERROR** — `ResilientEmailService` circuit-open drops now log at ERROR instead of WARN (H5, #573).
- **Analytics errors surfaced as WARN** — the analytics page-view filter logs failures at WARN instead of DEBUG (L1, #573).

### Fixed

- **OpenAPI spec endpoints degrade to 503** — the http4k OpenApi3/kotlinx.serialization incompatibility (http4k#750) now returns a clear 503 instead of a 500 (#558, #566).
- **Analytics resources closed on shutdown** — `SegmentAnalyticsService` executor + HttpClient are released in the shutdown hook (M1 audit, #567).
- **ConnectivityChecker thread-safety** — observer list swapped to `CopyOnWriteArrayList`; executor vars marked `@Volatile` (M2, L5, #567).
- **IN-clause/batch size capped** — `MAX_IN_CLAUSE` (1000) guard on all `bindList`/batch entry points prevents oversized-`IN` DoS (M3 audit, #568).
- **Dead TokenHashing duplicate removed** from `JdbiApiKeyRepository` (M4 audit, #568).
- **Outbox SQL uses bound `:status`** instead of enum-name interpolation (L8, #569).
- **QueryCount ThreadLocal `remove()`** instead of `set(0)` (L9, #569).
- **Push-notification stub drops logged at ERROR** (L7, #569).
- **Dev-mode JTE composite renderer** — extension templates from filesystem + platform templates from precompiled registry (no more 500 on platform templates in dev) (#560, #570).
- **Flyway out-of-order configurable** — `flyway.outOfOrder` system property / `FLYWAY_OUT_OF_ORDER` env var honored (#561, #570).

---

## [3.6.17] – 2026-06-18

---

## [3.6.17] – 2026-06-18

### Security

- **TOTP backup codes hardened** — backup codes are now hashed with BCrypt (matching password storage, logRounds=12) instead of unsalted SHA-256, and verification is constant-time via `BCrypt.checkpw`. Legacy SHA-256 codes are invalidated by design (#506, #511).
- **Config secrets masked in `toString()`** — `AppConfig` and nested config data classes no longer leak the DB password, JWT secret, SMTP password, OAuth private keys, or FCM/APNS credentials via `toString()` (logs, stack traces, observability). Non-blank secrets render as `***` (#524).
- **Login timing oracle closed** — the not-found login path now runs a dummy BCrypt verify so its latency matches the bad-password path, preventing account enumeration via response timing (#505).
- **JWT claims cache removed** — bearer tokens are re-verified on every request (HMAC verify is sub-ms), closing a 60s window where a token stayed valid after logout/password-change/role-change and exposing up to 2000 raw tokens in a heap dump (#507).
- **Password-reset token consumption is atomic** — a single `UPDATE ... WHERE used=false AND expires_at>now() RETURNING` replaces the non-atomic find+check+markUsed, closing a TOCTOU race where two concurrent resets with the same token both succeeded. Prior unused tokens are now invalidated when a new one is issued (#508).
- **TOTP brute-force bounded** — a per-user `failed_totp_attempts` counter (independent of the resettable per-partial-token cap) locks the account after N TOTP failures. An attacker with the password can no longer reset the cap by re-authenticating (#510).
- **Last-admin protection** — `setUserRole`/`setUserEnabled` refuse to demote or disable the last enabled administrator, preventing an unrecoverable lockout (#513).
- **Audit-log account enumeration closed** — all `AUTHENTICATION_FAILED` audit entries now use a single generic `"Invalid credentials"` detail; the per-reason discriminator stays in server logs only (#509).
- **Search-result URL XSS prevented** — `SearchPageFactory.safeHref()` allow-lists URL schemes at the provider-output chokepoint; `javascript:`/`data:`/`vbscript:` (and case/whitespace bypasses) collapse to `#` so a malicious/buggy `SearchProvider` can't inject script via the href (#517).
- **Request body-size limit** — a new `maxBodySize` filter rejects oversized request bodies (413) before they're buffered into memory, defaulting to 2 MiB (`MAX_REQUEST_BODY_BYTES`), preventing oversized-POST DoS (#516).
- **Fail-loud config defaults** — default JDBC credentials now abort startup (`System.exit(1)`) in non-dev/test profiles; `bool()` env parsing rejects invalid values instead of silently disabling security flags; `JwtConfig` rejects `enabled`+blank-`secret` at construction; `DEFAULT_JDBC_PASSWORD` is no longer public API (#527).
- **CSRF cookie `Secure` flag dropped** — the `_csrf` double-submit cookie is now non-Secure so browsers store it over HTTP (localhost dev, TLS-terminating proxies); CSRF protection comes from token-matching, not transport (#515).
- **HikariDataSource closed on shutdown** — the DB pool is now released in the shutdown hook (`finally`-guarded); previously it was abandoned on every JVM shutdown, leaking Postgres connections (#525).
- **Shutdown robust to per-step exceptions** — each cleanup step (flush, outbox, server-stop, persistence-close) is individually `try/catch(Throwable)`-guarded so one failure no longer aborts the rest (#526).

### Added

- **Health endpoints** — `/health/live` (always 200, no dependency probe) and `/health/ready` (DB+extension readiness, 503 when DOWN) for orchestrator liveness/readiness probes; `/health` kept as a backward-compatible readiness alias (#528).
- **Admin index handler** — bare `GET /admin` now 302-redirects to `/admin/extensions` instead of 404, and `/admin` is always defined when any admin content is mounted (#531).
- **Sync wire-format schema version** — `SyncPullResponse`/`SyncPushResponse` carry a `schemaVersion`; the client rejects pull responses whose version differs (`SYNC_SCHEMA_VERSION`), preventing silent data corruption across client/server version skew (#523).

### Changed

- **Session cookie Max-Age** — all session-establishing routes (login, OAuth, TOTP, dev-auto-login) now set `Max-Age` from `sessionTimeoutMinutes` so the browser lifetime matches the server policy (#518).
- **Sign in with Apple consistently disabled** — the provider returns the not-configured stub for both `authorizationUrl` and `exchangeCode` until the token-exchange implementation is complete (gated behind `TOKEN_EXCHANGE_IMPLEMENTED`), so users are never routed through Apple only to hit a 500 (#514).
- **Vote repository queries use `bindList`** — `JdbiVoteRepository.findScoresByMessages` uses JDBI `bindList` instead of string-concatenated `IN(...)` placeholders, matching the rest of the persistence layer and restoring prepared-statement plan caching (#529).
- **Sync conflict handling preserves local content** — conflicts now use `repository.markConflict` (preserving both versions for the UI and `resolveConflict`) instead of unconditionally overwriting the local row; `SyncConflict` gained a `clientMessage` field (#521).
- **TOTP backup-code JSON via kotlinx.serialization** — the hand-rolled concat/split serializer (which corrupted on `,` `"` `\`) is replaced by `ListSerializer(String.serializer())`; stored format unchanged, no migration (#512).

### Fixed

- **Sync dirty flag cleared after push** — `doSync()` now calls `repository.markClean` after a successful push, so locally-originated messages aren't re-pushed on every sync cycle (#519).
- **Sync pull loop bounded** — max-round (100) and max-item (50_000) guards prevent infinite loops / OOM when the server signals `hasMore=true`; an empty batch with `hasMore=true` is treated as a protocol error (#520).
- **Sync 401 routed to session-expired** — `HttpSyncClient.pull`/`push` throw `SessionExpiredException` specifically for 401/403, so auto-sync stops hammering a dead token and the user is told to re-authenticate (#522).
- **Missing `plt_message_votes` table** — the table queried by `JdbiVoteRepository` had no `CREATE TABLE` anywhere; added via V14 migration. First `JdbiVoteRepositoryTest` (9 cases) added (#529).
- **Redundant reset-token index dropped** — V15 drops `idx_plt_password_reset_tokens_token`, which duplicated the `UNIQUE(token)` constraint's btree (#530).
- **Dead `AppConfigTest` methods** — three `@Test` functions nested inside another test's body (never executed by JUnit) hoisted to class top level (#536).
- **Graceful shutdown closes the DB pool even if an earlier step throws** — see #526.

---

## [3.6.16] – 2026-06-14

### Added

- **Configurable security headers** — all response headers (`Permissions-Policy`, `X-Frame-Options`, `Referrer-Policy`, `X-Content-Type-Options`, `Strict-Transport-Security`) are now configurable via YAML/env vars with per-route overrides using Ant-style glob patterns. Extensions can no longer be blocked by hard-coded security headers (#501).

---

## [3.6.15] – 2026-06-10

### Added

- **Bulk route registration** — `ExtensionRouteContributionRegistry` now supports `publicUiAll`, `protectedUiAll`, `apiAll`, `adminAll`, `registerAll`, and `pages` methods for registering multiple routes in a single call, reducing extension `contribute()` boilerplate (#488).

### Changed

- **JTE template registry validation** — startup now warns when no `PrecompiledJteTemplateRegistry` implementations are discovered via ServiceLoader; template-not-found errors log registered class count and sample class names for faster diagnosis (#484).
- **Process label** — `start-web.ps1` passes `-Dprocess.label=outerstellar-web-dev` to the application JVM for easier process identification on shared machines (#476).

---

## [3.6.14] – 2026-06-09

### Added

- **External static asset directories** — platform and extension assets can now be loaded from configured filesystem directories before packaged resources, enabling deployment-specific CSS, JavaScript, and media overrides without rebuilding artifacts (#479).
- **Platform diagnostics** — added local-only route diagnostics with route owner, group, method, path pattern, description, handler kind, extension readiness checks, and excluded page sets (#476).
- **Extension readiness checks** — extensions can now report named `UP`, `WARN`, or `DOWN` readiness checks with actionable messages; required `DOWN` checks make `/health` fail for release and operations visibility (#476).

### Changed

- **Shell component ownership** — `/components/footer-status` is registered through platform component routes so shell templates no longer reference an unregistered component endpoint (#476).
- **Asset loading** — removed the unconditional Remix icon font preload while retaining the stylesheet load, avoiding unnecessary preload noise for pages that do not need the font immediately (#476).
- **Process hygiene** — dev and test scripts now mark Maven-launched JVM processes with stable `agent.owner` and `agent.task` values and document shared-machine process identification (#476).

---

## [3.6.13] – 2026-06-08

### Fixed

- **Global error handler fallback response** — when an exception occurs and rendering the normal error page also fails, the platform now returns a plain-text 500 response containing the original exception message instead of replacing it with a generic emergency HTML page.

---

## [3.6.12] – 2026-06-07

### Added

- **Nonce-based Content Security Policy support** — generated per-request CSP nonces, expanded `{nonce}` in configured policies, and exposed the nonce to platform and extension shell rendering so scripts can run without `unsafe-inline` (#472).

### Fixed

- **Extension contract route parameters** — mounted extension `ContractRoute` registrations through shared contracts per route group so path-parameter routes resolve instead of returning 404 (#471).
- **Error-page approval stability** — normalized generated CSP nonces in error-page approval tests while preserving coverage for nonce-bearing script tags.

---

## [3.6.11] – 2026-06-06

### Changed

- **Sync client module wiring** — consolidated desktop and JavaFX sync client wiring through shared sync modules, reducing duplicated HTTP client and module construction paths.
- **Sync session lifecycle** — extracted session lifecycle behavior into dedicated sync engine components for clearer login/logout state handling.
- **Error handling discipline** — documented and enforced a no-hidden-fallback policy for error handling. CI-facing local validation now explicitly requires Detekt, SpotBugs, Checkstyle, PMD/CPD, Spotless, and i18n validation to run before pushing.
- **Dependency refresh** — updated http4k, Netty, Jackson, Byte Buddy, persistence dependencies, and GitHub Actions workflow pins from the develop maintenance queue.

### Fixed

- **Global error handler diagnostics** — preserved the original exception when error-page rendering fails, logging both the original failure and renderer failure instead of hiding the root cause.
- **Hidden fallback cleanup** — replaced silent fallback behavior across i18n loading, extension contribution assembly, web error/demo routes, component vote handling, desktop icon/tray setup, JavaFX update/theme handling, permission parsing, and sync logout with explicit errors, status responses, or logging.
- **Desktop icon resources** — added missing Remix icon resources so stricter icon loading fails only for genuinely absent assets.

---

## [3.6.10] – 2026-06-04

### Changed

- **Single Flyway migration history table** — Extension migrations now share `flyway_schema_history` with platform migrations instead of using a separate `flyway_extension_history` table. This allows JVM and native-image builds to coexist on the same database (closes #453).
- `migrate()` in `DatabaseInfra` now accepts `extensionLocation` and `extensionMigrationNames` to merge both migration sets into a single Flyway run.
- `repairLegacyExtensionHistoryTable()` auto-detects and migrates any existing `flyway_extension_history` rows into `flyway_schema_history` with re-ranked `installed_rank`, then drops the legacy table.
- `migrateExtension()` deprecated — kept as wrapper calling `migrate()` internally.
- `ExtensionMigrations.historyTable` deprecated — the field is now ignored.

---

## [3.6.9] – 2026-06-02

### Added

- **Extension Maven archetype** — Replaced the `starter-extension-app` with a proper Maven archetype (`outerstellar-platform-extension-archetype`). Extension authors now scaffold new projects with `mvn archetype:generate` instead of copying a directory. The archetype produces a two-module project (extension + host) with working JTE templates, contract tests, and native-image verification.
- **Extension parent POM** — New `outerstellar-platform-extension-parent` provides zero-config JTE build with `JteClassRegistryExtension` and `NativeResourcesExtension` preconfigured in `pluginManagement`.
- **`page()` / `publicPage()` convenience methods** — `ExtensionRouteContributionRegistry` now offers one-liner page registration with JTE template inference from the ViewModel's `template()` method.
- **`ExtensionTemplateContributionRegistry`** — Template overrides are now registered through the contribution context (`context.templates.override(...)`) instead of a separate interface method.

### Changed

- **Removed 8 deprecated methods from `PlatformExtension`** — `contribute()` is now the single extension point. Removed: `routeRegistrations()`, `layoutTemplate()`, `filters()`, `bannerProviders()`, `includePlatformPages()`, `mode()`, `id()`, `appLabel()`.
- **Removed deprecated `ExtensionHostContext` aliases** — Use grouped fields (`rendering.renderer`, `app.config`, `security.apiKeys`, etc.) instead of top-level shortcuts (`renderer`, `config`, `apiKeyService`, etc.).
- **Removed `typealias ExtensionContext`** — Use `ExtensionHostContext` directly.
- **Simplified `ExtensionContribution.from()`** — Now only calls `contribute()`, no longer falls back to deprecated methods.
- **`WebFactory` takes `ExtensionContribution?`** instead of `PlatformExtension?` — template overrides read from the contribution object, not the extension interface.
- **`starter-extension-app/` removed** — Replaced by the Maven archetype.

---

## [3.6.8] – 2026-06-02

### Added

- **Single-plugin native executable** — Native images can now be built as composed applications (platform + one plugin) from a distribution module. The `starter-host` module demonstrates the pattern with shade plugin (merging `META-INF/services` via `ServicesResourceTransformer`), GraalVM `native-maven-plugin`, and a `NativeRegistryVerify` preflight that fails the build unless both platform and extension JTE registries are present.
- **JTE template generation for extensions** — The `starter-extension` module now runs `jte-maven-plugin:generate` with `JteClassRegistryExtension`, producing a per-module `JteClassRegistry`, service file, and native-image reflection metadata.
- **Pre-native registry verification** — A preflight check (`NativeRegistryVerify`) runs before GraalVM compilation and fails fast with a clear error message if either the platform or extension JTE registry is missing from the composed classpath.
- **Multi-registry regression tests** — Four new tests in `JteInfraTest` covering single-registry failure, multi-registry miss, preflight requires ≥2 registries, and composed preflight pass with both registries.

### Changed

- **Starter extension renders via JTE** — The starter home route now uses the platform's `TemplateRenderer` with a JTE template instead of hardcoded HTML, proving the extension → platform rendering pipeline works end-to-end.

---

## [3.6.7] – 2026-06-01

### Changed

- **Dependency refresh** — Updated npm, GitHub Actions, http4k, logging, persistence, MockK, and Maven plugin dependencies from the develop branch maintenance queue.

### Fixed

- **Native extension template rendering** — Production JTE rendering now discovers precompiled template registries from extensions through `ServiceLoader`, so native-image hosts can resolve both platform and extension templates.

---

## [3.6.6] – 2026-06-01

### Changed

- **Breaking extension vocabulary cleanup** — renamed the public SPI from plugin/hosted-app terminology to the extension model, including `platform-extension-api`, `PlatformExtension`, `ExtensionHostContext`, `ExtensionContributionContext`, and `PlatformMode` values `FullPlatform`, `ExtensionHost`, and `Headless`.
- **Module and artifact consistency** — renamed the reactor and published coordinates to the new extension naming set, including `platform-testkit`, `platform-seeder`, and `outerstellar-platform-jte-extensions`.

### Fixed

- **JTE renderer mode split** — development resolves source templates explicitly, while production and tests use the precompiled JTE registry without a silent fallback.
- **Packaged JVM template rendering** — containerized JVM startup now opts into precompiled JTE templates so packaged images render without source template directories.
- **Validator Maven plugin build** — corrected the Maven plugin annotations dependency coordinates after moving the validator artifacts into this repository.

---

## [3.6.5] – 2026-05-31

### Added

- **Extension authoring improvements** — Improved developer experience for extension-host configuration and authoring.
- **Config-driven extension host server tests** — Support for configuration-driven integration tests for extension host servers (#410).
- **Extension host service separation** — Platform services separated from default UI for extension-host deployments, allowing finer-grained control over which platform capabilities are exposed (#376, #409).
- **I18n module moved into platform** — Migrated `outerstellar-i18n` into the platform monorepo for unified versioning and releases (#412).
- **Validator migrated in-repo** — Migrated the i18n validator library and Maven extension into the repository, cleaning stale external dependencies (#405).

### Changed

- **Extension host composition** — Extension-host deployments can now selectively include platform UI pages independent of platform services (#409).
- **Release version safeguards** — CI release workflow now validates CHANGELOG entry, SNAPSHOT status, duplicate tag, and CI success before publishing (#406).

### Fixed

- **Issue 407 follow-up** — Recovered follow-up changes for issue 407 (#411).

---

## [3.6.4] – 2026-05-28

---

## [1.6.3] – 2026-05-28

### Added

- **Extension Composition Model** — WordPress-like extension+theme separation with three platform modes: `FullPlatform` (default, zero change), `ExtensionHost` (extension opts into platform UI pages), `Headless` (API only, no HTML UI).
- **Route Registry** — Central `RouteRegistry` with ownership tracking (`RouteOwner`: PlatformKernel, PlatformUi, Extension), route groups (PublicUi, ProtectedUi, Api, Admin, Static, Health), conflict detection, and startup diagnostics (route table logged at boot, conflicts fail fast).
- **PlatformPageSets** — 8 bundled page sets (home, contacts, settings, search, notifications, profile, admin, dev-dashboard) that extensions opt into via `includePlatformPages()`.
- **PlatformTheme interface** — `DaisyUITheme` default with `headInjections()`, `bodyInjections()`, and `templateOverrides()` hooks for CSS/JS customization.
- **Extension SPI** — Extracted into `platform-extension-api` module with `ExtensionManifest`, `PlatformExtensionLayout`, `PlatformExtensionAssets`, `PlatformExtensionNavigation`, typed contribution contexts, and extension-facing facades for config, users, analytics, notifications, rendering, and security.
- **Jazzer fuzz tests** — Regression coverage for CSP parsing, JWT validation, OAuth callback parsing, rate limiter form parsing, token bucket behavior, and URL validation.

### Changed

- **PlatformExtension interface refactored** — `excludeDefaultRoutes` replaced with `includePlatformPages()` (opt-in model). Added `routeRegistrations()` returning `List<ExtensionRouteRegistration>` with `RouteGroup` ownership. Added `mode: PlatformMode` property.
- **App.kt route assembly refactored** — Replaced hardcoded route lists with `RouteRegistry`-based assembly. Mode-based conditional registration: `FullPlatform` registers all UI routes, `ExtensionHost` registers only included page sets, `Headless` registers no UI routes.
- **ExtensionContext narrowed behind facades** — Raw host services replaced with stable `app`, `users`, `analytics`, `notifications`, `rendering`, and `security` facades. Compatibility aliases preserved for migration.
- **ServerComponents accepts extension** — `createServerComponents(extension = MyExtension())` is the primary entry point.

### Architecture

- **Koin removed from server runtime** — Dependency injection replaced with explicit constructor wiring. No runtime DI framework dependency.
- **SecurityService decomposed** — Split into `AuthService`, `AccountService`, `SessionService`, `UserAdminService`, `TOTPService`. Direct sub-service wiring replaces facade delegation.
- **WebContext split** — Decomposed into `RequestContext` (per-request state) and `ShellRenderer` (layout data builder).
- **AuthRoutes decomposed** — Split into `AuthRoutes`, `PasswordRoutes`, `ProfileRoutes`, `ApiKeyRoutes` with `UiRouteSet(publicRoutes, protectedRoutes)` pattern.
- **JDBI shared infrastructure** — `JdbiSupport.kt` with `InstantArgumentFactory`, `InstantColumnMapper`, `FilterClause`, `escapeLike()`.
- **MessageCache type safety** — Typed accessor methods replacing unsafe casts and `@Suppress("UNCHECKED_CAST")`.

### New Modules

- `platform-extension-api` — Extension SPI, extension-facing DTOs, contribution contexts. Separated from `platform-web` for cleaner consumer dependencies.

---

## [1.6.2] – 2026-05-25

### Architecture

- **App.kt decoupling** — Replaced `app()` 25-parameter signature with 6 component groups (`PersistenceComponents`, `SecurityComponents`, `CoreComponents`, `WebComponents`). Deleted `OptionalServices`, `AuthServices`, `AppContext` intermediate classes.
- **AuthRoutes decomposition** — Split AuthRoutes (487 lines, 10 params) into AuthRoutes (login/register/recover), PasswordRoutes, ProfileRoutes, and ApiKeyRoutes.
- **Auth guard consolidation** — Applied `SecurityRules.authenticated` filter to all protected UI routes. Removed ~25 inline null-check guards. Introduced `UiRouteSet(publicRoutes, protectedRoutes)` pattern for default-authenticated routing.
- **JDBI shared infrastructure** — Created `JdbiSupport.kt` with `InstantArgumentFactory`, `InstantColumnMapper`, `FilterClause`, `escapeLike()`, and `ResultSet` extension helpers (`getNullableInstant`, `getRequiredInstant`, `getInstantOrDefault`). Removed ~40 lines of manual `Timestamp.from()`/`.toInstant()` boilerplate across 10 repositories.
- **DeviceTokenService extraction** — New service layer between `DeviceRegistrationApi` and `DeviceTokenRepository`. Moves platform validation, token construction, ownership checks, and audit logging out of route handlers.
- **OAuthRepository relocation** — Moved `OAuthRepository`, `OAuthConnection`, `OAuthUserInfo` from `platform-security` to `platform-core`. Fixes inverted dependency where `platform-persistence-jdbi` depended on `platform-security`.
- **VoteService/PollService wiring** — Moved service creation from `WebFactory` (platform-web) to `CoreComponents` (platform-core), consistent with `MessageService`/`ContactService` pattern.
- **MessageCache type safety** — Replaced untyped `get/put/getOrPut` (`Any`/`Any?`) with typed accessor methods (`getMessage`/`putMessage`, `getMessageList`/`putMessageList`, `getMessageListOrPut`). Eliminated all `@Suppress("UNCHECKED_CAST")` and unsafe casts from `MessageService`.

### Changed

- **TOTPService constructor injection** — Changed from hidden internal instantiation to constructor parameter in `AuthService`. Improves testability and ensures single instance via `SecurityComponents`.
- **FQCN cleanup** — Replaced 35+ fully-qualified class names with proper imports in `App.kt` and `WebTest.kt`.
- Removed dead `AdminPageFactory` from `WebComponents` (WebPageFactory has its own lazy instance).
- Removed duplicate `bannerProviders` parameter from `stateFilter` call.
- Removed unnecessary `.let {}` wrapper on non-nullable `totpService`.

### Internal

- Consolidated duplicate `docs/TODO.md` into root `TODO.md`. Marked 3 already-implemented items (TOTP UX, Search SPI, Export SPI) as done.
- Added `JdbiSupportTest` with 12 unit tests covering `escapeLike`, `InstantArgumentFactory`, `InstantColumnMapper`.
- Registered `InstantArgumentFactory`/`InstantColumnMapper` on both production and test JDBi instances.

---

## [1.6.0] – 2026-05-08

### Breaking
- **Full DaisyUI v5 migration.** All custom CSS (817 lines) removed and replaced with DaisyUI v5.5.19 semantic components. The web UI now uses DaisyUI's `data-theme` attribute for theming instead of custom CSS variables.
- **Theme system overhauled.** `themes.json` and `ThemeCatalog` CSS generation engine removed. Themes are now DaisyUI built-in theme IDs (dark, light, cupcake, dracula, nord, etc.) — 32 themes available via sidebar selector or settings.
- **Desktop theming decoupled.** Desktop app uses a separate `DesktopTheme` enum with FlatLaf themes, independent from the web theme catalog.
- `buildUponDefaultConfig` re-enabled for detekt — codebases extending this project may see new violations.

### Added
- DaisyUI v5.5.19 integrated via Tailwind v4 CSS-first configuration (`@extension "daisyui"`)
- Sidebar layout rewritten with DaisyUI `drawer lg:drawer-open`
- Topbar layout rewritten with DaisyUI `navbar`
- All 60+ JTE templates migrated to DaisyUI semantic classes (`card`, `badge`, `btn`, `table`, etc.)
- Auth pages (sign-in, register, recover, reset password, change password) redesigned with centered viewport, proper form spacing, DaisyUI tabs and inputs
- Profile page uses DaisyUI `dialog` for delete confirmation
- Developer integration manual at `docs/MANUAL.md`

### Changed
- `tailwind.config.js`, `autoprefixer`, `postcss` removed — Tailwind v4 CSS-first config in `input.css` (14 lines)
- `ShellView` simplified: `themeId` renamed to `themeName`, removed `themeCss`, `isDarkMode`, `darkModeToggleUrl`, `toggleThemeLabel`
- `WebContext` uses `ThemeCatalog.isValidTheme()` and `allThemes` property
- `platform.js`: removed `toggleMobileMenu()`/`closeMobileMenu()`, toasts use `alert-error`/`alert-success`
- `SyncWindow` (1541 lines) split into `SyncDialogs`, `SyncProfilePanel`, `SyncViews` to pass detekt `LargeClass` rule
- All inline `style="..."` attributes in JTE templates replaced with DaisyUI/Tailwind utility classes
- Stale CSS class names (`button-link`, `message-meta`, `topbar-label`, `pagination-info`) removed

### Fixed
- `buildUponDefaultConfig` restored to `true` in `pom.xml` (was `false`)
- Missing `assertNotNull` import in `SwingAppE2ETest`
- `ImplicitDefaultLocale` in `SwingSyncApp` `Color.toHtml()`
- Unused `handle` parameter in `JdbiMessageRepository.buildFilterClause()`
- Layout spacing across all pages: proper `w-full` inputs, `gap-4` form spacing, consistent label styling

---

## [1.5.0] – 2026-05-04

### Breaking
- **Removed H2 database support.** PostgreSQL is now the sole database engine. H2 file-based and in-memory databases are no longer supported. All tests and production defaults use PostgreSQL exclusively.
- Production default `jdbcUrl` changed from `jdbc:h2:file:...` to `jdbc:postgresql://localhost:5432/outerstellar` with credentials `outerstellar`/`outerstellar`. Existing deployments must set `JDBCURL`, `JDBCUSER`, and `JDBCPASSWORD` environment variables or use `APP_PROFILE=postgres`.
- jOOQ code generation (`-Pjooq-codegen`) now requires a running PostgreSQL instance instead of a file-based H2 database. Use `docker/podman-compose.yml` to start PostgreSQL locally.
- Test base classes renamed: `H2JooqTest` → `JooqTest`, `H2JdbiTest` → `JdbiTest`, `H2WebTest` → `WebTest`. Downstream code extending these must update imports.

### Changed
- `SQLDialect.H2` removed from `PersistenceModule` — always uses `SQLDialect.POSTGRES`
- All test base classes use Testcontainers `PostgreSQLContainer` for ephemeral test databases
- `flyway-database-postgresql` added to persistence modules (required by Flyway 12+)
- `AppConfig` extracted from `webModule` into dedicated `configModule` — tests provide their own config
- Desktop GUI tests skip gracefully in headless mode via `Assumptions.assumeFalse`
- Desktop test container entrypoint builds upstream separately (skip tests) and disables Ryuk for Docker-in-Docker

### Security
- Generated admin password no longer logged in plaintext at startup
- `SESSIONCOOKIESECURE=false` override removed from `docker-compose.yml` (Dockerfile defaults to `true`)
- `/health` endpoint no longer exposes user count or raw database error messages

### Fixed
- `podman-compose.yml` PostgreSQL volume mount corrected to `/var/lib/postgresql/data`
- `serverBaseUrl` derived from `AppConfig.port` instead of hardcoded `localhost:8080`
- Docker E2E CI workflow now provisions PostgreSQL service container for the app

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
- Bumped `SpotBugs Maven Extension`, `byte-buddy`, `outerstellar-i18n-validator-maven-plugin`

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
- `ExtensionContext.currentUser(request)` — one-liner to get the authenticated user without manual LensFailure handling
- `ExtensionContext.buildPage(request, title, section, data)` — wraps extension ViewModels in the platform shell with CSRF token, theme, and nav
- `ExtensionContext.forTesting(renderer, securityService, userRepository)` — factory with sensible defaults for extension testing

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
- Framework dependency bumped to 1.0.7 (ExtensionManager SLF4J, ThemeService performance, ParameterInjector regex, I18n hot-reload fix)

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
- `baselineOnMigrate(true)` added to `migrateExtension()` for Flyway baseline support

### Changed
- Dependencies bumped: http4k 6.37.0.0, JDBI 3.52.0, ByteBuddy 1.18.7

---

## [1.2.5] – 2026-03-22

### Fixed
- CodeQL Maven auth using `s4u/maven-settings-action`

---

## [1.2.4] – 2026-03-22

### Added
- Extension filter hook — `PlatformExtension.filters` for custom request/response filters
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
- Upgraded ktfmt, Jackson, and Maven extensions
- Fixed Playwright test compatibility after dependency updates
- Applied ktfmt formatting to `SyncModels.kt`

---

## [1.0.0] – 2026-03-16

Initial release.
