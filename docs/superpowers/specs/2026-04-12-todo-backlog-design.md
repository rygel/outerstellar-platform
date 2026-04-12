# Design: TODO Backlog Implementation

**Date:** 2026-04-12  
**Branch:** fix/sidebar-selector-reload-delay  
**Source:** TODO.md — architecture and performance improvements

---

## Scope

Six improvements across three modules: `platform-core`, `platform-web`, `platform-persistence-jooq`.
All items are independent and can be implemented in any order.

---

## 1. Targeted cache invalidation in `MessageService` (High)

**File:** `platform-core/.../service/MessageService.kt`

**Problem:** Every mutation calls `cache.put("entity:${syncId}", msg)` followed immediately by
`cache.invalidateAll()`, which nukes the entry just written along with every other cached entry.
Only list-query results (`list:*` keys) need to be invalidated after a mutation; individual
entity entries (`entity:*`) should be kept or explicitly updated.

**Design:**
- Add `invalidateByPrefix(prefix: String)` to the `MessageCache` interface (alongside the
  existing `invalidate` / `invalidateAll`).
- Implement in `CaffeineMessageCache`:
  ```kotlin
  override fun invalidateByPrefix(prefix: String) {
      cache.invalidateAll(cache.asMap().keys.filter { it.startsWith(prefix) })
  }
  ```
- `NoOpMessageCache` gets a no-op override.
- In `MessageService`, replace every `cache.invalidateAll()` with
  `cache.invalidateByPrefix("list:")`.
- `resolveConflict` does not call `cache.put` before `invalidateAll`; add
  `cache.invalidate("entity:$syncId")` before `cache.invalidateByPrefix("list:")` there.

**Call sites to change:** `createServerMessage` (line 129), `createLocalMessage` (line 151),
`processPushRequest` (line 197), `restore` (line 207), `deleteMessage` (line 241),
`updateMessage` (line 277), `resolveConflict` (line 307 — also add entity invalidate).

---

## 2. Bound `gravatarCache` with Caffeine (High)

**File:** `platform-web/.../web/WebPageFactory.kt`

**Problem:** The file-level `private val gravatarCache = ConcurrentHashMap<String, String>()`
is unbounded and never expires. Across the JVM lifetime it grows without limit proportional to
the number of unique email addresses seen.

**Design:**
- Replace with a Caffeine cache bounded to 10 000 entries, expiring 1 hour after last access.
- Caffeine is already a transitive dependency via `platform-core`.
- Change `computeIfAbsent` to Caffeine's `get(key) { ... }` loader form.

```kotlin
private val gravatarCache: com.github.benmanes.caffeine.cache.Cache<String, String> =
    com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterAccess(1, java.util.concurrent.TimeUnit.HOURS)
        .build()

fun gravatarUrl(email: String, customUrl: String?): String {
    if (!customUrl.isNullOrBlank()) return customUrl
    return gravatarCache.get(email.trim().lowercase()) { normalized ->
        // MD5 hash computation unchanged
    }!!
}
```

---

## 3. Eliminate extra SELECT in `JooqContactRepository` (Medium)

**File:** `platform-persistence-jooq/.../persistence/JooqContactRepository.kt`

**Problem:** Three methods perform an UPDATE then a separate SELECT to retrieve the row's
internal `id` for use with `insertCollections`:

| Method | Lines |
|--------|-------|
| `updateContact` | 247–252 |
| `upsertSyncedContact` (UPDATE branch) | 138–146 |
| `resolveConflict` | 282–288 |

**Design:** Use jOOQ's `.returning(PLT_CONTACTS.ID)` on the UPDATE statement to get the id in
a single round-trip. If `.fetchOne()` returns null, the WHERE clause matched zero rows —
treat as the optimistic-lock failure (for `updateContact`) or a no-op (for the other two).

`updateContact` example:
```kotlin
val contactId = txDsl.update(PLT_CONTACTS)
    ...
    .where(PLT_CONTACTS.SYNC_ID.eq(contact.syncId))
    .and(PLT_CONTACTS.VERSION.eq(contact.version))
    .returning(PLT_CONTACTS.ID)
    .fetchOne(PLT_CONTACTS.ID) ?: throw OptimisticLockException("Contact", contact.syncId)
insertCollections(txDsl, contactId, contact.emails, contact.phones, contact.socialMedia)
```

`upsertSyncedContact` and `resolveConflict` follow the same pattern without the
OptimisticLockException — guard with `if (contactId != null)` as before.

Note: `RETURNING` on UPDATE requires PostgreSQL or H2 (both are used here). jOOQ generates the
correct syntax for both dialects automatically.

---

## 4. Continuous outbox drain when batch is full (Medium)

**File:** `platform-core/.../service/OutboxProcessor.kt`

**Problem:** `processPending()` fetches exactly `MAX_BATCH_SIZE` (10) entries and then returns,
regardless of whether more entries are waiting. When load is sustained, the backlog grows by up
to 10 entries every 30 seconds rather than being drained immediately.

**Design:** Wrap the fetch-and-process logic in a `do/while` loop that re-runs immediately when
the batch was full (implying more entries may exist):

```kotlin
fun processPending() {
    var processed: Int
    do {
        val entries = outboxRepository.listPending(MAX_BATCH_SIZE)
        if (entries.isEmpty()) return
        logger.info("Processing {} outbox entries", entries.size)
        processEntries(entries)
        processed = entries.size
    } while (processed == MAX_BATCH_SIZE)
}
```

No changes to `Main.kt` or the scheduler.

---

## 5. Document jOOQ vs JDBI persistence selection policy (Low)

**Files:**
- `platform-persistence-jdbi/.../di/PersistenceModule.kt`
- `platform-persistence-jooq/.../di/PersistenceModule.kt`

**Problem:** Both modules expose a `persistenceModule` val that wires all repository interfaces.
There is no runtime flag, no README, and no code comment explaining which module is active or
when to use one over the other.

**Design:** Add a KDoc comment to each `persistenceModule` val:

JDBI module:
```kotlin
/**
 * Persistence module backed by JDBI. Suitable for environments without jOOQ code generation
 * (e.g. lightweight deployments, embedded databases). Wire into your Koin app in place of
 * the jOOQ module — never include both.
 */
val persistenceModule get() = module { ... }
```

jOOQ module:
```kotlin
/**
 * Persistence module backed by jOOQ with generated type-safe SQL. Preferred for production
 * PostgreSQL deployments where compile-time query safety is valuable. Wire into your Koin app
 * in place of the JDBI module — never include both.
 */
val persistenceModule get() = module { ... }
```

---

## 6. Guard `WebContext.user` against cache removal (Low)

**File:** `platform-web/.../web/WebContext.kt`  
**Lines:** 69–86

**Problem:** The per-request user lookup silently depends on `CachingUserRepository` absorbing
the DB hit. If that cache is removed or its TTL is reduced, every HTTP request hits the database
without any visible warning in the code.

**Design:** Add a single comment at the `user` lazy property:

```kotlin
// Performance note: this lookup is cheap because CachingUserRepository (wired in DI)
// absorbs the DB hit. If that cache is removed or its TTL is significantly reduced,
// every request will incur a database round-trip — profile before changing.
val user: User? by lazy { ... }
```

---

## Testing

| Item | Test strategy |
|------|--------------|
| 1 — cache invalidation | Existing `MessageServiceTest` exercises mutations; verify list keys survive after entity update |
| 2 — gravatar cache | No new tests; Caffeine correctness is library-tested |
| 3 — extra SELECT | Existing `JooqContactRepository` integration tests cover updateContact / upsertSyncedContact |
| 4 — outbox drain | Existing `OutboxProcessorTest`; add a test: seed 11 entries, assert all are processed in one `processPending()` call |
| 5 — doc | No tests |
| 6 — comment | No tests |

---

## Implementation order

1. `MessageCache.invalidateByPrefix` + `CaffeineMessageCache` + `NoOpMessageCache` (interface change first)
2. `MessageService` — replace `invalidateAll()` calls
3. `gravatarCache` — swap in `WebPageFactory.kt`
4. `JooqContactRepository` — eliminate extra SELECTs
5. `OutboxProcessor` — continuous drain loop
6. KDoc on both `persistenceModule` vals
7. Comment on `WebContext.user`
8. Mark all six items complete in `TODO.md`
