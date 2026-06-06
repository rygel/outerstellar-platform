# Performance Optimizations — Design Spec

**Date:** 2026-05-16
**Status:** Draft
**Scope:** 7 performance items across platform-core, platform-web, platform-persistence-jooq, platform-persistence-jdbi

---

## 1. WebContext — Single Session Lookup

**Problem:** `WebContext.user` and `WebContext.sessionExpired` are independent `lazy` properties that each call `securityService.lookupSession(rawToken)`. When both are accessed during a request (common path: `sessionTimeout` filter checks `sessionExpired` then `user`), `lookupSession` fires twice — 2x DB reads + 2x DB writes per request.

**Solution:** Extract a single `lazy` session lookup result that both properties derive from.

```kotlin
private val sessionLookup: SessionLookup? by lazy {
    request.cookie(SESSION_COOKIE)?.value?.let { rawToken ->
        securityService?.lookupSession(rawToken)
    }
}

val user: User? by lazy {
    sessionLookup.let { lookup ->
        when (lookup) {
            is SessionLookup.Active -> lookup.user
            else -> null
        }
    }
}

val sessionExpired: Boolean by lazy {
    sessionLookup is SessionLookup.Expired
}
```

**Files:** `platform-web/.../web/WebContext.kt` (lines 92-112)

**Risk:** Low. Same data, derived once. The JWT lookup path in `user` remains independent because JWT lookup is a different code path.

---

## 2. Segment Analytics — Bounded Executor

**Problem:** `SegmentAnalyticsService.send()` spawns a new `Thread` per analytics event. Under load, this creates unbounded threads with no backpressure.

**Solution:** Replace with a single-thread `ExecutorService` backed by a bounded queue with drop-oldest policy.

```kotlin
private val executor = Executors.newSingleThreadExecutor { r ->
    Thread(r, "segment-analytics").also { it.isDaemon = true }
}

private val queue = LinkedBlockingQueue<Runnable>(100)
```

On queue full, drop the event and log at WARN. The executor reuses one thread for all events. On `close()`, call `executor.shutdown()` with a 5-second timeout.

**Files:** `platform-core/.../analytics/SegmentAnalyticsService.kt` (lines 85-96)

**Risk:** Low. Same fire-and-forget semantics. Drop policy is acceptable for analytics — losing an event is better than blocking a request thread.

---

## 3. Admin Actions — Single User Lookup

**Problem:** `UserAdminRoutes` toggle-enabled and toggle-role routes call `securityService.listUsers()` (loads ALL users) then `.find { it.id == userId }` to read current state for toggling. `SecurityService.setUserEnabled`/`setUserRole` already call `findById` internally, making this a redundant full-table scan.

**Solution:** Add `SecurityService.findUserSummary(id)` method, use it in toggle routes instead of `listUsers()`.

```kotlin
// SecurityService
fun findUserSummary(id: UUID): UserSummary? {
    return userRepository.findById(id)?.toSummary()
}
```

```kotlin
// UserAdminRoutes — toggle-enabled
val target = securityService.findUserSummary(UUID.fromString(userId))
if (target != null) {
    securityService.setUserEnabled(admin.id, UUID.fromString(userId), !target.enabled)
}
```

Same pattern for toggle-role.

**Files:**
- `platform-security/.../security/SecurityService.kt` (add method)
- `platform-web/.../web/UserAdminRoutes.kt` (lines 60-69, 99-109)

**Risk:** Low. `setUserEnabled`/`setUserRole` still do their own `findById` internally for validation — this is a redundant lookup but it's a single-row index hit, negligible cost. Removing the internal `findById` would require changing SecurityService's API contracts and is out of scope.

---

## 4. CaffeineMessageCache — RuntimeConfig Wiring + O(1) Invalidation

**Problem:**
- Cache uses hardcoded defaults (`maxSize=1000`, `ttl=10min`) ignoring `RuntimeConfig.cacheMessageMaxSize` / `cacheMessageExpireMinutes`.
- `invalidateByPrefix()` does O(N) key scan via `cache.asMap().keys.filter { it.startsWith(prefix) }`.

**Solution:**

**4a. Wire RuntimeConfig:**
Accept `maxSize` and `ttlMinutes` as constructor parameters, defaulting to current values. Wire in `WebModule`.

```kotlin
class CaffeineMessageCache(
    maxSize: Long = DEFAULT_MAX_SIZE,
    ttlMinutes: Long = DEFAULT_TTL_MINUTES,
    meterRegistry: MeterRegistry? = null,
)
```

```kotlin
// WebModule
single<MessageCache> {
    val config = get<AppConfig>()
    CaffeineMessageCache(
        maxSize = config.runtime.cacheMessageMaxSize.toLong(),
        ttlMinutes = config.runtime.cacheMessageExpireMinutes.toLong(),
    )
}
```

**4b. Version-key invalidation:**
Replace prefix-scan with a `ConcurrentHashMap<String, AtomicLong>` that tracks generation counters per cache namespace (e.g., `"messages"`, `"contacts"`). Include the generation in cache keys. On invalidation, increment the counter — old keys naturally expire via Caffeine's TTL.

```kotlin
private val generations = ConcurrentHashMap<String, AtomicLong>()

fun cacheKey(namespace: String, key: String): String {
    val gen = generations.computeIfAbsent(namespace) { AtomicLong(0) }.get()
    return "$namespace:$gen:$key"
}

override fun invalidateByPrefix(namespace: String) {
    generations.computeIfAbsent(namespace) { AtomicLong(0) }.incrementAndGet()
}
```

Callers that currently use `invalidateByPrefix("list:")` and `invalidateByPrefix("message:")` switch to the namespace model. Cache keys change from `"list:query:year:limit:offset"` to `"list:<gen>:query:year:limit:offset"`.

**Files:**
- `platform-core/.../persistence/CaffeineMessageCache.kt`
- `platform-core/.../persistence/MessageCache.kt` (interface change: `invalidateByPrefix` → `invalidate` with namespace)
- `platform-web/.../di/WebModule.kt`

**Risk:** Medium. Interface change to `MessageCache` affects callers. The `invalidateByPrefix` callers in `MessageService` need updating. Existing cache entries are lost on deploy (acceptable — cache is transient).

---

## 5. JteClassRegistry — Map Lookup

**Problem:** `getTemplateClass()` does O(N) linear scan over 34 classes on every production template render.

**Solution:** Build a `Map<String, Class<*>>` once at init.

```kotlin
private val classMap: Map<String, Class<*>> =
    allClasses.associateBy { it.name }

fun getTemplateClass(templateName: String): Class<*>? {
    // ... same name derivation logic ...
    return classMap[fullName]
}
```

**Files:** `platform-web/.../infra/JteClassRegistry.kt` (lines 92, 105-118)

**Risk:** Minimal. Same behavior, O(1) instead of O(N). Map is built once at class load.

---

## 6. ETag Filter — Skip Dynamic HTML

**Problem:** `etagCachingFilter` fully buffers every non-JSON 200 response body into a byte array, computes SHA-256, then wraps it back. This doubles memory for large HTML pages and adds latency. Dynamic HTML changes on every render (user data, timestamps), making ETags ineffective — clients never send `If-None-Match` for dynamic pages.

**Solution:** Only compute ETags for responses with cacheable content types (CSS, JS, fonts, images). Skip HTML entirely.

```kotlin
private val CACHEABLE_TYPES = setOf(
    "text/css",
    "application/javascript",
    "application/font-",
    "font/",
    "image/",
)

// In filter: skip if text/html or not in cacheable set
if (contentType.contains("text/html") || !CACHEABLE_TYPES.any { contentType.startsWith(it) }) {
    return response  // skip ETag for dynamic content
}
```

This means dynamic HTML responses stream directly without buffering. Static assets (CSS/JS from the Tailwind build) still get ETags and benefit from `304 Not Modified`.

**Files:** `platform-web/.../web/Filters.kt` (lines 59-81)

**Risk:** Low. Static assets still get ETags. Dynamic HTML never benefited from them. No behavioral change for end users.

---

## 7. Search Indexes — pg_trgm GIN

**Problem:** Message and contact search uses `%LIKE%` on `content`, `name`, `company` columns with no text indexes, forcing sequential scans.

**Solution:** Add a Flyway migration enabling `pg_trgm` extension and creating GIN trigram indexes.

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_plt_messages_content_trgm
    ON plt_messages USING GIN (content gin_trgm_ops);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_plt_contacts_name_trgm
    ON plt_contacts USING GIN (name gin_trgm_ops);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_plt_contacts_company_trgm
    ON plt_contacts USING GIN (company gin_trgm_ops);
```

`CONCURRENTLY` avoids locking the table during index creation on existing data. The `%LIKE%` queries automatically use the trigram indexes — no SQL changes needed.

**Files:**
- New migration: `platform-persistence-jooq/src/main/resources/db/migration/V10__add_trgm_search_indexes.sql`
- New migration: `platform-persistence-jdbi/src/main/resources/db/migration/V10__add_trgm_search_indexes.sql`

**Risk:** Low. `pg_trgm` is a standard PostgreSQL extension. Index creation is concurrent (no table lock). Storage overhead is moderate (~2-3x column size for the index). No query changes. Regenerate jOOQ sources not needed (no schema column changes).

---

## Implementation Order

1. JteClassRegistry (trivial, zero risk)
2. Segment analytics executor (isolated, zero risk)
3. ETag filter skip (isolated, zero risk)
4. WebContext single lookup (core change, medium verification)
5. Admin actions single lookup (simple, needs test updates)
6. CaffeineMessageCache (interface change, needs careful wiring)
7. Search indexes (migration, concurrent, needs jOOQ awareness)

## Testing Strategy

- **Unit tests:** WebContext session resolution, CaffeineMessageCache generation counters, SegmentAnalyticsService executor behavior
- **Integration tests:** Admin toggle routes (verify they no longer call listUsers), MessageService cache invalidation
- **Migration:** V10 runs in CI against PostgreSQL Testcontainers
- **Full reactor verify** before push as per AGENTS.md
