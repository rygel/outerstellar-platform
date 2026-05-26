# Performance Optimizations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate 7 performance bottlenecks: double session lookups, unbounded analytics threads, full-table admin scans, unwired cache config, linear JTE lookups, full-body ETag hashing, and missing search indexes.

**Architecture:** Each task is independent — no task depends on another. Implement in order of risk (lowest first). Each task produces a self-contained commit.

**Tech Stack:** Kotlin, Caffeine cache, jOOQ/JDBI, PostgreSQL pg_trgm, http4k, Koin DI

---

## Task 1: JteClassRegistry — O(1) Map Lookup

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/infra/JteClassRegistry.kt:92,117`

- [ ] **Step 1: Build a Map from the existing `allClasses` list and use it in `getTemplateClass`**

In `JteClassRegistry.kt`, after line 92 (`val allClasses: List<Class<*>> = ...`), add a map field. Then change line 117 from `.find {}` to map lookup.

Replace line 92 with:
```kotlin
    val allClasses: List<Class<*>> = pageClasses + fragmentClasses + componentClasses + layoutClasses

    private val classMap: Map<String, Class<*>> = allClasses.associateBy { it.name }
```

Replace line 117 (`return allClasses.find { it.name == fullName }`) with:
```kotlin
        return classMap[fullName]
```

- [ ] **Step 2: Verify build compiles**

Run: `mvn -pl platform-web compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true`

Expected: BUILD SUCCESS

- [ ] **Step 3: Run platform-web tests**

Run: `mvn -pl platform-web test -Dexec.skip=true`

Expected: All tests pass

- [ ] **Step 4: Commit**

```
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/infra/JteClassRegistry.kt
git commit -m "perf: replace linear scan with Map lookup in JteClassRegistry"
```

---

## Task 2: Segment Analytics — Bounded Executor

**Files:**
- Modify: `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/analytics/SegmentAnalyticsService.kt:85-96`

- [ ] **Step 1: Replace per-event Thread with single-thread ExecutorService**

Add imports at top (after existing imports):
```kotlin
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
```

Add a private executor field after line 23 (`private val authHeader = ...`):
```kotlin
    private val executor: ExecutorService = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue(100),
        { r -> Thread(r, "segment-analytics").also { it.isDaemon = true } },
    ) { _, _ -> logger.warn("Segment analytics queue full — dropping event") }
```

Replace lines 85-96 (the Thread creation block inside `send()`) with:
```kotlin
            try {
                executor.execute {
                    try {
                        client.send(request, HttpResponse.BodyHandlers.discarding())
                    } catch (e: Exception) {
                        logger.warn("Segment {} call failed: {}", endpoint, e.message)
                    }
                }
            } catch (e: java.util.concurrent.RejectedExecutionException) {
                logger.warn("Segment analytics event dropped: queue full")
            }
```

- [ ] **Step 2: Verify build compiles**

Run: `mvn -pl platform-core compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true`

Expected: BUILD SUCCESS

- [ ] **Step 3: Run platform-core tests**

Run: `mvn -pl platform-core test`

Expected: All tests pass

- [ ] **Step 4: Commit**

```
git add platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/analytics/SegmentAnalyticsService.kt
git commit -m "perf: replace per-event Thread with bounded single-thread executor in SegmentAnalytics"
```

---

## Task 3: ETag Filter — Skip Dynamic HTML

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/Filters.kt:59-81`

- [ ] **Step 1: Add a content-type check to skip HTML and only ETag cacheable types**

Replace the entire `etagCachingFilter` (lines 59-81) with:
```kotlin
val etagCachingFilter: Filter = Filter { next: HttpHandler ->
    { request ->
        val response = next(request)
        val contentType = response.header("content-type") ?: ""
        val isCacheable = contentType.contains("text/css") ||
            contentType.contains("javascript") ||
            contentType.contains("font/") ||
            contentType.contains("image/")
        if (
            response.status == Status.OK && response.header("ETag") == null && isCacheable
        ) {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = response.body.stream.readBytes()
            digest.update(bytes)
            val hash = digest.digest().take(8).joinToString("") { "%02x".format(it) }
            val etag = "\"$hash\""
            val ifNoneMatch = request.header("If-None-Match")
            if (ifNoneMatch == etag) {
                Response(Status.NOT_MODIFIED)
            } else {
                response.body(Body(ByteBuffer.wrap(bytes))).header("ETag", etag)
            }
        } else {
            response
        }
    }
}
```

- [ ] **Step 2: Verify build and tests**

Run: `mvn -pl platform-web test -Dexec.skip=true`

Expected: All tests pass

- [ ] **Step 3: Commit**

```
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/Filters.kt
git commit -m "perf: skip ETag computation for dynamic HTML, only hash cacheable static assets"
```

---

## Task 4: WebContext — Single Session Lookup

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/WebContext.kt:92-112`

- [ ] **Step 1: Extract single session lookup, derive both user and sessionExpired from it**

Replace lines 92-112 (`val user` and `val sessionExpired`) with:
```kotlin
    private val sessionLookup: SessionLookup? by lazy {
        request.cookie(SESSION_COOKIE)?.value?.let { rawToken ->
            securityService?.lookupSession(rawToken)
        }
    }

    val user: User? by lazy {
        when (sessionLookup) {
            is SessionLookup.Active -> sessionLookup.user
            else ->
                request.cookie(JWT_COOKIE)?.value?.let { token ->
                    jwtService?.extractClaims(token)?.let { (userId, _) ->
                        userRepository?.findById(userId)?.takeIf { it.enabled }
                    }
                }
        }
    }

    val sessionExpired: Boolean by lazy {
        sessionLookup is SessionLookup.Expired
    }
```

Key change: `sessionLookup` is resolved once. `user` derives from it (falling back to JWT if session is null). `sessionExpired` checks the same lookup result.

- [ ] **Step 2: Verify build and tests**

Run: `mvn -pl platform-web test -Dexec.skip=true`

Expected: All tests pass

- [ ] **Step 3: Commit**

```
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/WebContext.kt
git commit -m "perf: resolve session lookup once per request in WebContext"
```

---

## Task 5: Admin Actions — Single User Lookup

**Files:**
- Modify: `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SecurityService.kt` (add method after line 151)
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/UserAdminRoutes.kt:60-69,99-109`

- [ ] **Step 1: Add `findUserSummary` to SecurityService**

After line 151 (`fun countUsers(): Long = userRepository.countAll()`), add:
```kotlin

    fun findUserSummary(id: UUID): UserSummary? = userRepository.findById(id)?.toSummary()
```

- [ ] **Step 2: Update toggle-enabled route to use `findUserSummary`**

In `UserAdminRoutes.kt`, replace lines 64-68:
```kotlin
                        val users = securityService.listUsers()
                        val target = users.find { it.id == userId }
                        if (target != null) {
                            securityService.setUserEnabled(admin.id, UUID.fromString(userId), !target.enabled)
                        }
```
with:
```kotlin
                        val target = securityService.findUserSummary(UUID.fromString(userId))
                        if (target != null) {
                            securityService.setUserEnabled(admin.id, UUID.fromString(userId), !target.enabled)
                        }
```

- [ ] **Step 3: Update toggle-role route to use `findUserSummary`**

Replace lines 103-107:
```kotlin
                        val users = securityService.listUsers()
                        val target = users.find { it.id == userId }
                        if (target != null) {
                            val newRole = if (target.role == UserRole.ADMIN) UserRole.USER else UserRole.ADMIN
                            securityService.setUserRole(admin.id, UUID.fromString(userId), newRole)
                        }
```
with:
```kotlin
                        val target = securityService.findUserSummary(UUID.fromString(userId))
                        if (target != null) {
                            val newRole = if (target.role == UserRole.ADMIN) UserRole.USER else UserRole.ADMIN
                            securityService.setUserRole(admin.id, UUID.fromString(userId), newRole)
                        }
```

- [ ] **Step 4: Verify build and tests**

Run: `mvn -pl platform-security,platform-web test -Dexec.skip=true`

Expected: All tests pass

- [ ] **Step 5: Commit**

```
git add platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SecurityService.kt platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/UserAdminRoutes.kt
git commit -m "perf: replace listUsers with findUserSummary for admin toggle actions"
```

---

## Task 6: CaffeineMessageCache — RuntimeConfig + Generation Invalidation

**Files:**
- Modify: `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/MessageCache.kt` (interface)
- Modify: `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/CaffeineMessageCache.kt` (impl)
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/di/WebModule.kt:67`
- Modify: `platform-core/src/test/kotlin/io/github/rygel/outerstellar/platform/persistence/CaffeineMessageCacheTest.kt`

- [ ] **Step 1: Update MessageCache interface — rename `invalidateByPrefix` to `invalidateNamespace`**

In `MessageCache.kt`, replace line 14:
```kotlin
    fun invalidateByPrefix(prefix: String) = invalidateAll()
```
with:
```kotlin
    fun invalidateNamespace(namespace: String) = invalidateAll()
```

- [ ] **Step 2: Update CaffeineMessageCache — accept config params, use generation counters**

Replace the entire `CaffeineMessageCache.kt` with:
```kotlin
package io.github.rygel.outerstellar.platform.persistence

import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

private const val DEFAULT_MAX_SIZE = 1000L
private const val DEFAULT_TTL_MINUTES = 10L

class CaffeineMessageCache(
    maxSize: Long = DEFAULT_MAX_SIZE,
    ttlMinutes: Long = DEFAULT_TTL_MINUTES,
    meterRegistry: MeterRegistry? = null,
) : MessageCache {
    private val cache =
        Caffeine.newBuilder()
            .maximumSize(maxSize)
            .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
            .recordStats()
            .build<String, Any>()

    private val generations = ConcurrentHashMap<String, AtomicLong>()

    init {
        if (meterRegistry != null) {
            CaffeineCacheMetrics.monitor(meterRegistry, cache, "messageCache")
        }
    }

    override fun get(key: String): Any? = cache.getIfPresent(key)

    override fun put(key: String, value: Any) {
        cache.put(key, value)
    }

    override fun getOrPut(key: String, loader: () -> Any): Any = cache.get(key) { loader() }

    override fun invalidate(key: String) {
        cache.invalidate(key)
    }

    override fun invalidateAll() {
        cache.invalidateAll()
    }

    override fun invalidateNamespace(namespace: String) {
        generations.computeIfAbsent(namespace) { AtomicLong(0) }.incrementAndGet()
    }

    fun generationKey(namespace: String, key: String): String {
        val gen = generations.computeIfAbsent(namespace) { AtomicLong(0) }.get()
        return "$namespace:$gen:$key"
    }

    override fun getStats(): Map<String, Any> {
        val stats = cache.stats()
        return mapOf(
            "hitCount" to stats.hitCount(),
            "missCount" to stats.missCount(),
            "evictionCount" to stats.evictionCount(),
            "evictionWeight" to stats.evictionWeight(),
            "hitRate" to stats.hitRate(),
            "missRate" to stats.missRate(),
            "averageLoadPenalty" to stats.averageLoadPenalty(),
        )
    }
}
```

- [ ] **Step 3: Wire RuntimeConfig in WebModule**

In `WebModule.kt`, replace line 67:
```kotlin
        single<MessageCache> { io.github.rygel.outerstellar.platform.persistence.CaffeineMessageCache() }
```
with:
```kotlin
        single<MessageCache> {
            val runtime = get<AppConfig>().runtime
            io.github.rygel.outerstellar.platform.persistence.CaffeineMessageCache(
                maxSize = runtime.cacheMessageMaxSize.toLong(),
                ttlMinutes = runtime.cacheMessageExpireMinutes.toLong(),
            )
        }
```

- [ ] **Step 4: Update all `invalidateByPrefix` callers to `invalidateNamespace`**

In `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/service/MessageService.kt`, replace all 7 occurrences of `invalidateByPrefix` with `invalidateNamespace`:

```
cache.invalidateByPrefix("list:")  →  cache.invalidateNamespace("list")
```

Note: the prefix `"list:"` becomes namespace `"list"` (no trailing colon needed with generation counters).

- [ ] **Step 5: Update MessageCacheTest mocks**

In `platform-core/src/test/kotlin/io/github/rygel/outerstellar/platform/service/MessageCacheTest.kt`, replace all occurrences:
```
cache.invalidateByPrefix("list:")  →  cache.invalidateNamespace("list")
```

- [ ] **Step 6: Update CaffeineMessageCacheTest**

Replace `platform-core/src/test/kotlin/io/github/rygel/outerstellar/platform/persistence/CaffeineMessageCacheTest.kt` with:
```kotlin
package io.github.rygel.outerstellar.platform.persistence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CaffeineMessageCacheTest {

    private val cache = CaffeineMessageCache()

    @Test
    fun `invalidateNamespace bumps generation making old keys stale`() {
        cache.put("list:0:all:null:10:0", "result-1")
        cache.put("entity:abc-123", "message-1")

        cache.invalidateNamespace("list")

        assertNull(cache.get("list:0:all:null:10:0"))
        assertEquals("message-1", cache.get("entity:abc-123"))
    }

    @Test
    fun `invalidateNamespace with no prior keys is a no-op`() {
        cache.put("entity:abc-123", "message-1")

        cache.invalidateNamespace("list")

        assertEquals("message-1", cache.get("entity:abc-123"))
    }

    @Test
    fun `generationKey includes current generation`() {
        assertEquals("list:0:query", cache.generationKey("list", "query"))
        cache.invalidateNamespace("list")
        assertEquals("list:1:query", cache.generationKey("list", "query"))
    }

    @Test
    fun `constructor accepts custom size and TTL`() {
        val custom = CaffeineMessageCache(maxSize = 50, ttlMinutes = 1)
        custom.put("key", "value")
        assertEquals("value", custom.get("key"))
    }
}
```

- [ ] **Step 7: Update test Stubs.kt if needed**

Check `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/Stubs.kt` for `invalidateByPrefix` and replace with `invalidateNamespace` if present.

- [ ] **Step 8: Verify build and tests**

Run: `mvn -pl platform-core,platform-web test -Dexec.skip=true`

Expected: All tests pass

- [ ] **Step 9: Commit**

```
git add platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/MessageCache.kt platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/CaffeineMessageCache.kt platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/di/WebModule.kt platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/service/MessageService.kt platform-core/src/test/kotlin/io/github/rygel/outerstellar/platform/persistence/CaffeineMessageCacheTest.kt platform-core/src/test/kotlin/io/github/rygel/outerstellar/platform/service/MessageCacheTest.kt
git commit -m "perf: wire RuntimeConfig into CaffeineMessageCache and use generation-based invalidation"
```

---

## Task 7: Search Indexes — pg_trgm GIN

**Files:**
- Create: `platform-persistence-jooq/src/main/resources/db/migration/V10__add_trgm_search_indexes.sql`
- Create: `platform-persistence-jdbi/src/main/resources/db/migration/V10__add_trgm_search_indexes.sql`

- [ ] **Step 1: Create the Flyway migration**

Create the file `platform-persistence-jooq/src/main/resources/db/migration/V10__add_trgm_search_indexes.sql` with:
```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_plt_messages_content_trgm
    ON plt_messages USING GIN (content gin_trgm_ops);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_plt_contacts_name_trgm
    ON plt_contacts USING GIN (name gin_trgm_ops);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_plt_contacts_company_trgm
    ON plt_contacts USING GIN (company gin_trgm_ops);
```

Copy the same file to `platform-persistence-jdbi/src/main/resources/db/migration/V10__add_trgm_search_indexes.sql`.

- [ ] **Step 2: Verify build compiles and migration is picked up**

Run: `mvn -pl platform-persistence-jooq,platform-persistence-jdbi compile`

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```
git add platform-persistence-jooq/src/main/resources/db/migration/V10__add_trgm_search_indexes.sql platform-persistence-jdbi/src/main/resources/db/migration/V10__add_trgm_search_indexes.sql
git commit -m "perf: add pg_trgm GIN indexes for message content and contact name/company search"
```

---

## Final Verification

After all 7 tasks are complete:

- [ ] **Run full reactor verify**

```bash
mvn clean verify -T4 -pl platform-core,platform-security,platform-persistence-jooq,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seed
```

Expected: BUILD SUCCESS, 0 test failures, 0 quality gate violations

- [ ] **Update TODO.md files — mark performance items as done**

In `docs/TODO.md`, mark these as `[x]`:
- Wire runtime cache settings into CaffeineMessageCache
- Replace prefix-scan message cache invalidation
- Resolve session state once per request
- Precompute JTE template class lookup
- Restrict dynamic ETag hashing
- Use bounded executor for Segment analytics
- Avoid loading all users for admin row actions
- Add text-search indexes for %LIKE% search paths

In root `TODO.md`, find and mark the matching performance items.

- [ ] **Final commit**

```
git add docs/TODO.md TODO.md
git commit -m "docs: mark completed performance optimization items"
```
