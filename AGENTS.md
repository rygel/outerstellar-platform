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
platform-core              Domain models, services, configuration (AppConfig, RuntimeConfig), composition model types
platform-plugin-api        Hosted-app SPI, plugin-facing DTOs, contribution contexts, facades
platform-security          Auth, permissions, User/UserRepository, OAuth, API keys, JWT
platform-persistence-jdbi  JDBI repositories + Flyway migrations
platform-test-infrastructure SharedPostgres container, TestDatabase, testing utilities
platform-sync-client       Sync DTOs, DesktopSyncEngine (sync client logic)
platform-web               http4k web server, JTE templates, HTMX frontend, route registry
platform-desktop           Swing desktop client with two-way sync
platform-seed              Database seeding utility
platform-desktop-javafx    JavaFX desktop module (scaffolded, not production-ready)
platform-jte-extensions    Custom JTE code generation (JteClassRegistry)
```

### Key Design Patterns

- **Repository pattern**: Interfaces in `platform-core` (e.g. `MessageRepository`, `ContactRepository`), implementations in `platform-persistence-jdbi` (JdbiXxxRepository).
- **Dependency injection**: Explicit constructor wiring. No runtime DI framework.
- **http4k routes**: Contract-based routing via `bindContract`. Filters chain via `.then()`. Routes assembled through `RouteRegistry` in `App.kt` with mode-based conditional registration.
- **JTE templates**: Precompiled in production (`JTE_PRODUCTION=true`), source-compiled in dev. Templates in `src/main/jte/`.
- **Flyway migrations**: Source of truth for schema.
- **AppConfig/RuntimeConfig**: Configuration from YAML + env vars. `AppConfig.fromEnvironment()` loads `application-{PROFILE}.yaml` then `application.yaml`. All fields support env var override. `platformMode` field controls composition mode via `PLATFORM_MODE` env var.
- **Domain page factories**: AuthPageFactory, ErrorPageFactory, SidebarFactory, ContactsPageFactory, HomePageFactory, InfraPageFactory, SettingsPageFactory, SearchPageFactory, DevDashboardPageFactory, AdminPageFactory own page-specific rendering directly.
- **Plugin composition**: `PlatformPlugin` interface with `mode` (PlatformMode), `includePlatformPages()` (Set of PlatformPageSets), `routeRegistrations()` (List of PluginRouteRegistration), `layoutTemplate()` (JTE template override), `filters()`, `bannerProviders()`. Route ownership tracked via `RouteRegistry` with startup conflict detection.

## Build and run

- Run Maven commands from the repository root unless a task explicitly requires module-local execution.
- Use existing scripts for local workflows:
  - `start-web.ps1`
  - `stop-web.ps1`
  - `start-swing.ps1`

### Container runtime (Podman)

**Podman 5.8.2** is available on this machine with rootful mode enabled.

**CRITICAL: Use `podman` exclusively. NEVER use the `docker` CLI.** The Docker CLI is unreliable on this machine — commands hang indefinitely and leave stale BuildKit containers that break Testcontainers. Always use `podman` commands directly.

### Podman pipe configuration

The Podman machine exposes two named pipes on Windows:

| Pipe | Status | Use |
|---|---|---|
| `\\.\pipe\podman-machine-default` | **Working** | Podman-native API — always use this |
| `\\.\pipe\docker_engine` | **Broken** | Docker compatibility pipe — unreliable, do NOT use |

Testcontainers connects via `DOCKER_HOST` set in Surefire:
```xml
<environmentVariables>
    <DOCKER_HOST>npipe:////./pipe/podman-machine-default</DOCKER_HOST>
</environmentVariables>
```

The `~/.testcontainers.properties` must NOT pin `docker.client.strategy` — let Testcontainers auto-discover via `DOCKER_HOST`.

### Parallel test execution

Multiple agents/projects can run Testcontainers tests simultaneously against the same Podman pipe. The Podman-native pipe handles concurrent connections. Each Testcontainers instance creates unique containers with random host ports — no collisions. `SharedPostgres` uses `withReuse(true)` and `withLabel()` to share containers across test classes within a project while isolating from other projects.

```powershell
# Check Podman status
podman machine list

# Start if stopped (must be rootful for Docker API forwarding)
podman machine start

# Verify it's working
podman ps

# Build Docker images — ALWAYS use podman, never docker
podman build -t my-image -f Dockerfile .

# Run containers — ALWAYS use podman, never docker
podman run -d --name my-container my-image
```

**Before running integration tests**, ensure the Podman machine is running (`podman machine start`). All Testcontainers-based integration tests (WebTest, JdbiTest) require it. If tests fail with `NoClassDefFoundError` on test classes, the Podman machine is likely stopped.

### Test timeout guardrails

The full non-desktop reactor build (6 modules) must complete in **under 20 minutes**. If it exceeds 20 minutes, something is wrong — investigate immediately.

Existing timeouts enforced by Maven Surefire:

| Timeout | Value | Scope |
|---|---|---|
| `forkedProcessTimeoutInSeconds` | 300 (5 min) | Kills an entire Surefire fork if it hangs |
| `junit.jupiter.execution.timeout.default` | 120s (2 min) | Fails a single test method if it takes too long |

These prevent individual hangs but do NOT limit total build time. For total-build enforcement:
- **CI workflows** set `timeout-minutes: 20` on the test job.
- **Locally**, use `scripts/test.ps1` — wraps `mvn clean verify` with a hard 20-minute kill switch and warns if the build exceeds 75% of the limit.

```powershell
# Full build with 20-minute timeout (default)
pwsh scripts/test.ps1

# Quick iteration — single module, skip quality checks
pwsh scripts/test.ps1 -Modules platform-web -SkipQuality

# Custom timeout
pwsh scripts/test.ps1 -TimeoutMinutes 10 -Modules platform-core
```

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
# Preferred wrapper: builds docker/Dockerfile.build --target desktop-test,
# runs Swing tests under Xvfb, and copies reports back.
pwsh scripts/test-desktop.ps1

# Bash equivalent:
bash docker/run-desktop-tests.sh

# Manual equivalent, if the wrapper is unavailable:
podman build --target desktop-test -t outerstellar-test-desktop -f docker/Dockerfile.build .
podman run --rm --network host `
  -v "${env:USERPROFILE}\.m2\settings.xml:/root/.m2/settings.xml:ro" `
  -e "DOCKER_HOST=unix:///var/run/docker.sock" `
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

## Release workflow safeguards

- `Release and Publish` is manual-only and must be dispatched from `main` with two matching inputs: `release_version` and `confirm_release_version`.
- `Publish to Maven Central` is also manual-only and requires the same exact version confirmation from `main`.
- Both workflows fail unless the requested version exactly matches the root `pom.xml` version, is not a `-SNAPSHOT`, has a matching `CHANGELOG.md` heading, and already passed CI on that exact `main` commit.
- Maven Central additionally requires the matching GitHub release tag to already exist, and the GitHub release is only created after the GitHub Packages publish succeeds.

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
| `platformMode` | `PLATFORM_MODE` | `FullPlatformApp` | Composition mode (`FullPlatformApp`, `PluginHostedApp`, `HeadlessKernel`) |
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
| Route assembly | `App.kt` (mode-based `RouteRegistry` assembly) |
| Composition model types | `platform-core/.../composition/` (`PlatformMode`, `RouteRegistry`, `RegisteredRoute`, `RouteOwner`, `RouteGroup`) |
| Hosted-app SPI | `platform-plugin-api/` (`HostedAppManifest`, facades, DTOs) |
| Plugin interface | `platform-web/.../PlatformPlugin.kt` |
| Platform page sets | `platform-web/.../composition/PlatformPageSets.kt` |
| Theme interface | `platform-web/.../theme/PlatformTheme.kt`, `DaisyUITheme.kt` |
| Filters | `Filters.kt` in `platform-web` |
| Per-request state | `RequestContext.kt` (user, lang, theme, layout, CSRF) |
| Layout data builder | `ShellRenderer.kt` (builds `ShellView` from request context) |
| Page rendering | `HomePageFactory.kt`, `ContactsPageFactory.kt`, `AdminPageFactory.kt`, `SettingsPageFactory.kt`, `ErrorPageFactory.kt`, `SearchPageFactory.kt`, `AuthPageFactory.kt`, `InfraPageFactory.kt`, `DevDashboardPageFactory.kt` |
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
   pwsh scripts/test-desktop.ps1
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

## Branch hygiene

### Always start from latest upstream

Before creating any new feature branch, ALWAYS fetch from origin and branch from the latest `origin/develop`:

```powershell
git fetch origin
git checkout -b feat/my-feature origin/develop
```

NEVER reuse a branch whose PR was already merged. A merged branch contains stale code that conflicts with develop. Starting fresh avoids cherry-pick conflicts and regression bugs.

### One PR per branch, one branch per PR

After a PR is merged:
1. Delete the local branch
2. Create a new branch from `origin/develop` for the next piece of work
3. Do NOT cherry-pick commits from the old branch onto the new one — redo the work on a clean base

Cherry-picking across merged PRs creates conflicts because the target file state changed. Redoing the work on the new base is faster than resolving cascading conflicts.
