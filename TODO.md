# TODO

Architecture and performance improvements identified during code review.

---

## High Priority

- [x] **Targeted cache invalidation in `MessageService`**
  Replace `cache.invalidateAll()` calls with per-key invalidation so a single message
  mutation does not flush the entire cache for all readers.
  — `platform-core/.../service/MessageService.kt` lines 129–130, 150–152, 197, 207–208, 240–242, 276–278

- [x] **Bound the `gravatarCache` with Caffeine**
  Replace the unbounded `ConcurrentHashMap` with a Caffeine cache (e.g. max 10 000 entries,
  1 h TTL) to prevent slow memory growth in long-running instances.
  — `platform-web/.../web/WebPageFactory.kt` line 9

---

## Medium Priority

- [x] **Eliminate extra SELECT in `JooqContactRepository.updateContact()`**
  The `contactId` used as the UPDATE WHERE key is re-fetched with a separate SELECT
  immediately after the update. Pass the ID directly to `insertCollections()` instead.
  — `platform-persistence-jooq/.../persistence/JooqContactRepository.kt` lines 247–252

- [x] **Continuous outbox drain when batch is full**
  If `OutboxProcessor` processes a full batch (exactly 10 items), reschedule immediately
  rather than waiting another 30 s, so backlog does not grow under sustained load.
  — `platform-core/.../service/OutboxProcessor.kt` line 8 · `Main.kt` line 61

---

## Low Priority

- [x] **Document the jOOQ vs. JDBI persistence selection policy**
  Both modules implement the same repository interfaces with no runtime flag or documented
  rule for which is active. Add a decision record or README note stating when each is used
  and which is the default, to avoid diverging SQL under future schema changes.

- [x] **Guard `WebContext.user` against cache removal**
  The per-request user lookup is safe today because `CachingUserRepository` absorbs the DB
  hit, but there is no in-code note to that effect. Add a comment so future changes to the
  cache (removal, TTL reduction) surface the performance risk immediately.
  — `platform-web/.../web/WebContext.kt` lines 69–86
