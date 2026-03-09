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

The project is currently a **single Maven module** organized by package:

- `dev.outerstellar.starter` - bootstrap, configuration, routing
- `...persistence` - repository contract and jOOQ-backed implementation
- `...sync` - shared sync DTOs, sync routes, client sync service
- `...web` - page/view-model factory for JTE/KTE templates
- `...swing` - desktop client, theme manager, launch config
- `src/main/jte` - JTE Kotlin templates
- `src/main/resources/db/migration` - Flyway migrations

### Why a single module?

This was chosen to get to a complete, running starter quickly while keeping the wiring easy to follow.

### What would likely change later?

A future cleanup should split this into modules such as:

- `shared-core`
- `server-app`
- `swing-app`

That would improve separation, but the current single-module layout is intentional for bootstrap speed and demo clarity.

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

The build includes workbook-style quality tooling and project hygiene:

- Enforcer
- JaCoCo
- SpotBugs
- Modernizer
- Spotless
- Checkstyle
- PMD
- Detekt
- Versions Maven Plugin
- OWASP Dependency Check

These are included so the starter begins with the same quality expectations as downstream projects.

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

### 1. jOOQ/Flyway code generation against H2

**Finding:** H2 file locking can occur when Flyway and jOOQ touch the same database during build generation.

**Decision:** use `AUTO_SERVER=TRUE` on the codegen JDBC URL.

**Why:** it prevents code generation from tripping over the migrated schema file during the Maven lifecycle.

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

## What is necessary right now

For this starter to remain healthy, these pieces are important:

- Flyway remains the schema authority
- jOOQ remains the database access layer
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
