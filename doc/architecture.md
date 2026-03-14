# Outerstellar Starter Architecture

## Purpose

This starter is meant to provide a working vertical slice for:

- Kotlin on JDK 21
- http4k for the web server
- Flyway for schema migration
- jOOQ for database access and code generation
- H2 for local development and demo persistence
- JTE with Kotlin templates (`.kte`) for server-rendered HTML
- HTMX for progressive enhancement and fragment swapping
- A Swing desktop demo with local persistence and two-way sync
- Outerstellar i18n and theme integration

The goal is to start from a runnable platform, not just a dependency list.

## High-level shape

The project is organized as a **multi-module Maven project**:

- `core` - shared domain models, services, and configuration
- `persistence` - jOOQ-backed repository implementation and Flyway migrations
- `api-client` - shared sync DTOs and client sync service
- `web` - http4k web server, JTE templates, and HTMX interactions
- `desktop` - Swing desktop client, theme manager, and UI tests
- `security` - security-related utilities and configuration

### Why multiple modules?

This structure provides better separation of concerns, allows for independent testing, and keeps dependencies scoped to where they are actually needed (e.g., Swing-specific libraries are only in `desktop`).

## Runtime architecture

### Web application

- `Main.kt` creates the data source, runs Flyway, builds the jOOQ repository, creates the JTE renderer, and starts Jetty via http4k.
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
- It uses its own H2 database and the same repository style.
- `SyncService.kt` pushes local dirty changes and pulls server changes through `/api/v1/sync`.
- `ThemeManager.kt` applies FlatLaf plus an Outerstellar-inspired palette.

This gives a concrete offline-capable sync example without introducing a second stack.

## Persistence and schema

### H2

H2 was chosen because it is ideal for a starter:

- zero external setup
- easy local use for both server and Swing demo
- works well with Flyway and jOOQ
- keeps the starter easy to run

### Flyway

Flyway is the source of truth for schema evolution.

Current migrations:

- `V1__create_messages.sql`
- `V2__add_sync_support.sql`

### jOOQ

jOOQ is the database interface by design.

Why:

- type-safe SQL access
- generated schema bindings
- clean fit for Kotlin
- keeps SQL explicit instead of hiding it behind ORM behavior

The repository layer wraps jOOQ so the app uses a small domain-oriented API rather than scattered DSL calls.

#### jOOQ code generation policy

The project uses **manual jOOQ code generation** with generated sources checked into version control.

- generated sources location: `persistence/src/main/generated/jooq`
- generation profile: `jooq-codegen`
- generation command: `mvn -pl persistence -Pjooq-codegen generate-sources`
- PowerShell shortcut: `./generate-jooq.ps1`

This keeps builds deterministic and removes implicit schema/codegen drift between environments.

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

This is acceptable for a starter and gives a clean base for expansion.

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

The starter uses `outerstellar-i18n` for localization.

Current bundles include:

- `web-messages.properties`
- `web-messages_fr.properties`
- `swing-messages.properties`
- `swing-messages_fr.properties`

### Theming

The starter uses `outerstellar-theme` plus local theme selection.

Current web theme choices:

- dark
- bootstrap

Current Swing theme choices:

- FlatLaf light
- FlatLaf dark
- Outerstellar-themed dark palette

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
- Versions Maven Plugin (dependency updates)
- OWASP Dependency Check (security vulnerabilities)

These are included and enforced so the starter begins with and maintains the exact same rigorous quality expectations as downstream projects.

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

### `generate-jooq.ps1`

Regenerates jOOQ sources using the manual `jooq-codegen` profile.

Use this whenever Flyway migrations or jOOQ generation config changes.

## Important findings and gotchas

These are worth preserving because they caused real issues during implementation.

### 1. jOOQ/Flyway code generation against H2

**Finding:** H2 file locking can occur when Flyway and jOOQ touch the same database during code generation.

**Decision:** use `AUTO_SERVER=TRUE` on the codegen JDBC URL and run generation manually via profile `jooq-codegen`.

**Why:** it prevents code generation lock contention while avoiding implicit generation during every build.

### 2. Kotlin/http4k compatibility

**Finding:** current http4k metadata was not happy with the earlier Kotlin version that was first used.

**Decision:** move to Kotlin `2.3.10`.

**Why:** this resolved the compatibility issue cleanly.

### 3. JTE runtime dependency

**Finding:** the application could fail at runtime with:

`NoClassDefFoundError: gg/jte/html/HtmlTemplateOutput`

even though templates compiled.

**Decision:** keep `gg.jte:jte-runtime` as a direct dependency.

**Why:** the runtime engine needs those classes available when rendering generated templates.

### 4. JTE hot-reload classloader

**Finding:** when creating the JTE engine for hot reload, the generated classes did not automatically see the full application classpath.

**Decision:** explicitly pass the application classloader into `TemplateEngine.create(...)`.

**Why:** without that, generated template classes could not reliably resolve runtime classes under `exec:java`.

### 5. JTE output location

**Finding:** default hot-reload behavior could write generated template classes into an undesirable repo-root output location.

**Decision:** direct generated JTE classes to `target/jte-classes`.

**Why:** it keeps generated artifacts under `target/` and avoids polluting the project root.

### 6. Outerstellar theme resource loading

**Finding:** loading `themes.json` directly caused deserialization failure because it is not a single concrete theme payload for this usage.

**Decision:** load concrete files such as:

- `themes/dark.json`
- `themes/bootstrap.json`

**Why:** the theme service expects a specific theme structure in this path.

### 7. Web startup process handling

**Finding:** stopping only the Maven process did not always stop the actual Jetty listener.

**Decision:** capture the server PID from the listening port and stop the process tree.

**Why:** this avoids zombie server processes and makes `start-web.ps1` / `stop-web.ps1` reliable.

### 8. PID shutdown races

**Finding:** process-tree shutdown can race with already-exiting children.

**Decision:** use tolerant process termination in `stop-web.ps1`.

**Why:** shutdown should remain reliable even if a child disappears between discovery and termination.

### 9. Headless Swing UI testing

**Finding:** Running Swing UI tests in a local or CI environment can lead to unexpected window popups and mouse cursor capture, which can be disruptive and cause test failures in non-GUI environments.

**Decision:** Enforce headless mode by default for all desktop tests using `java.awt.headless=true` in Maven, while providing a toggle switch (`-Dheadless=false`) for manual GUI verification.

**Why:** This ensures tests are stable, non-intrusive, and compatible with headless CI pipelines, while still allowing developers to run full GUI tests when needed.

## What is necessary right now

For this starter to remain healthy, these pieces are important:

- Flyway remains the schema authority
- Detekt ensures Kotlin code style and formatting (configured in `detekt.yml`)
- jOOQ remains the database access layer
- jOOQ generated sources remain checked in under `persistence/src/main/generated/jooq`
- jOOQ sources are regenerated manually with `-Pjooq-codegen` when schema changes
- JTE/KTE remains the server rendering path
- `jte-runtime` stays on the runtime classpath
- JTE hot-reload continues using the explicit application classloader
- concrete Outerstellar theme files are loaded, not `themes.json`
- `start-web.ps1` / `stop-web.ps1` remain the supported background runtime controls

## Future evolution

The most likely next architectural improvements are:

1. split into multiple Maven modules
2. extract shared sync/domain contracts more cleanly
3. add real authentication back-end behavior behind the example pages
4. expand sync beyond the message demo
5. introduce stronger error handling and observability conventions

For now, the current decisions are intentional because they optimize for a usable, teachable starter with working web, database, templating, theming, i18n, and sync examples.
