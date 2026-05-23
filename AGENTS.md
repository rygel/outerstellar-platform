# AGENTS Guide

This file defines repository-specific guardrails for coding agents and contributors.

## Scope

- Applies to the entire repository.
- If a subdirectory later adds its own `AGENTS.md`, that file should refine behavior for that subtree.

## Architecture

### Synchronous Only — No Async

This project uses **synchronous blocking I/O** with a planned migration to **Java virtual threads (Project Loom)**. This is a deliberate architectural decision.

- NEVER introduce coroutines, async/await, reactive frameworks, or callback-based APIs
- NEVER suggest "migrating to coroutines" — synchronous code is the correct architecture
- Background work uses `Thread({...}, "name").also { it.isDaemon = true }.start()` — this is correct
- When virtual threads are adopted, zero code changes are needed — the JVM handles everything

### Module Structure

```
platform-core              Domain models, services, configuration (AppConfig, RuntimeConfig)
platform-security          Auth, permissions, User/UserRepository, OAuth, API keys, JWT
platform-persistence-jdbi  JDBI repositories + Flyway migrations
platform-test-infrastructure SharedPostgres container, TestDatabase, testing utilities
platform-sync-client       Sync DTOs, DesktopSyncEngine (sync client logic)
platform-web               http4k web server, JTE templates, HTMX frontend
platform-desktop           Swing desktop client with two-way sync
platform-seed              Database seeding utility
platform-desktop-javafx    JavaFX desktop module (scaffolded but not implemented)
```

### Key Design Patterns

- **Repository pattern**: Interfaces in `platform-core` (e.g. `MessageRepository`, `ContactRepository`), implementations in `platform-persistence-jdbi` (JdbiXxxRepository).
- **Dependency injection**: Koin. Objects use `by inject()` for lazy resolution or `get()` for eager. MainComponent uses lazy delegates.
- **http4k routes**: Contract-based routing via `bindContract`. Filters chain via `.then()`. All routes assembled in `App.kt`.
- **JTE templates**: Precompiled in production (`JTE_PRODUCTION=true`), source-compiled in dev. Templates in `src/main/jte/`.
- **Flyway migrations**: Source of truth for schema.
- **AppConfig/RuntimeConfig**: Configuration from YAML + env vars. `AppConfig.fromEnvironment()` loads `application-{PROFILE}.yaml` then `application.yaml`. All fields support env var override.
- **WebPageFactory → domain factories**: AuthPageFactory, ErrorPageFactory, SidebarFactory, ContactsPageFactory, HomePageFactory, InfraPageFactory, SettingsPageFactory, SearchPageFactory, DevDashboardPageFactory, AdminPageFactory. All delegate from the original WebPageFactory.

## Build and run

- Run Maven commands from the repository root unless a task explicitly requires module-local execution.
- Use existing scripts for local workflows:
  - `start-web.ps1`
  - `stop-web.ps1`
  - `start-swing.ps1`

### Test execution

```powershell
# Full build excluding desktop modules (PowerShell)
# NOTE: `-pl,!platform-desktop,!platform-desktop-javafx` does NOT work via PowerShell + cmd.exe
# Use explicit module list instead:
mvn clean verify -T4 -pl platform-core,platform-security,platform-test-infrastructure,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seed

# Run a specific test in a specific module (ALWAYS use -am to rebuild upstream modules)
mvn -pl platform-web -am test -Dtest=HealthCheckIntegrationTest

# Skip CSS build when tests hang on npm
mvn -pl platform-web -am test -Dexec.skip=true

# Skipping quality checks for fast iteration
mvn -pl platform-web compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true
```

### Stale classpath prevention

**Always use `-am` (also-make) when running `-pl` (project list).** Without `-am`, Maven resolves
upstream dependencies from `~/.m2/repository/` which may contain stale SNAPSHOT JARs, causing
phantom test failures that don't exist in the source code.

```powershell
# WRONG — resolves upstream from ~/.m2/ (may be stale)
mvn -pl platform-web test

# CORRECT — rebuilds upstream modules first, uses fresh reactor output
mvn -pl platform-web -am test

# Full reactor build (always safe, no -am needed)
mvn clean verify -T4 -pl platform-core,platform-security,platform-test-infrastructure,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seed
```

### Desktop Tests in Podman

**Desktop/Swing tests must NEVER run directly on the host machine.** They capture mouse and keyboard. Always use Podman:

```powershell
# Build the image (slow, only when dependencies change)
podman build -t outerstellar-test-desktop -f docker/Dockerfile.test-desktop .

# Run tests (mount Maven cache + Docker socket for Testcontainers)
# NOTE: On Windows, OMIT the :Z flag (SELinux-specific)
podman run --rm --network host `
  -v "${env:USERPROFILE}\.m2\repository:/root/.m2/repository" `
  -v "${env:USERPROFILE}\.m2\settings.xml:/root/.m2/settings.xml" `
  -v "/var/run/docker.sock:/var/run/docker.sock" `
  outerstellar-test-desktop
```

### Common Issue: Stale Bytecode

After any `mvn clean install -DskipTests` that changes compiled production code in a dependency module, the dependent module's test classpath may hold stale classes. Always do `mvn clean test` when cross-module changes are involved.

### Common Issue: UserRole Duplicate

There are TWO `UserRole` enums — one in `platform-core` (model package) and one in `platform-security` (security package). Module dependencies cause `platform-web` and `platform-desktop` to sometimes resolve the wrong one during incremental compilation. Clean build (`mvn clean compile`) resolves this.

### Modules that depend on external GitHub Packages

`outerstellar-framework` and `fragments-seo-core` are published to GitHub Packages (`maven.pkg.github.com/rygel/outerstellar-framework`). CI resolves them via `GH_PACKAGES_TOKEN` secret. On local machines, `~/.m2/settings.xml` must have a `github-rygel` server entry with a GitHub PAT. If a dependency can't resolve, check that the release is published (not SNAPSHOT).

## Maven profile conventions

- Coverage:
  - `-Pcoverage` for coverage-oriented verification runs.
- Test execution:
  - `-Ptests-headless` for desktop/Swing CI-safe runs.
  - `-Ptests-headful` for local visual verification runs.
- Runtime:
  - `-Pruntime-dev` for local launch commands.
  - `-Pruntime-prod` for production-like launch commands.
- Database migration:
  - `-Pmigrate` runs standalone migration via `MigratorKt`.

## Database schema rules

- Flyway migrations are the schema source of truth.
- Naming convention: `V{version}__{description}.sql` (Flyway default).
- Migration changes should be committed with corresponding code changes.

## Swing theming and i18n rules

- All Swing windows/dialogs must follow FlatLaf theming and shared UI defaults.
- Avoid hardcoded user-facing strings in Swing code; use i18n keys.
- Runtime language/theme switching must update already mounted UI, not just newly opened dialogs.
- Any Swing theming change should include regression test updates:
  - headless-safe `ThemeManager` unit tests
  - GUI E2E coverage for settings-driven theme switching where applicable

## Configuration Reference

All configuration is read from `application.yaml` (or `application-{profile}.yaml`) and overridden by environment variables.

### Core Config (AppConfig fields)

| YAML Key | Env Var | Default | Description |
|---|---|---|---|
| `version` | `APP_VERSION` | `dev` | Application version shown in footer |
| `port` | `PORT` | 8080 | HTTP server port |
| `jdbcUrl` | `JDBC_URL` | `jdbc:postgresql://localhost:5432/outerstellar` | Database URL |
| `jdbcUser` | `JDBC_USER` | `outerstellar` | Database user |
| `jdbcPassword` | `JDBC_PASSWORD` | `outerstellar` | Database password |
| `profile` | `APP_PROFILE` | `default` | Active config profile |
| `devMode` | `DEVMODE` | false | Dev auto-login |
| `sessionTimeoutMinutes` | `SESSIONTIMEOUTMINUTES` | 30 | Session timeout |
| `registrationEnabled` | `REGISTRATION_ENABLED` | true | Enable or disable public user registration |
| `sessionAbsoluteTimeoutMinutes` | `SESSION_ABSOLUTE_TIMEOUT_MINUTES` | 1440 | Absolute max session lifetime in minutes (cannot be extended by sliding window) |
| `cspPolicy` | `CSP_POLICY` | (default policy) | Content-Security-Policy |

### Runtime Config (runtime section)

| YAML Key | Env Var | Default |
|---|---|---|
| `runtime.hikariMaximumPoolSize` | `HIKARI_MAX_POOL_SIZE` | 20 |
| `runtime.hikariMinimumIdle` | `HIKARI_MIN_IDLE` | 2 |
| `runtime.flywayEnabled` | `FLYWAY_ENABLED` | true |
| `runtime.jtePreloadEnabled` | `JTE_PRELOAD_ENABLED` | false |
| `runtime.cacheMessageMaxSize` | `CACHE_MESSAGE_MAX_SIZE` | 1000 |
| `runtime.rateLimitIpCapacity` | `RATE_LIMIT_IP_CAPACITY` | 10 |
| `runtime.rateLimitAccountCapacity` | `RATE_LIMIT_ACCOUNT_CAPACITY` | 20 |

Explicit profiles: `APP_PROFILE=small` (4 connections, small caches), `APP_PROFILE=large` (50 connections, preload enabled).

## Key Code Locations

| Pattern | Location |
|---------|----------|
| Route definitions | `App.kt` (`buildBaseApp`, `buildUiRoutes`, `buildApiRoutes`) |
| Filters | `Filters.kt` in `platform-web` |
| Web context (per-request state) | `WebContext.kt` |
| Shell/page rendering | `WebPageFactory.kt` (delegates to domain factories) |
| JTE templates | `src/main/jte/.../layouts/`, `.../pages/`, `.../components/` |
| CSS | `input.css` (Tailwind v4 + DaisyUI v5), generates `site.css` |
| Desktop main | `SwingSyncApp.kt` (SyncWindow, SyncWindowMenu, SyncWindowNav) |
| Desktop dialogs | `SyncDialogs.kt` (630 lines) |
| Sync engine | `DesktopSyncEngine.kt` in `platform-sync-client` |
| Migrations | `platform-persistence-jdbi/src/main/resources/db/migration/` |
| Test infrastructure | `platform-test-infrastructure` (`SharedPostgres`, `TestDatabase`) |
| Web test base | `platform-web/src/test/kotlin/.../web/WebTest.kt` |
| Jdbi test base | `platform-persistence-jdbi/src/test/kotlin/.../persistence/JdbiTest.kt` |

## Testing expectations

Full test architecture and patterns: **[docs/testing.md](docs/testing.md)**.

- Prefer module-focused validation first, then broader reactor validation when changes cross modules.
- Minimum for persistence/schema changes:
  - `mvn -pl platform-persistence-jdbi test`
- Minimum for Swing UI/theming changes:
  - `mvn -pl platform-desktop -Ptests-headless test`
- Minimum for web changes:
  - `mvn -pl platform-web test -Dexec.skip=true`
- **Full reactor must exclude desktop modules** when running locally:
  ```bash
  mvn clean verify -T4 -pl platform-core,platform-security,platform-test-infrastructure,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seed
  ```
- Desktop tests via Podman (see Podman section above).
- Playwright E2E tests are tagged `@Tag("e2e")` and run in CI via Docker E2E workflow.
- After any `mvn clean install -DskipTests` that changes compiled production code in a dependency module, the dependent module's test classpath may hold stale classes. Always do `mvn clean test` or `mvn clean install -DskipTests` before running tests in downstream modules.

### http4k testing conventions

- All HTTP assertions use hamkrest matchers (`HttpMatchers.kt`). New tests should use `assertThat(response, hasStatus(...))` instead of raw `assertEquals`.
- Approval tests live in `src/test/kotlin/.../approval/`. Golden files (`.approved`) in `src/test/resources/`.
- WebDriver and Chaos tests are proof-of-concept — expand as needed.

### Shared PostgreSQL container conventions

- `platform-test-infrastructure` provides `SharedPostgres` (singleton reused container) and `TestDatabase` (per-class DB handle).
- `WebTest` and `JdbiTest` both use `@TestInstance(PER_CLASS)` — each test class gets its own database, dropped in `@AfterAll`.
- `@AfterEach` row deletion cleans between methods within a class. No manual `cleanup()` calls needed.
- platform-web Surefire config: `parallel=classes`, `threadCount=4`. Classes run concurrently (each has its own DB), methods run sequentially.
- Never create your own PostgreSQL container — extend `WebTest` or `JdbiTest`.
- Never use `testJdbi.open()` — it leaks connections. Use `testJdbi.withHandle { }` or `testJdbi.useHandle<Exception> { }`.

## Project Status (as of May 2026)

### Core features
- Web app with auth, messages, contacts, notifications, search, admin, settings
- Desktop Swing client with two-way sync
- Plugin system (PlatformPlugin interface)
- SEO: Open Graph, Twitter Card, JSON-LD, sitemap, robots.txt, canonical, hreflang
- Accessibility: aria-hidden, aria-live, skip-to-content

### Recently completed (PR #239-261)
- AppContext refactoring, Security hardening (CORS, CSP, session, audit, SSRF)
- Per-account rate limiting
- WebPageFactory split into 10 domain factories
- fragments-seo-core integration
- Responsive layout (auto-close nav, touch CSS)
- App.kt refactoring (OptionalServices extraction)
- SyncWindowMenu + SyncWindowNav extraction
- 18 performance/runtime items (configurable knobs, startup timing, fetch optimization, indexes, adaptive outbox)

### Open items
- Generic `catch (Exception)` boilerplate in DesktopSyncEngine
- DesktopSyncEngine interface for testability
- TOTP two-factor authentication
- Unified Settings page with tabs
- Search SPI (SearchProvider interface)
- Export SPI (CSV/JSON export)
- Jazzer fuzz tests

## http4k contract routing rules

These are hard-won rules from actual failures. Violating them produces compile errors or wrong runtime behavior.

### Path parameters with the `/` operator

`Path.string().of("id")` creates a path lens. Chaining with `/ "literal"` creates a **multi-segment** route spec. The handler lambda arity MUST match:

```kotlin
// Single path param — 1-arg handler
"/api/v1/polls" / syncIdPath meta { ... } bindContract GET to
    { syncId -> { req -> Response(...) } }

// Path param + literal suffix — 2-arg handler (second arg is the literal, always ignore with _)
"/api/v1/polls" / syncIdPath / "vote" meta { ... } bindContract POST to
    { syncId, _ -> { req -> Response(...) } }
```

Getting the arity wrong is a **compile error**, not a runtime error. The compiler catches it — but only if you compile.

### Reference implementation

`HomeRoutes.kt` in `platform-web` is the canonical working example of path-param contract routes. **Always read it before writing new contract routes.** Do not guess at the DSL syntax.

### ServerRoutes interface

Route classes consumed by `App.kt` must implement `ServerRoutes` and return `List<ContractRoute>` from `routes`. Do not remove this interface — `App.kt` uses `+=` to add them. If a class cannot implement `ServerRoutes` (e.g. returns `Pair<Binder, ContractRoute>`), the return type is wrong and the route DSL is being used incorrectly.

## Agent discipline: read before write, verify after write

These rules exist because an agent repeatedly caused multi-hour debugging sessions by skipping them.

### Read existing patterns before writing

Before writing or rewriting code in an unfamiliar DSL or framework pattern:
1. **Find a working reference** in the same codebase (e.g. `HomeRoutes.kt` for http4k contract routes)
2. **Read it fully** — understand every import, operator, lambda arity, and return type
3. **Copy the pattern mechanically** — do not improvise or assume syntax

Guessing at framework DSLs and "fixing" compile errors iteratively is wasteful. The reference implementation already exists.

### Compile after every file change

After editing a file, compile immediately:
```powershell
mvn -pl <module> compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

Do not batch 5 file rewrites and then compile. Errors compound and become harder to diagnose.

### Run tests before declaring work done

After all files compile, run the relevant tests before pushing:
```powershell
mvn -pl <module> test -Dtest=<TestClass>
```

Do not push code that has not been compiled and tested locally. CI round-trips waste time.

### Do not chain guesses

If a fix introduces a new error, **stop and read the reference implementation** instead of guessing again. Each wrong guess adds cognitive load and makes the next guess worse. The pattern is:

```
Write → Compile → FAIL → Read reference → Fix → Compile → PASS
```

Not:

```
Write → Compile → FAIL → Guess fix → Compile → FAIL → Guess fix → Compile → FAIL → ...
```

## Commit gate: all local tests must pass

Before every commit, the following MUST be true:

1. **All non-desktop tests pass locally.** Run the full reactor build:
   ```powershell
   mvn clean verify -T4 -pl platform-core,platform-security,platform-test-infrastructure,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seed
   ```
   If any test fails, fix it before committing. Do not commit failing tests.

2. **Desktop/UI tests must run in Podman containers.** Desktop/Swing tests must NEVER run directly on the host machine — they capture mouse and keyboard. Use:
   ```powershell
   podman build -t outerstellar-test-desktop -f docker/Dockerfile.test-desktop .
   podman run --rm --network host -v "${env:USERPROFILE}\.m2\repository:/root/.m2/repository" -v "${env:USERPROFILE}\.m2\settings.xml:/root/.m2/settings.xml" -v "/var/run/docker.sock:/var/run/docker.sock" outerstellar-test-desktop
   ```

**Do not commit if either of these conditions is not met.** Pushing code that fails locally wastes CI time and is unacceptable.

## Testing discipline

Full test architecture, patterns, and conventions are documented in **[docs/testing.md](docs/testing.md)**. Read it before writing any new tests.

Key rules:
- **Full end-to-end tests only. Zero smoke tests.** Every test must assert meaningful behavior: correct data, correct state transitions, correct error handling. Checking only an HTTP status code is a smoke test — write a real test instead.
- Integration tests exercise the full stack (filters → routes → services → persistence → database). They are the standard, not the exception.
- Never create your own PostgreSQL container — extend `WebTest` or `JdbiTest`.
- Never use `TemplateEngine.create(DirectoryCodeResolver(...))` in tests — always use precompiled templates.
- Never run desktop tests on the host — use Podman containers.

## Safety and repository hygiene

- Do not commit transient artifacts from `target/`.
- Keep generated sources deterministic and reproducible.
- Do not suppress warnings by default; prefer fixing root causes.
- Never push directly to `develop` or `main`. Always create feature branches and PRs.
- Run full reactor `mvn verify` locally before pushing to CI. CI round-trips waste time.
- Desktop/Swing tests must NEVER run directly on the host machine. Always use the Podman container.
