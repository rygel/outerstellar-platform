# ADR-0001: UTC-only timestamps in the database

## Status: Accepted

## Context

The Outerstellar Platform's PostgreSQL schema currently uses a **mix** of `TIMESTAMP` (without time zone) and `TIMESTAMP WITH TIME ZONE` (TIMESTAMPTZ) columns. This inconsistency causes silent data corruption on JVMs that do not run in the UTC timezone.

### How the bug manifests

The PostgreSQL JDBC driver handles these two column types differently:

- **TIMESTAMPTZ** — the driver stores and retrieves values as absolute points in time (epoch millis). `rs.getTimestamp(col).toInstant()` is always correct regardless of JVM timezone.

- **TIMESTAMP** — the driver treats stored values as "local time in the JVM's default timezone." On a JVM running in EEST (UTC+2), `rs.getTimestamp(col).toInstant()` shifts the value by +2 hours. A token that expires at `16:00 UTC` is read as `18:00 UTC`, or vice versa, causing password reset tokens to appear expired (or not yet valid).

This broke `PasswordResetFlowIntegrationTest` and `JdbiUserRepositoryTest` on developer machines outside UTC. The only reliable fix is to eliminate the ambiguity at the schema level.

### Current breakdown

**TIMESTAMPTZ** (correct, timezone-aware):

| Table | Column |
|-------|--------|
| `plt_users` | `created_at` |
| `plt_users` | `locked_until` |
| `plt_users` | `last_activity_at` |
| `plt_polls` | `closed_at`, `deadline`, `created_at`, `updated_at` |
| `plt_poll_options` | `created_at` |
| `plt_poll_votes` | `created_at` |

**TIMESTAMP** (broken on non-UTC JVMs):

| Table | Column |
|-------|--------|
| `plt_messages` | `created_at`, `deleted_at` |
| `plt_contacts` | `created_at`, `updated_at_epoch_ms` (epoch millis, unaffected) |
| `plt_outbox` | `created_at`, `processed_at`, `deleted_at` |
| `plt_sessions` | `created_at`, `expires_at` |
| `plt_audit_log` | `created_at` |
| `plt_password_reset_tokens` | `expires_at`, `created_at` |
| `plt_api_keys` | `created_at`, `last_used_at` |
| `plt_contacts_activity` | `created_at`, `last_seen` |
| `plt_message_attachments` | `created_at` |
| `plt_notifications` | `read_at`, `created_at` |

## Decision

1. **All timestamp columns must use `TIMESTAMP WITH TIME ZONE` (TIMESTAMPTZ).** No exceptions. This eliminates JVM-timezone-dependent behavior entirely.

2. **Application code must use `Instant` exclusively** for all timestamp values. No `LocalDateTime`, no `java.util.Date`, no `java.sql.Timestamp` in business logic. Repository implementations convert between `Instant` and JDBC types at the boundary.

3. **Writes bind `Timestamp.from(instant)`** (or let JDBI's `ArgumentFactory` handle it). No manual `LocalDateTime.ofInstant(...)` conversions.

4. **Reads use `rs.getTimestamp(col).toInstant()`** for nullable columns, or the non-nullable variant with a meaningful error. No `toLocalDateTime().atZone(UTC)` workarounds.

5. **A Flyway migration converts all existing `TIMESTAMP` columns to `TIMESTAMP WITH TIME ZONE`.** Existing data is assumed to be in UTC and is converted in-place via `ALTER TABLE ... ALTER COLUMN ... TYPE TIMESTAMPTZ USING ... AT TIME ZONE 'UTC'`.

6. **CI and production must run with JVM timezone `UTC`.** This is a defense-in-depth measure. Even with TIMESTAMPTZ, explicit UTC avoids any remaining edge cases in logging, scheduling, or third-party libraries.

## Consequences

### What becomes easier

- **Zero timezone bugs.** All timestamp arithmetic is correct regardless of JVM timezone.
- **Simpler repository code.** Every read is `toInstant()`, every write is `Timestamp.from(instant)`. No `toLocalDateTime().atZone(UTC)` gymnastics.
- **Portable tests.** Tests pass on any developer machine, not just those set to UTC.
- **Single pattern to teach.** New contributors and agents have one rule: use `Instant`, trust TIMESTAMPTZ.

### What becomes harder

- **Migration risk.** Converting existing data requires care. If any existing data was stored with implicit local-timezone offsets, the `AT TIME ZONE 'UTC'` conversion will shift it. We must verify the data is clean before migrating.
- **Raw SQL queries.** Anyone writing ad-hoc SQL must remember that TIMESTAMPTZ columns display in the session's timezone (`SHOW timezone`). For UTC output, use `SET timezone = 'UTC'` or `col AT TIME ZONE 'UTC'`.

### What we commit to

- Code review must reject any new `TIMESTAMP` column in migrations.
- Code review must reject `LocalDateTime` in persistence code (use `Instant` instead).
- The AGENTS.md guardrails file must reference this ADR in the database schema rules section.
