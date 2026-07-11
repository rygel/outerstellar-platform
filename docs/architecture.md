# Outerstellar Platform Architecture

## Purpose

This platform is meant to provide a working vertical slice for:

- Kotlin on JDK 21
- http4k for the web server
- Flyway for schema migration
- JDBI for database access
- PostgreSQL for persistence
- JTE with Kotlin templates (`.kte`) for server-rendered HTML
- HTMX for progressive enhancement and fragment swapping
- A Swing desktop demo with local persistence and two-way sync
- Outerstellar i18n and theme integration

The goal is to start from a runnable platform, not just a dependency list.

## High-level shape

The project is organized as a **multi-module Maven project**:

- `platform-core` - shared domain models, services, and configuration
- `platform-persistence-jdbi` - JDBI-backed repository implementation and Flyway migrations
- `platform-sync-client` - shared sync DTOs and client sync service
- `platform-web` - http4k web server, JTE templates, and HTMX interactions
- `platform-desktop` - Swing desktop client, theme manager, and UI tests
- `platform-security` - security-related utilities and configuration

### Why multiple modules?

This structure provides better separation of concerns, allows for independent testing, and keeps dependencies scoped to where they are actually needed (e.g., Swing-specific libraries are only in `platform-desktop`).

## Runtime architecture

### Web application

- `Main.kt` creates the data source, runs Flyway, builds the JDBI repository, creates the JTE renderer, and starts Jetty via http4k.
- `App.kt` composes the routes for:
  - home page
  - auth example pages
  - error example pages
  - footer status fragment
  - sync API
  - health check

The web UI is **server-rendered first**, with HTMX used for partial replacement instead of a SPA.

### Desktop application

- `SwingSyncApp.kt` runs a local Swing client.
- It uses its own PostgreSQL database and the same repository style.
- `SyncService.kt` pushes local dirty changes and pulls server changes through `/api/v1/sync`.
- `ThemeManager.kt` applies FlatLaf plus an Outerstellar-inspired palette.

This gives a concrete offline-capable sync example without introducing a second stack.

## Persistence and schema

### PostgreSQL

PostgreSQL is the sole database engine for the platform:

- production-grade RDBMS
- used for local development via Podman/Docker
- works well with Flyway and JDBI
- keeps the platform consistent across environments

### Flyway

Flyway is the source of truth for schema evolution.

Current migrations:

- `V1__initial_schema.sql`
- `V2__user_profile_enhancements.sql`
- `V3__sessions_table.sql`
- `V4__user_preferences.sql`

### Persistence module: JDBI

The platform uses JDBI for database access.

| Module | Artifact | Database access | Koin module |
|--------|----------|----------------|-------------|
| JDBI | `platform-persistence-jdbi` | JDBI lightweight SQL | `persistenceModule` (from `io.github.rygel.outerstellar.platform.di`) |

The module:

- Implements repository interfaces defined in `platform-core`
- Provides Flyway migrations at `classpath:db/migration`
- Provides the `persistenceModule` Koin module so the rest of the app is agnostic
- Provides `DatabaseInfra` functions (`migrate`, `migrateExtension`, `createDataSource`)

#### Adding new repositories

When adding a new repository:

1. Define the interface in `platform-core` (e.g. `FooRepository`)
2. Implement it in `platform-persistence-jdbi`
3. Register the implementation in `PersistenceModule.kt`

### Extension migrations

The platform supports extension applications that have their own Flyway migrations without version conflicts. Extensions declare an optional `ExtensionMigrations` value, which provides:

- **`location`**: classpath location for the extension's SQL files (e.g. `classpath:db/migration/extension`).
- **`historyTable`**: separate Flyway history table name. Defaults to `"flyway_extension_history"`.
- **`migrationNames`**: optional explicit migration filenames for native-image classpath extraction.

The host runs two separate Flyway instances:

| Aspect | Host (platform) | Extension |
|--------|----------------|--------|
| Migration location | `classpath:db/migration` | Configurable via `ExtensionMigrations.location` |
| History table | `flyway_schema_history` | Configurable via `ExtensionMigrations.historyTable` |
| `baselineOnMigrate` | No | Yes |

Extensions implement `PlatformExtension` and the server passes `extension.migrations` into persistence startup. The persistence bootstrap runs those migrations after the host migrations in a separate Flyway instance. This means extensions can use **any** version numbers (including V1–V4) without conflicting with the platform's own migrations.

## Sync design

The current sync implementation is intentionally **message-focused** rather than a generic sync engine.

It demonstrates:

- local dirty tracking
- server pull by last-sync timestamp
- push of local dirty records
- simple conflict reporting
- shared persistence concepts across web and Swing

### Why this shape?

It proves the core architecture without prematurely building a full framework.

### Current limitation

Sync is currently:

- manual, not background/automatic
- centered on one aggregate (`messages`)
- simple in conflict handling

This is acceptable for a platform and gives a clean base for expansion.

## Rendering and web UI decisions

### JTE + KTE

The rendering engine is **JTE**, and the templates are **Kotlin templates** using `.kte`.

Why:

- fast server-side rendering
- Kotlin-friendly templates
- simple deployment model
- good fit with http4k

### HTMX usage

The web application uses HTMX for component-like fragment replacement:

- `/auth/components/forms/{mode}`
- `/auth/components/result`
- `/errors/components/help/{kind}`
- `/components/footer-status`

This keeps the application mostly server-rendered while still enabling richer interactions.

### http4k contract routing with path parameters

The project uses http4k's contract-based routing with `bindContract`. Path parameters use the `/` operator from `org.http4k.contract.div`. **This DSL has non-obvious arity rules that trip up anyone who hasn't read the working code:**

```kotlin
val syncIdPath = Path.string().of("syncId")

// Single path param — handler takes 1 arg
"/api/v1/polls" / syncIdPath meta { ... } bindContract GET to
    { syncId -> { req -> Response(...) } }

// Path param + literal suffix — handler takes 2 args (second is the suffix string, ignore with _)
"/api/v1/polls" / syncIdPath / "vote" meta { ... } bindContract POST to
    { syncId, _ -> { req -> Response(...) } }
```

The `/` operator between a path lens and a string literal creates a **multi-segment** route spec. The handler lambda arity **must** match the number of segments. Getting this wrong produces a compile error, not a runtime error — but only if you compile.

**Reference implementation:** `HomeRoutes.kt` in `platform-web` is the canonical working example. Always read it before writing new contract routes. Do not guess at the DSL syntax.

**Route classes** consumed by `App.kt` implement `ServerRoutes` and return `List<ContractRoute>`. `App.kt` uses `+=` to add them. Do not remove this interface.

### Kotlinx Serialization in http4k models

http4k uses `KotlinxSerialization` as its JSON body lens provider. Models used in request/response bodies must use `@Serializable` with explicit serializer annotations for non-standard types:

- **UUID fields**: Use `@Serializable(with = UuidSerializer::class)` — do NOT use `@Contextual` because no `SerializersModule` is configured in the http4k JSON instance.
- **Instant fields**: Use `@Serializable(with = InstantSerializer::class)` — same reason.

The serializers live in `platform-core/.../model/Serialization.kt` as `internal` objects. Using `@Contextual` silently fails at runtime with a "no serializer found" error.

### Shell layout

The web pages now share a shell with:

- sticky top bar
- sticky footer
- sidebar navigation
- theme switch links
- language switch links

This is meant to act as the default application shell for future pages.

## Internationalization and theming

### i18n

The platform uses the in-repository `outerstellar-i18n` module for localization.

Current bundles include:

- `web-messages.properties`
- `web-messages_fr.properties`
- `swing-messages.properties`
- `swing-messages_fr.properties`

### Theming

The web UI uses the platform-owned `ThemeCatalog` of DaisyUI theme IDs, while Swing desktop uses `DesktopTheme` + FlatLaf via `ThemeManager`.

Current web theme choices come from `ThemeCatalog` and include:

- dark
- light
- cupcake
- dracula
- nord
- sunset
- ...plus the rest of the 32 built-in DaisyUI themes

Current Swing theme choices come from `DesktopTheme`:

- FlatLaf dark
- FlatLaf light
- FlatLaf Darcula
- FlatLaf IntelliJ
- FlatLaf macOS Dark
- FlatLaf macOS Light

## Build and quality tooling

The build includes workbook-style quality tooling and project hygiene. **It is mandatory that these static analysis and formatting tools remain present, enabled, and strictly enforced to ensure a high-quality baseline for all downstream projects:**

- Enforcer (dependency convergence and version rules)
- JaCoCo (test coverage)
- SpotBugs (static analysis for bugs)
- Modernizer (detect legacy Java API usage)
- Spotless / Ktlint (code formatting)
- Checkstyle (coding standard adherence)
- PMD (source code analyzer)
- Detekt (Kotlin static analysis)
- Versions Maven Extension (dependency updates)
- OWASP Dependency Check (security vulnerabilities)

These are included and enforced so the platform begins with and maintains the exact same rigorous quality expectations as downstream projects.

### Build profile strategy

The root Maven build uses explicit profiles to separate concerns:

- `coverage` for coverage-oriented verification runs.
- `tests-headless` and `tests-headful` for Swing/UI test mode selection.
- `runtime-dev` and `runtime-prod` for launch-time defaults (faster runtime-oriented builds and runtime env flags).

## Operational scripts

### `run-dev.ps1`

Convenience script for development-time watcher + app startup.

### `start-web.ps1` / `stop-web.ps1`

Used for managed background startup and shutdown.

Important behavior:

- PID files are stored under `target/dev-runtime`
- the scripts manage both Maven launcher processes and the actual listening server PID
- shutdown walks process trees to avoid leaving child JVMs behind

### `start-swing.ps1`

Launches the Swing sync demo.

## Important findings and gotchas

These are worth preserving because they caused real issues during implementation.

### 1. Kotlin/http4k compatibility

**Finding:** current http4k metadata was not happy with the earlier Kotlin version that was first used.

**Decision:** move to Kotlin `2.3.10`.

**Why:** this resolved the compatibility issue cleanly.

### 2. JTE runtime dependency

**Finding:** the application could fail at runtime with:

`NoClassDefFoundError: gg/jte/html/HtmlTemplateOutput`

even though templates compiled.

**Decision:** keep `gg.jte:jte-runtime` as a direct dependency.

**Why:** the runtime engine needs those classes available when rendering generated templates.

### 3. JTE hot-reload classloader

**Finding:** when creating the JTE engine for hot reload, the generated classes did not automatically see the full application classpath.

**Decision:** explicitly pass the application classloader into `TemplateEngine.create(...)`.

**Why:** without that, generated template classes could not reliably resolve runtime classes under `exec:java`.

### 4. JTE output location

**Finding:** default hot-reload behavior could write generated template classes into an undesirable repo-root output location.

**Decision:** direct generated JTE classes to `target/jte-classes`.

**Why:** it keeps generated artifacts under `target/` and avoids polluting the project root.

### 5. Outerstellar theme resource loading

**Finding:** loading `themes.json` directly caused deserialization failure because it is not a single concrete theme payload for this usage.

**Decision:** load concrete files such as:

- `themes/dark.json`
- `themes/bootstrap.json`

**Why:** the theme service expects a specific theme structure in this path.

### 6. Web startup process handling

**Finding:** stopping only the Maven process did not always stop the actual Jetty listener.

**Decision:** capture the server PID from the listening port and stop the process tree.

**Why:** this avoids zombie server processes and makes `start-web.ps1` / `stop-web.ps1` reliable.

### 7. PID shutdown races

**Finding:** process-tree shutdown can race with already-exiting children.

**Decision:** use tolerant process termination in `stop-web.ps1`.

**Why:** shutdown should remain reliable even if a child disappears between discovery and termination.

### 8. Swing UI testing without stealing the display

**Finding:** Swing GUI tests need a display (`JFrame` throws `HeadlessException` when `java.awt.headless=true`). But tests must not pop up windows on the developer's machine.

**Decision:** Default is `java.awt.headless=true` (no windows). GUI tests run inside a Podman container with Xvfb via `pwsh scripts/test-desktop.ps1`, `bash docker/run-desktop-tests.sh`, or `mvn -Ptest-desktop verify`. CI also uses Xvfb. The desktop test image is built from `docker/Dockerfile.build --target desktop-test`.

**Why:** Both requirements are met — tests actually execute (not skipped), and no windows appear on the developer's screen. The Podman container provides an isolated virtual display.

## Testing strategy

### Test categories

The project uses three tiers of tests:

| Tier | Location | Runs in CI | Purpose |
|------|----------|-----------|---------|
| **Unit tests** | `*/src/test/` | Always | ViewModel logic, SecurityService, repository behavior |
| **Integration tests** | `platform-web/src/test/` | Always | http4k function tests — full app stack without a running server |
| **UI layout tests** | `platform-desktop/src/test/` | Headful only | Verify Swing dialogs and components are visually usable |

### http4k function-level integration tests

The web module uses http4k's in-memory testing pattern: the `app()` function returns an `HttpHandler` that is called directly without starting Jetty. This gives full-stack coverage (filters, routing, persistence, rendering) with fast execution.

Pattern:
```kotlin
class SomeIntegrationTest : WebTest() {
    private lateinit var app: HttpHandler

    @BeforeEach
    fun setup() {
        // Wire real repositories against the shared PostgreSQL test database
        app = app(messageService, contactService, ...).http!!
    }

    @Test
    fun `endpoint returns expected result`() {
        val response = app(Request(GET, "/some/path"))
        assertEquals(Status.OK, response.status)
    }
}
```

### UI usability testing requirements

**Every Swing dialog, form, and panel must have layout tests that verify the UI is actually usable — not just that components exist.** This is a binding requirement for all downstream projects that build on this platform.

Specifically, when adding or modifying Swing UI:

1. **Text fields must have a minimum preferred width of 150px.** A field that renders at 30px wide is technically present but unusable. Test with:
   ```kotlin
   assertTrue(field.preferredSize.width >= 150, "Field too narrow: ${field.preferredSize.width}px")
   ```

2. **Buttons must be at least 60x24px.** Smaller buttons are hard to click and text gets clipped. Test with:
   ```kotlin
   assertTrue(button.width >= 60, "Button too narrow")
   assertTrue(button.height >= 24, "Button too short")
   ```

3. **Dialogs must have minimum dimensions (300x200px).** A dialog that renders at 100x50 is broken even if all fields are present.

4. **Navigation elements must have sufficient click targets (60x60px minimum).** Sidebar buttons, nav items, and toolbar actions must be easily clickable.

5. **Tables must have a minimum usable width (200px).** A table squeezed into 50px shows nothing useful.

These tests use `assumeFalse(GraphicsEnvironment.isHeadless())` so they skip cleanly in CI. Run them locally with:
```
mvn test -pl platform-desktop -Ptests-headful
```

The existing `UiLayoutTest.kt` demonstrates the pattern. **When you add a new dialog or panel, add corresponding size assertions to this class.**

### Web UI usability testing requirements

**The same principle applies to the web UI.** When adding or modifying web pages and HTMX components, integration tests must verify that the rendered HTML is functionally complete — not just that the status code is 200.

Specifically:

1. **Forms must contain all expected fields.** Test that the HTML body includes the expected `<input>`, `<select>`, and `<button>` elements:
   ```kotlin
   val body = response.bodyString()
   assertTrue(body.contains("name=\"email\""), "Form should have email field")
   assertTrue(body.contains("name=\"password\""), "Form should have password field")
   assertTrue(body.contains("type=\"submit\""), "Form should have submit button")
   ```

2. **Tables must render column headers and data.** An admin page that shows an empty `<table>` with no headers is broken:
   ```kotlin
   assertTrue(body.contains("<th"), "Table should have column headers")
   assertTrue(body.contains("Username"), "Table should show Username column")
   ```

3. **Navigation links must be present for the user's role.** An admin should see admin links; a regular user should not:
   ```kotlin
   assertTrue(body.contains("/admin/users"), "Admin should see Users link")
   assertFalse(body.contains("/admin/users"), "Regular user should NOT see Users link")
   ```

4. **HTMX attributes must be correct.** Forms that use `hx-post` or `hx-get` must target the right URLs:
   ```kotlin
   assertTrue(body.contains("hx-post=\"/auth/components/change-password\""))
   ```

5. **Error and success states must render with appropriate tone classes.** The `panel-success` and `panel-danger` classes must appear in the right contexts.

6. **Pages must render within the layout shell.** Verify that `<html>`, the sidebar, and the topbar are present — a page that renders raw content without the layout is broken:
   ```kotlin
   assertTrue(body.contains("class=\"shell\""), "Page should render inside layout shell")
   assertTrue(body.contains("class=\"sidebar\""), "Page should have sidebar navigation")
   ```

The existing `UserManagementIntegrationTest` demonstrates this pattern for admin pages, auth flows, and navigation visibility.

### Desktop test execution model

The default `java.awt.headless=true` prevents `JFrame` creation, so GUI tests skip during normal local builds. **GUI tests are executed inside a Podman container with Xvfb**, ensuring they always run without popping up windows on the developer's machine.

| Command | What runs |
|---------|-----------|
| `mvn test -pl platform-desktop` | Headless tests only (ViewModel, ThemeManager, etc.) — no windows created |
| `pwsh scripts/test-desktop.ps1` | **All Swing tests** including GUI — runs inside Podman with Xvfb and copies reports |
| `mvn -Ptest-desktop verify` | **All Swing tests** including GUI — delegates to the same Podman/Xvfb wrapper |
| `mvn test -pl platform-desktop -Ptests-headful` | All tests against the local display (use only if you want windows) |

The desktop test image (`docker/Dockerfile.build --target desktop-test`) packages the project with Xvfb and runs `xvfb-run mvn test -pl platform-desktop -Ddesktop.headless=false`. This gives real pixel-level rendering without a physical screen.

ViewModel-level tests (e.g., `SyncViewModelAuthTest`) always run in every mode since they don't create AWT/Swing components.

## Authentication and user management

The platform includes a complete authentication system:

- **Login / Register** — both web (session cookie) and API (bearer token)
- **Password change** — API endpoint + web page with topbar link
- **Password reset** — email-based flow with time-limited tokens (uses `ConsoleEmailService` in dev)
- **User administration** — admin-only list/enable/disable/promote/demote
- **API key management** — SHA-256 hashed keys with `osk_` prefix, create/list/delete
- **Session timeout** — configurable inactivity timeout for both cookie and bearer sessions
- **Audit log** — records all auth-related actions (admin-visible at `/admin/audit`)

### Bearer authentication

The bearer auth filter (`bearerAuthFilter` in `App.kt`) supports two token types:
1. **User ID tokens** (UUIDs) — returned by the login/register endpoints
2. **API keys** — `osk_`-prefixed keys, hashed with SHA-256 and looked up in the `api_keys` table

Both are checked for session timeout before proceeding.

## Security

### HTTP security headers

All responses include: `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`. HTML responses additionally include a `Content-Security-Policy` header.

### Rate limiting

The `/api/v1/auth/login` and `/api/v1/auth/register` endpoints are rate-limited (10 requests/minute per IP) to prevent brute force attacks.

### CORS

Configurable via `corsOrigins` in `AppConfig` (default empty; the `dev` profile uses `*`). Explicit allowlists are
validated as `http[s]://host[:port]` origins and the server echoes only the requesting origin, with `Vary: Origin`.
Requests without an `Origin` header and requests from disallowed origins receive no CORS headers.

### WebSocket authentication

The `/ws/sync` WebSocket endpoint validates the session cookie and rejects unauthenticated connections with status code 4401.

## Operational features

### Health check

`GET /health/live` reports process liveness. `GET /health/ready` and its `/health` compatibility alias report database
and extension readiness, returning 503 when required dependencies are unavailable. Management routes accept only a
direct, unforwarded loopback connection or a valid `MANAGEMENT_TOKEN` bearer credential; `Host` is not trusted.

### Connection pooling

HikariCP manages the database connection pool (10 max, 2 idle, 10s timeout).

### Graceful shutdown

The shutdown hook coordinates: stops the outbox scheduler (waits 5s for in-flight work), then stops the Jetty server.

### Swagger UI

Available at `/swagger.html` — loads from CDN and points at the Auth, Sync, and Admin OpenAPI specs.

### Docker

- `docker/Dockerfile` — multi-stage build (Maven builder + JRE runtime)
- `docker/docker-compose.yml` — single service with persistent volume
- Build with: `mvn -Pdocker package`

## Build profile strategy

All common operations use Maven profiles — no shell scripts needed:

| Command | Purpose |
|---------|---------|
| `mvn compile` | Compile all modules |
| `mvn -Pfast compile` | Compile skipping all quality checks |
| `mvn test` | Run all tests (headless) |
| `mvn test -Ptests-headful` | Run all tests including GUI |
| `mvn -Pcoverage verify` | Run tests with JaCoCo coverage |
| `mvn -Pdocker package` | Build Docker image |
| `pwsh scripts/test-desktop.ps1` | Run Swing GUI tests inside Podman with Xvfb |
| `mvn -Ptest-desktop verify` | Run Swing GUI tests via the Maven wrapper profile |
| `mvn -Pseed compile exec:java` | Seed database with sample data |
| `mvn -Pruntime-dev compile exec:java -pl platform-web` | Run web app in dev mode |

## What is necessary right now

For this platform to remain healthy, these pieces are important:

- Flyway remains the schema authority
- Detekt ensures Kotlin code style and formatting (configured in `detekt.yml`)
- JDBI is the database access layer
- New repositories must be implemented in `platform-persistence-jdbi`
- JTE/KTE remains the server rendering path
- `jte-runtime` stays on the runtime classpath
- JTE hot-reload continues using the explicit application classloader
- concrete Outerstellar theme files are loaded, not `themes.json`
- **UI layout tests are maintained for every Swing dialog and panel** — mere existence checks are not sufficient
- **Integration tests use http4k function-level testing** — no running server needed
- **All auth features (login, register, password change/reset, admin, API keys) must have test coverage**

## Future evolution

The most likely next architectural improvements are:

1. extract shared sync/domain contracts more cleanly
2. expand sync beyond the message demo
3. replace `ConsoleEmailService` with SMTP integration for production
4. add TOTP two-factor authentication
5. add database backup/restore tooling

For now, the current decisions are intentional because they optimize for a usable, teachable platform with working web, database, templating, theming, i18n, authentication, and sync examples.
