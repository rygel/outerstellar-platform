# Performance Optimizations Round 2 — Design Spec

**Date:** 2026-05-16
**Status:** Draft
**Scope:** 6 remaining performance items across platform-core, platform-web, platform-sync-client, platform-desktop, platform-persistence-jooq, platform-persistence-jdbi

---

## 1. Debounce Desktop Search Reloads

**Problem:** Typing in the search field fires a DB query on every keystroke (10 queries for "hello" — 5 for messages + 5 for contacts).

**Solution:** Add a 300ms debounce `javax.swing.Timer` in `SyncViewModel.searchQuery` setter. Cancel pending timer on each keystroke; fire `loadMessages()` only after the user pauses. For JavaFX, use `PauseTransition(Duration.millis(300))`.

**Files:**
- `platform-desktop/.../swing/viewmodel/SyncViewModel.kt` (setter at lines 86-90)
- `platform-desktop/.../swing/SwingSyncApp.kt` (DocumentListener at lines 490-504)
- `platform-desktop-javafx/.../fx/controller/MessagesController.kt` (line 77)

---

## 2. Push Search Limits Closer to Providers

**Problem:** Each search provider fetches up to `limit` results independently. With 2 providers and `limit=20`, up to 40 results are fetched and then half are discarded after merging.

**Solution:**
- Pass `ceil(limit / providers.size)` to each provider instead of full `limit`.
- Add `listMessagesForSearch(query, limit)` to `MessageService` that skips the `countMessages()` query (search doesn't need total count). Internally calls `repository.listMessages()` directly.
- Same for contacts: `listContactsForSearch(query, limit)`.

**Files:**
- `platform-core/.../service/MessageService.kt` (add search-optimized method)
- `platform-core/.../service/ContactService.kt` (add search-optimized method)
- `platform-web/.../search/MessageSearchProvider.kt` (use new method)
- `platform-web/.../search/ContactSearchProvider.kt` (use new method)
- `platform-web/.../web/SearchRoutes.kt` (divide limit)
- `platform-web/.../web/SearchPageFactory.kt` (divide limit)

---

## 3. Bound Sync Pull and Dirty Push Batches

**Problem:** `findChangesSince()` and `listDirtyMessages()` have no LIMIT — a device offline for days loads thousands of records in one query.

**Solution:**
- Add `limit: Int = 500` parameter to `findChangesSince()`, `listDirtyMessages()`, `listDirtyContacts()` in repository interfaces and implementations.
- Add `hasMore: Boolean` to `SyncPullResponse`.
- `getChangesSince()` queries with `limit + 1`, returns `hasMore = results.size > limit`, then drops the extra.
- `SyncService` push loop pages through dirty items in batches of 500.
- Default limit: 500 (configurable).

**Files:**
- `platform-core/.../persistence/MessageRepository.kt` (interface)
- `platform-core/.../persistence/ContactRepository.kt` (interface)
- `platform-core/.../service/MessageService.kt`
- `platform-core/.../service/ContactService.kt`
- `platform-core/.../sync/SyncPullResponse.kt` (add hasMore)
- `platform-persistence-jooq/.../JooqMessageRepository.kt`
- `platform-persistence-jooq/.../JooqContactRepository.kt`
- `platform-persistence-jdbi/.../JdbiMessageRepository.kt`
- `platform-persistence-jdbi/.../JdbiContactRepository.kt`
- `platform-sync-client/.../sync/SyncService.kt`

---

## 4. Reduce Paired List/Count Pagination Queries

**Problem:** Every paginated list view runs two SQL queries: `listMessages()` + `countMessages()` with identical WHERE clauses.

**Solution:** Use `COUNT(*) OVER()` window function in the list query. PostgreSQL returns the total count inline with each row (computed once, not per row). Eliminate the separate `countMessages()` / `countContacts()` calls in `MessageService` and `ContactService`.

In jOOQ: `dsl.select(asterisk(), count().over()).from(PLT_MESSAGES)...`
In JDBI: `SELECT *, COUNT(*) OVER() AS total_count FROM plt_messages ...`

The `listMessages()` return type changes to include total count. Service layer extracts it from the first row (or 0 for empty results).

**Files:**
- `platform-core/.../persistence/MessageRepository.kt` (add method or change return type)
- `platform-core/.../persistence/ContactRepository.kt` (same)
- `platform-core/.../service/MessageService.kt`
- `platform-core/.../service/ContactService.kt`
- `platform-persistence-jooq/.../JooqMessageRepository.kt`
- `platform-persistence-jooq/.../JooqContactRepository.kt`
- `platform-persistence-jdbi/.../JdbiMessageRepository.kt`
- `platform-persistence-jdbi/.../JdbiContactRepository.kt`

---

## 5. Batch Sync Push Upserts

**Problem:** Each pushed message requires 3 SQL roundtrips: SELECT (check exists) + INSERT/UPDATE + SELECT (return). For 100 messages, that's 300 queries.

**Solution:** Add `batchUpsertSyncedMessages(List<SyncMessage>): List<StoredMessage>` to repository interfaces.

**Strategy:**
1. Collect all incoming `syncId`s
2. Single SELECT to find which already exist: `WHERE sync_id IN (...)`
3. Partition into inserts vs updates based on existence
4. jOOQ: use `dsl.batchInsert()` / `dsl.batchUpdate()` for each set
5. JDBI: use prepared statement `INSERT ... ON CONFLICT (sync_id) DO UPDATE SET ...` in a batch
6. Single final SELECT to return all stored results

**Files:**
- `platform-core/.../persistence/MessageRepository.kt` (add batch method)
- `platform-core/.../persistence/ContactRepository.kt` (add batch method)
- `platform-core/.../service/MessageService.kt` (use batch in processPushRequest)
- `platform-core/.../service/ContactService.kt` (use batch in processPushRequest)
- `platform-persistence-jooq/.../JooqMessageRepository.kt`
- `platform-persistence-jooq/.../JooqContactRepository.kt`
- `platform-persistence-jdbi/.../JdbiMessageRepository.kt`
- `platform-persistence-jdbi/.../JdbiContactRepository.kt`
- `platform-sync-client/.../sync/SyncService.kt` (use batch on client side too)

---

## 6. Stream or Page CSV Exports

**Problem:** CSV exports load entire datasets into memory (`Int.MAX_VALUE` for audit, `findAll()` for users). Large datasets cause heap pressure.

**Solution:**
- Replace in-memory `StringBuilder` with paged iteration.
- Add `listUsersPaged(limit, offset)` and `getAuditLogPaged(limit, offset)` methods.
- Export handlers loop over pages, appending to a `StringBuilder` per page but releasing each page for GC.
- Cap audit export at 10,000 rows (configurable constant).
- User/audit export uses `findPage()` which already exists.

**Files:**
- `platform-web/.../web/UserAdminRoutes.kt` (export handlers + companion CSV methods)
- `platform-web/.../export/MessageExportProvider.kt`
- `platform-web/.../export/ContactExportProvider.kt`
- `platform-security/.../security/SecurityService.kt` (add paged export method)
- `platform-core/.../export/ExportService.kt` (streaming support)

---

## Implementation Order

1. Debounce desktop search (isolated, desktop module only)
2. Push search limits (service + web, simple)
3. Bound sync batches (interface change, medium)
4. Reduce paired list/count (repository change, medium)
5. Batch sync push upserts (complex, multi-repo)
6. Stream/page CSV exports (medium, web routes)
