# TODO Backlog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement all six items from TODO.md: targeted cache invalidation, bounded gravatar cache, eliminate extra SELECTs in JooqContactRepository, continuous outbox drain, jOOQ vs JDBI documentation, and a WebContext.user cache-dependency comment.

**Architecture:** Items 1–2 change the `MessageCache` interface and its implementations in `platform-core` and `platform-web`. Items 3–4 are self-contained changes inside `platform-persistence-jooq` and `platform-core`. Items 5–6 are doc-only changes with no runtime impact.

**Tech Stack:** Kotlin, JUnit 5, MockK, Caffeine 3.x, jOOQ 3.21, Maven

---

## File Map

| File | Change |
|------|--------|
| `platform-core/src/main/kotlin/…/persistence/MessageCache.kt` | Add `invalidateByPrefix` with default fallback |
| `platform-core/src/main/kotlin/…/persistence/CaffeineMessageCache.kt` | Implement `invalidateByPrefix` |
| `platform-core/src/main/kotlin/…/service/MessageService.kt` | Replace 7× `invalidateAll()` with `invalidateByPrefix("list:")` |
| `platform-core/src/test/kotlin/…/persistence/CaffeineMessageCacheTest.kt` | New — test `invalidateByPrefix` |
| `platform-core/src/test/kotlin/…/service/MessageServiceTest.kt` | Add cache-preservation test |
| `platform-web/pom.xml` | Add Caffeine as direct dependency |
| `platform-web/src/main/kotlin/…/web/WebPageFactory.kt` | Replace `ConcurrentHashMap` gravatar cache with Caffeine |
| `platform-persistence-jooq/src/main/kotlin/…/persistence/JooqContactRepository.kt` | Use `RETURNING` in `updateContact`, `upsertSyncedContact`, `resolveConflict` |
| `platform-core/src/main/kotlin/…/service/OutboxProcessor.kt` | Continuous drain loop in `processPending()` |
| `platform-core/src/test/kotlin/…/service/OutboxProcessorTest.kt` | Add full-batch re-poll test |
| `platform-persistence-jdbi/src/main/kotlin/…/di/PersistenceModule.kt` | KDoc comment |
| `platform-persistence-jooq/src/main/kotlin/…/di/PersistenceModule.kt` | KDoc comment |
| `platform-web/src/main/kotlin/…/web/WebContext.kt` | Cache-dependency comment on `user` |
| `TODO.md` | Mark all six items complete |

---

## Task 1: Add `invalidateByPrefix` to `MessageCache`

**Files:**
- Modify: `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/MessageCache.kt`
- Create: `platform-core/src/test/kotlin/io/github/rygel/outerstellar/platform/persistence/CaffeineMessageCacheTest.kt`
- Modify: `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/CaffeineMessageCache.kt`

- [ ] **Step 1: Write a failing test for `invalidateByPrefix`**

Create `platform-core/src/test/kotlin/io/github/rygel/outerstellar/platform/persistence/CaffeineMessageCacheTest.kt`:

```kotlin
package io.github.rygel.outerstellar.platform.persistence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CaffeineMessageCacheTest {

    private val cache = CaffeineMessageCache()

    @Test
    fun `invalidateByPrefix removes only matching keys`() {
        cache.put("list:all:null:10:0", "result-1")
        cache.put("list:q:null:10:0", "result-2")
        cache.put("entity:abc-123", "message-1")

        cache.invalidateByPrefix("list:")

        assertNull(cache.get("list:all:null:10:0"))
        assertNull(cache.get("list:q:null:10:0"))
        assertEquals("message-1", cache.get("entity:abc-123"))
    }

    @Test
    fun `invalidateByPrefix with no matching keys is a no-op`() {
        cache.put("entity:abc-123", "message-1")

        cache.invalidateByPrefix("list:")

        assertEquals("message-1", cache.get("entity:abc-123"))
    }
}
```

- [ ] **Step 2: Run the test to confirm it fails**

```bash
mvn test -pl platform-core -Dtest=CaffeineMessageCacheTest -T 4
```

Expected: compilation failure — `invalidateByPrefix` does not yet exist.

- [ ] **Step 3: Add `invalidateByPrefix` to the `MessageCache` interface**

In `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/MessageCache.kt`, add the method after `invalidateAll()`. The default implementation calls `invalidateAll()` so that existing implementations remain correct without an override:

```kotlin
interface MessageCache {
    fun get(key: String): Any?

    fun put(key: String, value: Any)

    fun getOrPut(key: String, loader: () -> Any): Any = get(key) ?: loader().also { put(key, it) }

    fun invalidate(key: String)

    fun invalidateAll()

    fun invalidateByPrefix(prefix: String) = invalidateAll()

    fun getStats(): Map<String, Any>
}
```

- [ ] **Step 4: Implement `invalidateByPrefix` efficiently in `CaffeineMessageCache`**

In `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/CaffeineMessageCache.kt`, add after `invalidateAll()`:

```kotlin
override fun invalidateByPrefix(prefix: String) {
    cache.invalidateAll(cache.asMap().keys.filter { it.startsWith(prefix) })
}
```

The full file after this change:

```kotlin
package io.github.rygel.outerstellar.platform.persistence

import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import java.util.concurrent.TimeUnit

private const val DEFAULT_MAX_SIZE = 1000L
private const val DEFAULT_TTL_MINUTES = 10L

class CaffeineMessageCache(meterRegistry: MeterRegistry? = null) : MessageCache {
    private val cache =
        Caffeine.newBuilder()
            .maximumSize(DEFAULT_MAX_SIZE)
            .expireAfterWrite(DEFAULT_TTL_MINUTES, TimeUnit.MINUTES)
            .recordStats()
            .build<String, Any>()

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

    override fun invalidateByPrefix(prefix: String) {
        cache.invalidateAll(cache.asMap().keys.filter { it.startsWith(prefix) })
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

- [ ] **Step 5: Run the test to confirm it passes**

```bash
mvn test -pl platform-core -Dtest=CaffeineMessageCacheTest -T 4
```

Expected: `BUILD SUCCESS`, both tests pass.

- [ ] **Step 6: Commit**

```bash
git add platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/MessageCache.kt \
        platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/CaffeineMessageCache.kt \
        platform-core/src/test/kotlin/io/github/rygel/outerstellar/platform/persistence/CaffeineMessageCacheTest.kt
git commit -m "feat: add invalidateByPrefix to MessageCache — avoids flushing entity keys on list mutations"
```

---

## Task 2: Replace `invalidateAll()` in `MessageService`

**Files:**
- Modify: `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/service/MessageService.kt`
- Modify: `platform-core/src/test/kotlin/io/github/rygel/outerstellar/platform/service/MessageServiceTest.kt`

- [ ] **Step 1: Write a failing test verifying entity keys survive after mutation**

Add to `platform-core/src/test/kotlin/io/github/rygel/outerstellar/platform/service/MessageServiceTest.kt`:

```kotlin
import io.github.rygel.outerstellar.platform.persistence.CaffeineMessageCache
import io.mockk.every
import io.mockk.mockk
import java.util.UUID

// Add inside class MessageServiceTest:

@Test
fun `createServerMessage preserves entity cache entry after mutation`() {
    val syncId = UUID.randomUUID().toString()
    val msg = StoredMessage(syncId, "Alice", "hello", 1000L, false, false, 1L)
    every { repository.createServerMessage("Alice", "hello") } returns msg

    val cache = CaffeineMessageCache()
    val serviceWithCache = MessageService(repository, cache = cache)

    serviceWithCache.createServerMessage("Alice", "hello")

    // Entity key must still be present — not nuked by invalidateAll
    assertEquals(msg, cache.get("entity:$syncId"))
}

@Test
fun `createServerMessage clears list cache entries`() {
    val syncId = UUID.randomUUID().toString()
    val msg = StoredMessage(syncId, "Alice", "hello", 1000L, false, false, 1L)
    every { repository.createServerMessage("Alice", "hello") } returns msg

    val cache = CaffeineMessageCache()
    cache.put("list:null:null:10:0", "stale-result")

    val serviceWithCache = MessageService(repository, cache = cache)
    serviceWithCache.createServerMessage("Alice", "hello")

    assertNull(cache.get("list:null:null:10:0"))
}
```

Also add these imports to the top of the file (alongside the existing ones):

```kotlin
import org.junit.jupiter.api.Assertions.assertNull
import io.github.rygel.outerstellar.platform.persistence.CaffeineMessageCache
```

- [ ] **Step 2: Run the tests to confirm they fail**

```bash
mvn test -pl platform-core -Dtest=MessageServiceTest -T 4
```

Expected: the two new tests fail — `createServerMessage` currently calls `invalidateAll()`, which clears `entity:*` entries.

- [ ] **Step 3: Replace `invalidateAll()` with `invalidateByPrefix("list:")` at all seven call sites**

In `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/service/MessageService.kt`:

**`createServerMessage` (around line 128)** — replace:
```kotlin
cache.put("entity:${message.syncId}", message)
cache.invalidateAll()
```
with:
```kotlin
cache.put("entity:${message.syncId}", message)
cache.invalidateByPrefix("list:")
```

**`createLocalMessage` (around line 150)** — replace:
```kotlin
cache.put("entity:${message.syncId}", message)
cache.invalidateAll()
```
with:
```kotlin
cache.put("entity:${message.syncId}", message)
cache.invalidateByPrefix("list:")
```

**`processPushRequest` (around line 197)** — replace:
```kotlin
if (appliedCount > 0 || conflicts.isNotEmpty()) {
    cache.invalidateAll()
    eventPublisher.publishRefresh("message-list-panel")
}
```
with:
```kotlin
if (appliedCount > 0 || conflicts.isNotEmpty()) {
    cache.invalidateByPrefix("list:")
    eventPublisher.publishRefresh("message-list-panel")
}
```

**`restore` (around line 207)** — replace:
```kotlin
cache.invalidate("entity:$syncId")
cache.invalidateAll()
```
with:
```kotlin
cache.invalidate("entity:$syncId")
cache.invalidateByPrefix("list:")
```

**`deleteMessage` (around line 241)** — replace:
```kotlin
cache.invalidate("entity:$syncId")
cache.invalidateAll()
```
with:
```kotlin
cache.invalidate("entity:$syncId")
cache.invalidateByPrefix("list:")
```

**`updateMessage` (around line 277)** — replace:
```kotlin
cache.put("entity:${updated.syncId}", updated)
cache.invalidateAll()
```
with:
```kotlin
cache.put("entity:${updated.syncId}", updated)
cache.invalidateByPrefix("list:")
```

**`resolveConflict` (around line 307)** — replace:
```kotlin
cache.invalidateAll()
eventPublisher.publishRefresh("message-list-panel")
```
with:
```kotlin
cache.invalidate("entity:$syncId")
cache.invalidateByPrefix("list:")
eventPublisher.publishRefresh("message-list-panel")
```

- [ ] **Step 4: Run all platform-core tests**

```bash
mvn test -pl platform-core -T 4
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/service/MessageService.kt \
        platform-core/src/test/kotlin/io/github/rygel/outerstellar/platform/service/MessageServiceTest.kt
git commit -m "fix: use invalidateByPrefix(\"list:\") in MessageService — entity cache entries survive mutations"
```

---

## Task 3: Replace unbounded `gravatarCache` with Caffeine in `WebPageFactory`

**Files:**
- Modify: `platform-web/pom.xml`
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/WebPageFactory.kt`

- [ ] **Step 1: Add Caffeine as a direct dependency in `platform-web/pom.xml`**

Find the `<dependencies>` block in `platform-web/pom.xml`. Add after the `outerstellar-platform-core` dependency:

```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

The version is managed by the parent pom (`${caffeine.version}` = 3.2.3), so no `<version>` tag is needed.

- [ ] **Step 2: Replace the `gravatarCache` declaration and usage in `WebPageFactory.kt`**

At the top of `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/WebPageFactory.kt`, the current code is:

```kotlin
private val gravatarCache = java.util.concurrent.ConcurrentHashMap<String, String>()

fun gravatarUrl(email: String, customUrl: String?): String {
    if (!customUrl.isNullOrBlank()) return customUrl
    return gravatarCache.computeIfAbsent(email.trim().lowercase()) { normalized ->
        val hash =
            java.security.MessageDigest.getInstance("MD5").digest(normalized.toByteArray()).joinToString("") { // nosemgrep: kotlin.lang.security.use-of-md5.use-of-md5 -- Gravatar API requires MD5
                "%02x".format(it)
            }
        "https://www.gravatar.com/avatar/$hash?d=identicon&s=80"
    }
}
```

Replace with:

```kotlin
private val gravatarCache: com.github.benmanes.caffeine.cache.Cache<String, String> =
    com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterAccess(1, java.util.concurrent.TimeUnit.HOURS)
        .build()

fun gravatarUrl(email: String, customUrl: String?): String {
    if (!customUrl.isNullOrBlank()) return customUrl
    val normalized = email.trim().lowercase()
    return gravatarCache.get(normalized) {
        val hash =
            java.security.MessageDigest.getInstance("MD5").digest(it.toByteArray()).joinToString("") { byte -> // nosemgrep: kotlin.lang.security.use-of-md5.use-of-md5 -- Gravatar API requires MD5
                "%02x".format(byte)
            }
        "https://www.gravatar.com/avatar/$hash?d=identicon&s=80"
    }!!
}
```

The `!!` is safe — the loader never returns null, but Caffeine's Java API exposes `Cache.get` as a platform type in Kotlin.

- [ ] **Step 3: Build and test platform-web**

```bash
mvn test -pl platform-web -T 4
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add platform-web/pom.xml \
        platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/WebPageFactory.kt
git commit -m "fix: replace unbounded gravatarCache ConcurrentHashMap with Caffeine (max 10k, 1h TTL)"
```

---

## Task 4: Eliminate extra SELECTs in `JooqContactRepository`

**Files:**
- Modify: `platform-persistence-jooq/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/JooqContactRepository.kt`

There are three methods with the same pattern: UPDATE followed by a separate SELECT to get the internal `id` for `insertCollections`. All three are fixed by using jOOQ's `.returning(PLT_CONTACTS.ID)` clause on the UPDATE.

- [ ] **Step 1: Run existing tests to establish a passing baseline**

```bash
mvn test -pl platform-persistence-jooq -T 4
```

Expected: `BUILD SUCCESS`. Note this count of passing tests — all must still pass after the change.

- [ ] **Step 2: Fix `updateContact` (lines 227–258)**

Replace the body of the `dsl.transaction` block in `updateContact`:

Old:
```kotlin
override fun updateContact(contact: StoredContact): StoredContact {
    dsl.transaction { config ->
        val txDsl = using(config)
        val rows =
            txDsl
                .update(PLT_CONTACTS)
                .set(PLT_CONTACTS.NAME, contact.name)
                .set(PLT_CONTACTS.COMPANY, contact.company)
                .set(PLT_CONTACTS.COMPANY_ADDRESS, contact.companyAddress)
                .set(PLT_CONTACTS.DEPARTMENT, contact.department)
                .set(PLT_CONTACTS.UPDATED_AT_EPOCH_MS, System.currentTimeMillis())
                .set(PLT_CONTACTS.DIRTY, contact.dirty)
                .set(PLT_CONTACTS.DELETED, contact.deleted)
                .set(PLT_CONTACTS.VERSION, contact.version + 1)
                .where(PLT_CONTACTS.SYNC_ID.eq(contact.syncId))
                .and(PLT_CONTACTS.VERSION.eq(contact.version))
                .execute()

        if (rows == 0) throw OptimisticLockException("Contact", contact.syncId)

        val contactId =
            txDsl
                .select(PLT_CONTACTS.ID)
                .from(PLT_CONTACTS)
                .where(PLT_CONTACTS.SYNC_ID.eq(contact.syncId))
                .fetchOne(PLT_CONTACTS.ID)
        if (contactId != null) {
            insertCollections(txDsl, contactId, contact.emails, contact.phones, contact.socialMedia)
        }
    }

    return requireNotNull(findBySyncId(contact.syncId))
}
```

New:
```kotlin
override fun updateContact(contact: StoredContact): StoredContact {
    dsl.transaction { config ->
        val txDsl = using(config)
        val contactId =
            txDsl
                .update(PLT_CONTACTS)
                .set(PLT_CONTACTS.NAME, contact.name)
                .set(PLT_CONTACTS.COMPANY, contact.company)
                .set(PLT_CONTACTS.COMPANY_ADDRESS, contact.companyAddress)
                .set(PLT_CONTACTS.DEPARTMENT, contact.department)
                .set(PLT_CONTACTS.UPDATED_AT_EPOCH_MS, System.currentTimeMillis())
                .set(PLT_CONTACTS.DIRTY, contact.dirty)
                .set(PLT_CONTACTS.DELETED, contact.deleted)
                .set(PLT_CONTACTS.VERSION, contact.version + 1)
                .where(PLT_CONTACTS.SYNC_ID.eq(contact.syncId))
                .and(PLT_CONTACTS.VERSION.eq(contact.version))
                .returning(PLT_CONTACTS.ID)
                .fetchOne(PLT_CONTACTS.ID) ?: throw OptimisticLockException("Contact", contact.syncId)
        insertCollections(txDsl, contactId, contact.emails, contact.phones, contact.socialMedia)
    }

    return requireNotNull(findBySyncId(contact.syncId))
}
```

- [ ] **Step 3: Fix the UPDATE branch of `upsertSyncedContact` (around lines 124–146)**

The UPDATE branch currently ends with:
```kotlin
txDsl
    .update(PLT_CONTACTS)
    ...
    .where(PLT_CONTACTS.SYNC_ID.eq(contact.syncId))
    .execute()

val contactId =
    txDsl
        .select(PLT_CONTACTS.ID)
        .from(PLT_CONTACTS)
        .where(PLT_CONTACTS.SYNC_ID.eq(contact.syncId))
        .fetchOne(PLT_CONTACTS.ID)
if (contactId != null) {
    insertCollections(txDsl, contactId, contact.emails, contact.phones, contact.socialMedia)
}
```

Replace with:
```kotlin
val contactId =
    txDsl
        .update(PLT_CONTACTS)
        .set(PLT_CONTACTS.NAME, contact.name)
        .set(PLT_CONTACTS.COMPANY, contact.company)
        .set(PLT_CONTACTS.COMPANY_ADDRESS, contact.companyAddress)
        .set(PLT_CONTACTS.DEPARTMENT, contact.department)
        .set(PLT_CONTACTS.UPDATED_AT_EPOCH_MS, contact.updatedAtEpochMs)
        .set(PLT_CONTACTS.DIRTY, dirty)
        .set(PLT_CONTACTS.DELETED, contact.deleted)
        .set(PLT_CONTACTS.VERSION, existing.version + 1)
        .where(PLT_CONTACTS.SYNC_ID.eq(contact.syncId))
        .returning(PLT_CONTACTS.ID)
        .fetchOne(PLT_CONTACTS.ID)
if (contactId != null) {
    insertCollections(txDsl, contactId, contact.emails, contact.phones, contact.socialMedia)
}
```

- [ ] **Step 4: Fix `resolveConflict` (around lines 266–296)**

The SELECT block:
```kotlin
val contactId =
    txDsl
        .select(PLT_CONTACTS.ID)
        .from(PLT_CONTACTS)
        .where(PLT_CONTACTS.SYNC_ID.eq(syncId))
        .fetchOne(PLT_CONTACTS.ID)
if (contactId != null) {
    insertCollections(
        txDsl,
        contactId,
        resolvedContact.emails,
        resolvedContact.phones,
        resolvedContact.socialMedia,
    )
}
```

Replace by adding `.returning(PLT_CONTACTS.ID)` to the UPDATE above it and removing the separate SELECT. The full `resolveConflict` transaction body becomes:

```kotlin
dsl.transaction { config ->
    val txDsl = using(config)
    val contactId =
        txDsl
            .update(PLT_CONTACTS)
            .set(PLT_CONTACTS.NAME, resolvedContact.name)
            .set(PLT_CONTACTS.COMPANY, resolvedContact.company)
            .set(PLT_CONTACTS.COMPANY_ADDRESS, resolvedContact.companyAddress)
            .set(PLT_CONTACTS.DEPARTMENT, resolvedContact.department)
            .set(PLT_CONTACTS.UPDATED_AT_EPOCH_MS, resolvedContact.updatedAtEpochMs)
            .set(PLT_CONTACTS.DIRTY, resolvedContact.dirty)
            .set(PLT_CONTACTS.VERSION, PLT_CONTACTS.VERSION.plus(1))
            .setNull(PLT_CONTACTS.SYNC_CONFLICT)
            .where(PLT_CONTACTS.SYNC_ID.eq(syncId))
            .returning(PLT_CONTACTS.ID)
            .fetchOne(PLT_CONTACTS.ID)
    if (contactId != null) {
        insertCollections(
            txDsl,
            contactId,
            resolvedContact.emails,
            resolvedContact.phones,
            resolvedContact.socialMedia,
        )
    }
}
```

- [ ] **Step 5: Run all tests in platform-persistence-jooq**

```bash
mvn test -pl platform-persistence-jooq -T 4
```

Expected: `BUILD SUCCESS`, same number of tests as the baseline in Step 1.

- [ ] **Step 6: Commit**

```bash
git add platform-persistence-jooq/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/JooqContactRepository.kt
git commit -m "perf: use RETURNING clause in JooqContactRepository to eliminate post-UPDATE SELECT"
```

---

## Task 5: Continuous outbox drain when batch is full

**Files:**
- Modify: `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/service/OutboxProcessor.kt`
- Modify: `platform-core/src/test/kotlin/io/github/rygel/outerstellar/platform/service/OutboxProcessorTest.kt`

- [ ] **Step 1: Write a failing test for the continuous-drain behaviour**

Add to `OutboxProcessorTest`:

```kotlin
@Test
fun `processPending re-polls immediately when batch is full`() {
    val fullBatch = (1..10).map { entry() }
    every { outboxRepository.listPending(10) } returnsMany listOf(fullBatch, emptyList())

    val processor = OutboxProcessor(outboxRepository)
    processor.processPending()

    verify(exactly = 2) { outboxRepository.listPending(10) }
    fullBatch.forEach { e -> verify { outboxRepository.markProcessed(e.id) } }
}
```

- [ ] **Step 2: Run the test to confirm it fails**

```bash
mvn test -pl platform-core -Dtest=OutboxProcessorTest -T 4
```

Expected: one new test FAIL — currently `listPending` is called exactly once regardless of batch size. Other existing tests still pass.

- [ ] **Step 3: Implement the continuous-drain loop in `OutboxProcessor`**

Replace `processPending()` in `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/service/OutboxProcessor.kt`:

```kotlin
fun processPending() {
    var batchSize: Int
    do {
        val entries = outboxRepository.listPending(MAX_BATCH_SIZE)
        if (entries.isEmpty()) return
        batchSize = entries.size
        logger.info("Processing {} outbox entries", batchSize)
        processEntries(entries)
    } while (batchSize == MAX_BATCH_SIZE)
}
```

- [ ] **Step 4: Run all platform-core tests**

```bash
mvn test -pl platform-core -T 4
```

Expected: `BUILD SUCCESS`. All existing tests still pass (a 2-entry batch is < 10, so the loop exits after one iteration).

- [ ] **Step 5: Commit**

```bash
git add platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/service/OutboxProcessor.kt \
        platform-core/src/test/kotlin/io/github/rygel/outerstellar/platform/service/OutboxProcessorTest.kt
git commit -m "fix: drain full outbox batch immediately instead of waiting for next 30s tick"
```

---

## Task 6: Documentation — jOOQ/JDBI policy, `WebContext.user` comment, `TODO.md`

**Files:**
- Modify: `platform-persistence-jdbi/src/main/kotlin/io/github/rygel/outerstellar/platform/di/PersistenceModule.kt`
- Modify: `platform-persistence-jooq/src/main/kotlin/io/github/rygel/outerstellar/platform/di/PersistenceModule.kt`
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/WebContext.kt`
- Modify: `TODO.md`

- [ ] **Step 1: Add KDoc to the JDBI `persistenceModule`**

In `platform-persistence-jdbi/src/main/kotlin/io/github/rygel/outerstellar/platform/di/PersistenceModule.kt`, add a KDoc comment immediately before `val persistenceModule`:

```kotlin
/**
 * Persistence module backed by JDBI. Suitable for environments that do not use jOOQ code generation
 * (e.g., lightweight deployments, embedded databases). Wire this into your Koin app in place of the
 * jOOQ module — never include both `platform-persistence-jdbi` and `platform-persistence-jooq` at runtime.
 */
val persistenceModule
    get() = module {
```

- [ ] **Step 2: Add KDoc to the jOOQ `persistenceModule`**

In `platform-persistence-jooq/src/main/kotlin/io/github/rygel/outerstellar/platform/di/PersistenceModule.kt`, add immediately before `val persistenceModule`:

```kotlin
/**
 * Persistence module backed by jOOQ with generated type-safe SQL. Preferred for production
 * PostgreSQL deployments where compile-time query safety is valuable. Wire this into your Koin
 * app in place of the JDBI module — never include both `platform-persistence-jooq` and
 * `platform-persistence-jdbi` at runtime.
 */
val persistenceModule
    get() = module {
```

- [ ] **Step 3: Add cache-dependency comment to `WebContext.user`**

In `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/WebContext.kt`, locate the `user` lazy property (around line 69). Add a comment immediately before the `val user` line:

```kotlin
// Performance note: this lookup is cheap because CachingUserRepository (wired in DI)
// absorbs the DB hit. If that cache is removed or its TTL is significantly reduced,
// every request will incur a database round-trip — profile before changing.
val user: User? by lazy {
```

- [ ] **Step 4: Mark all six TODO items complete**

In `TODO.md`, replace all `- [ ]` with `- [x]` for the six items. The file should end up as:

```markdown
## High Priority

- [x] **Targeted cache invalidation in `MessageService`**
  ...

- [x] **Bound the `gravatarCache` with Caffeine**
  ...

## Medium Priority

- [x] **Eliminate extra SELECT in `JooqContactRepository.updateContact()`**
  ...

- [x] **Continuous outbox drain when batch is full**
  ...

## Low Priority

- [x] **Document the jOOQ vs. JDBI persistence selection policy**
  ...

- [x] **Guard `WebContext.user` against cache removal**
  ...
```

- [ ] **Step 5: Build the affected modules to confirm no compilation errors**

```bash
mvn test -pl platform-persistence-jdbi,platform-persistence-jooq,platform-web -T 4
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add platform-persistence-jdbi/src/main/kotlin/io/github/rygel/outerstellar/platform/di/PersistenceModule.kt \
        platform-persistence-jooq/src/main/kotlin/io/github/rygel/outerstellar/platform/di/PersistenceModule.kt \
        platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/WebContext.kt \
        TODO.md
git commit -m "docs: document jOOQ/JDBI selection policy, add WebContext cache note, close TODO items"
```

---

## Final Verification

- [ ] **Run the full build across all modules**

```bash
mvn verify -T 4
```

Expected: `BUILD SUCCESS`. This is required before pushing per the global CLAUDE.md policy.
