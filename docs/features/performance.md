# Performance

## Caching

### Message Cache

`CaffeineMessageCache` (platform-core): 1,000 entries, 10-minute TTL. Caches list results and entity lookups. Configurable via `runtime.cacheMessageMaxSize` and `runtime.cacheMessageExpireMinutes`.

### Gravatar Cache

Caffeine cache (10,000 entries, 1-hour TTL) avoids repeated MD5 hashing and HTTP calls for avatar URLs.

### Sidebar Selectors

Theme, language, and layout selectors are cached in `SidebarFactory` (500 entries, 5-minute TTL). Keyed by `{type}:{lang}:{theme}:{layout}`.

## Database

### Connection Pool

HikariCP with configurable sizing (6 knobs in `runtime.*`). Default: pool size 20, min idle 2.

### Indexes

| Table | Index | Query |
|---|---|---|
| `plt_messages` | `(dirty, deleted_at)` | listDirtyMessages |
| `plt_messages` | `(updated_at_epoch_ms DESC, id DESC)` | listMessages, findChangesSince |
| `plt_contacts` | `(dirty)` | listDirtyContacts |
| `plt_contacts` | `(updated_at_epoch_ms DESC, id DESC)` | findChangesSince |
| `plt_sessions` | `(token_hash)` | Every authenticated request |
| `plt_sessions` | `(user_id)` | Session invalidation |
| `plt_outbox` | `(status)` | listPending, listFailed |
| `plt_notifications` | `(user_id, read_at)` | listForUser |
| `plt_password_reset_tokens` | `(token)` | Reset flow |

### Fetch Projections

All jOOQ and JDBI queries use explicit column projections instead of `SELECT *`. This avoids transferring unused columns (notably `created_at` on several tables, and `processed_at`/`retry_count`/`last_error`/`deleted_at` on outbox reads).

## Startup

### Phases

```
Phase 1: Config validation       ~40ms  (resolves AppConfig only)
Phase 2: Server start            ~2800ms (resolves app handler, DataSource, Flyway)
Phase 3: Background jobs         ~150ms (admin seeding, outbox scheduler)
```

Logged at startup as: `Startup — Koin modules loaded: 42ms`

### Migration Runner

For production, set `FLYWAY_ENABLED=false` on the app and run migrations separately:
```bash
mvn -pl platform-persistence-jooq -Pmigrate exec:java
```

### JTE Preload

Set `JTE_PRELOAD_ENABLED=true` or `JTE_PRODUCTION=true` to preload template classes at startup instead of lazily. Reduces first-request latency.

## Filter Chain

Static assets, `/health`, `/metrics`, `/robots.txt`, and `/sitemap.xml` are served **before** the filter chain, skipping:
- User session resolution (DB lookup)
- CSRF checks
- Rate limiting
- State filter (WebContext construction)

## Outbox Processing

Adaptive scheduler:
- Processes immediately when backlog exists (continuous draining)
- Exponential backoff when empty (1s, 2s, 4s, 8s, 16s, up to 30s max)
- Configurable batch size (default 10)
- Tracks `totalProcessed` and `totalFailed` counters

## Database Query Monitoring

Every server-rendered request logs DB query count and duration:
```
[req-abc] GET /admin/users -> 200 OK (243ms, 12 DB queries)
[req-def] GET /site.css -> 200 OK (2ms)
```

## Profiles

Use `APP_PROFILE=small` for low-resource deployments, `APP_PROFILE=large` for high-throughput. See [Configuration](configuration.md) for the full parameter differences.
