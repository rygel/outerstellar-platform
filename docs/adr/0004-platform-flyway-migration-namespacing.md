# ADR-0004: Platform Flyway Migrations Must Be Namespaced

## Status: Accepted

## Context

`DatabaseInfra.migrate()` assembles the Flyway location list from two parts: the platform's own
migrations and an optional extension location supplied by a host app via `ExtensionMigrations`. The
platform's own migrations live at `classpath:db/migration/V1..Vn` (flat, top-level).

Flyway treats a `classpath:` location as a **root and scans it recursively**. When a host app
bundles the platform jars in a shaded/fat JAR, the runtime classpath commonly contains several
migration subtrees that each start at their own `V1`:

- `db/migration/V1__initial_schema.sql` … `V16` — platform core
- `db/migration/audit/V1__audit_schema.sql` — a sibling platform/private module
- `db/migration/mirrorlessdb/V1…V17` — the host extension's own migrations, declared via
  `ExtensionMigrations(location="classpath:db/migration/mirrorlessdb", historyTable=...)`

Because `migrate()` registers `classpath:db/migration` (the shared parent) as a location, two
compounding failures occur (#601):

1. Flyway recurses into **every** subdirectory under `db/migration` and finds two `V1` migrations
   (platform core `V1__initial_schema.sql` and the audit module's `audit/V1__audit_schema.sql`).
   It aborts with `Found more than one migration with version 1`, and **the host app cannot boot**.
2. When the extension's own nested location (e.g. `classpath:db/migration/mirrorlessdb`) is also
   registered, Flyway discards it as a *sub-location* of the already-registered parent
   `classpath:db/migration`, defeating the per-extension isolation that `ExtensionMigrations` is
   supposed to provide.

This was reported as a regression on v3.6.19 but the scanning behavior itself predates that release;
what changed was the presence of colliding sibling subtrees on host classpaths. The defect blocks any
host upgrade once more than one module ships a `V1` under the shared `db/migration` root.

## Decision

The platform must never register a Flyway location that can contain another module's migrations.
Concretely:

1. **Platform core migrations are namespaced under a dedicated subdirectory.** The platform's own
   migrations move from `db/migration/` to `db/migration/platform/` (or an equivalently
   platform-owned path). The `migrate()` call scans `classpath:db/migration/platform` — a location
   that only the platform owns and that no host or sibling module is expected to occupy.

2. **Extension/host migrations must occupy their own disjoint subdirectory** (e.g.
   `db/migration/<extension>/`), declared via `ExtensionMigrations.location`. They must never be
   placed directly under the shared `db/migration` root.

3. **`migrate()` never registers a shared parent of multiple migration owners.** The platform
   location and the extension location are peers (siblings under `db/migration/`), not a parent and
   child. This keeps Flyway from (a) recursing into foreign subtrees and causing version collisions,
   and (b) discarding the extension location as a sub-location of the platform's.

4. **The migration manifest (`migrations.index`) and native-image extraction track the namespaced
   path.** `MIGRATION_NAMES` and `extractMigrationsToFilesystem` read from the platform-owned
   subdirectory only, so native-image builds are unaffected by host migration trees.

## Consequences

Host apps can bundle the platform alongside their own and other modules' migrations without boot
failures, as long as each owner occupies a distinct subdirectory under `db/migration`. The
`ExtensionMigrations` isolation mechanism works as documented because Flyway sees independent peer
locations rather than a parent that shadows them.

The platform's migrations move one directory level deeper. This is a one-time relocation: the
migration files keep their `V{n}__{name}.sql` names and contents, and Flyway's
`flyway_schema_history` rows are keyed by version + checksum (not by classpath path), so existing
databases continue to validate without re-baselining. The relocation must be shipped as a single,
coordinated change (path move + `migrate()` scan path + manifest + native-image extraction path)
across `platform-persistence-jdbi`.

Anyone adding a new migration subtree on the classpath — platform module, host extension, or
sibling library — must place it under its own `db/migration/<owner>/` directory and never under the
shared root. CI should guard against a top-level (non-namespaced) `db/migration/*.sql` reappearing,
since its presence is what reintroduces this regression.
